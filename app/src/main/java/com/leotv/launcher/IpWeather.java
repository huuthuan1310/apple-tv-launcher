package com.leotv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Auto-detects user city & coordinates via Internet IP (no GPS/location permission required).
 * Queries Open-Meteo for live temperature & weather status.
 * Throttles queries and caches aggressively to avoid network overhead.
 */
final class IpWeather {
    interface Listener { void onWeather(String label); }

    private static final String TAG = "LeoWeather";
    private static final double DEFAULT_LAT = Double.NaN;
    private static final double DEFAULT_LON = Double.NaN;
    private static final String DEFAULT_CITY = "";

    private final SharedPreferences cache;
    private final Context context;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private long nextAttempt;
    private boolean loading;
    private boolean closed;

    IpWeather(Context context) {
        this.context = context.getApplicationContext();
        cache = context.getSharedPreferences("hcm_weather", Context.MODE_PRIVATE);
    }

    private javax.net.ssl.SSLSocketFactory weatherTls() throws Exception {
        // System trust manager (covers DigiCert, Google Trust Services, Cloudflare, etc.)
        javax.net.ssl.TrustManagerFactory sysTmf = javax.net.ssl.TrustManagerFactory.getInstance(
                javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        sysTmf.init((java.security.KeyStore) null);
        final javax.net.ssl.X509TrustManager sysTm = (javax.net.ssl.X509TrustManager) sysTmf.getTrustManagers()[0];

        // ISRG Root X1 trust manager (for Let's Encrypt / Open-Meteo on Android <= 23)
        java.security.KeyStore customRoots = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
        customRoots.load(null, null);
        try (InputStream pem = context.getResources().openRawResource(R.raw.isrg_root_x1)) {
            customRoots.setCertificateEntry("isrg-root-x1", java.security.cert.CertificateFactory
                    .getInstance("X.509").generateCertificate(pem));
        }
        javax.net.ssl.TrustManagerFactory customTmf = javax.net.ssl.TrustManagerFactory.getInstance(
                javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        customTmf.init(customRoots);
        final javax.net.ssl.X509TrustManager customTm = (javax.net.ssl.X509TrustManager) customTmf.getTrustManagers()[0];

        // Composite trust manager that accepts certs trusted by either system CAs or ISRG Root X1
        javax.net.ssl.X509TrustManager compositeTm = new javax.net.ssl.X509TrustManager() {
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
                    throws java.security.cert.CertificateException {
                try {
                    customTm.checkClientTrusted(chain, authType);
                } catch (java.security.cert.CertificateException e) {
                    sysTm.checkClientTrusted(chain, authType);
                }
            }

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
                    throws java.security.cert.CertificateException {
                try {
                    customTm.checkServerTrusted(chain, authType);
                } catch (java.security.cert.CertificateException e) {
                    sysTm.checkServerTrusted(chain, authType);
                }
            }

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return sysTm.getAcceptedIssuers();
            }
        };

        javax.net.ssl.SSLContext tls = javax.net.ssl.SSLContext.getInstance("TLS");
        tls.init(null, new javax.net.ssl.TrustManager[]{compositeTm}, null);
        return tls.getSocketFactory();
    }

    private JSONObject fetchJson(String urlString) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            if (conn instanceof javax.net.ssl.HttpsURLConnection && android.os.Build.VERSION.SDK_INT <= 23) {
                ((javax.net.ssl.HttpsURLConnection) conn).setSSLSocketFactory(weatherTls());
            }
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; LeoTV)");
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code != 200) throw new java.io.IOException("HTTP " + code + " from " + url.getHost());
            try (InputStream input = conn.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048];
                int size;
                while ((size = input.read(buffer)) != -1) {
                    if (output.size() + size > 65536) throw new java.io.IOException("Response too large");
                    output.write(buffer, 0, size);
                }
                return new JSONObject(output.toString("UTF-8"));
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static class GeoLocation {
        final double lat;
        final double lon;
        final String city;
        GeoLocation(double lat, double lon, String city) {
            this.lat = lat;
            this.lon = lon;
            this.city = city;
        }
    }

    private GeoLocation detectLocationByIp() {
        // 1. Primary: ipwho.is (HTTPS, free, accurate city & timezone)
        try {
            JSONObject json = fetchJson("https://ipwho.is/");
            if (json.optBoolean("success", false)) {
                double lat = json.optDouble("latitude", Double.NaN);
                double lon = json.optDouble("longitude", Double.NaN);
                String city = json.optString("city", "");
                if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                    Log.i(TAG, "Location detected via IP");
                    return new GeoLocation(lat, lon, city);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "ipwho.is fallback: " + e.getMessage());
        }

        // 2. Secondary: get.geojs.io (HTTPS, free)
        try {
            JSONObject json = fetchJson("https://get.geojs.io/v1/ip/geo.json");
            String latStr = json.optString("latitude", "");
            String lonStr = json.optString("longitude", "");
            String city = json.optString("city", "");
            if (!latStr.isEmpty() && !lonStr.isEmpty()) {
                double lat = Double.parseDouble(latStr);
                double lon = Double.parseDouble(lonStr);
                Log.i(TAG, "Location detected via fallback IP service");
                return new GeoLocation(lat, lon, city);
            }
        } catch (Exception e) {
            Log.w(TAG, "geojs.io fallback: " + e.getMessage());
        }

        return null;
    }

    private static String formatCityName(String rawCity) {
        if (rawCity == null || rawCity.trim().isEmpty()) return DEFAULT_CITY;
        String trimmed = rawCity.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (lower.contains("ho chi minh") || lower.contains("saigon")) return "TP.HCM";
        if (lower.equals("hanoi") || lower.equals("ha noi")) return "Hà Nội";
        if (lower.equals("da nang") || lower.equals("danang")) return "Đà Nẵng";
        if (lower.equals("hai phong") || lower.equals("haiphong")) return "Hải Phòng";
        if (lower.equals("can tho") || lower.equals("cantho")) return "Cần Thơ";
        if (lower.equals("nha trang") || lower.equals("nhatrang")) return "Nha Trang";
        if (lower.equals("da lat") || lower.equals("dalat")) return "Đà Lạt";
        if (lower.equals("hue") || lower.equals("thua thien hue")) return "Huế";
        if (lower.equals("vung tau") || lower.equals("vungtau")) return "Vũng Tàu";
        if (lower.equals("quy nhon") || lower.equals("quynhon")) return "Quy Nhơn";
        if (lower.equals("phan thiet") || lower.equals("phanthiet")) return "Phan Thiết";
        if (lower.equals("bien hoa") || lower.equals("bienhoa")) return "Biên Hòa";
        if (lower.equals("buon ma thuot") || lower.equals("buonmathuot")) return "Buôn Ma Thuột";
        if (lower.equals("pleiku")) return "Pleiku";
        if (lower.equals("rach gia") || lower.equals("rachgia")) return "Rạch Giá";
        if (lower.equals("long xuyen") || lower.equals("longxuyen")) return "Long Xuyên";
        if (lower.equals("my tho") || lower.equals("mytho")) return "Mỹ Tho";
        if (lower.equals("thai nguyen") || lower.equals("thainguyen")) return "Thái Nguyên";
        if (lower.equals("bac ninh") || lower.equals("bacninh")) return "Bắc Ninh";
        if (lower.equals("ha long") || lower.equals("halong")) return "Hạ Long";
        if (lower.equals("nam dinh") || lower.equals("namdinh")) return "Nam Định";
        if (lower.equals("vinh")) return "Vinh";
        if (lower.equals("thanh hoa") || lower.equals("thanhhoa")) return "Thanh Hóa";
        if (lower.equals("thai binh") || lower.equals("thaibinh")) return "Thái Bình";
        if (lower.equals("hai duong") || lower.equals("haiduong")) return "Hải Dương";
        if (lower.equals("binh duong") || lower.equals("binhduong")) return "Bình Dương";
        if (lower.equals("dong nai") || lower.equals("dongnai")) return "Đồng Nai";

        // Strip " City" suffix for cleaner display on TV top bar (e.g. "Singapore City" -> "Singapore")
        if (trimmed.endsWith(" City")) {
            return trimmed.substring(0, trimmed.length() - 5);
        }
        return trimmed;
    }

    String cached() {
        String saved = cache.getString("label", null);
        if (saved != null && !saved.isEmpty()) {
            return saved.replaceFirst("^[^\\p{L}\\p{N}−-]+", "");
        }
        return context.getString(R.string.leo_weather_unavailable);
    }

    int weatherCode() { return cache.getInt("weather_code", -1); }
    boolean isDay() { return cache.getBoolean("is_day", true); }
    void networkChanged() {
        cache.edit().putLong("loc_time",0).putLong("time",0).apply();
        nextAttempt=0;
    }

    void refresh(Listener listener) {
        listener.onWeather(cached());
        long now = System.currentTimeMillis();
        boolean needLoc = !cache.contains("loc_lat") || !cache.contains("weather_code");
        if (closed || loading || now < nextAttempt || (!needLoc && now - cache.getLong("time", 0) < 30 * 60_000L)) return;
        loading = true;
        nextAttempt = now + 5 * 60_000L;
        worker.execute(() -> {
            String result = null;
            try {
                // 1. Check or refresh location via IP (refresh location every 12h or if never detected)
                long locAge = System.currentTimeMillis() - cache.getLong("loc_time", 0);
                double lat = Double.longBitsToDouble(cache.getLong("loc_lat", Double.doubleToLongBits(DEFAULT_LAT)));
                double lon = Double.longBitsToDouble(cache.getLong("loc_lon", Double.doubleToLongBits(DEFAULT_LON)));
                String city = cache.getString("loc_city", DEFAULT_CITY);

                if (locAge > 12 * 3600_000L || !cache.contains("loc_lat")) {
                    GeoLocation detected = detectLocationByIp();
                    if (detected != null) {
                        lat = detected.lat;
                        lon = detected.lon;
                        city = detected.city;
                        cache.edit()
                                .putLong("loc_lat", Double.doubleToLongBits(lat))
                                .putLong("loc_lon", Double.doubleToLongBits(lon))
                                .putString("loc_city", city)
                                .putLong("loc_time", System.currentTimeMillis())
                                .apply();
                    }
                }

                if (!cache.contains("loc_lat")) throw new java.io.IOException("IP location unavailable");

                // 2. Fetch weather for current location
                String weatherUrl = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day&timezone=auto&forecast_days=1",
                        lat, lon);
                JSONObject root = fetchJson(weatherUrl);
                JSONObject current = root.getJSONObject("current");
                if (current.isNull("temperature_2m")) throw new java.io.IOException("Missing temperature");
                int code = current.getInt("weather_code");
                result = Math.round(current.getDouble("temperature_2m")) + "° · " + formatCityName(city);
                cache.edit()
                        .putString("label", result)
                        .putInt("weather_code", code)
                        .putBoolean("is_day", current.optInt("is_day", 1) == 1)
                        .putLong("time", System.currentTimeMillis())
                        .apply();
                Log.i(TAG, "Weather updated: " + result);
            } catch (Exception error) {
                Log.w(TAG, error.getClass().getSimpleName() + ": " + error.getMessage());
            }

            final String label = result;
            main.post(() -> {
                loading = false;
                if (!closed) listener.onWeather(label != null ? label : cached());
            });
        });
    }

    void close() {
        closed = true;
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }
}
