package com.leotv.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads dynamic wallpaper from a public Google Photos shared album URL.
 * Parses the album page, extracts high-res photo URLs, downloads and caches
 * the wallpaper locally for instant offline display at next boot.
 */
public final class GooglePhotosWallpaperLoader {

    private static final String TAG = "GooglePhotosWallpaper";
    private static final String CACHED_FILE_NAME = "gphotos_wallpaper.jpg";
    private static final String SLIDESHOW_DIR_NAME = "slideshow";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final Pattern PHOTO_PW_PATTERN = Pattern.compile("https://lh3\\.googleusercontent\\.com/pw/([a-zA-Z0-9_\\-]+)");
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static Handler sMainHandler;

    private static void postToMain(Runnable runnable) {
        try {
            if (sMainHandler == null && Looper.getMainLooper() != null) {
                sMainHandler = new Handler(Looper.getMainLooper());
            }
            if (sMainHandler != null) {
                sMainHandler.post(runnable);
                return;
            }
        } catch (Throwable ignored) {
        }
        runnable.run();
    }

    public interface Callback {
        void onWallpaperLoaded(File file);
    }

    public interface SlideshowCallback {
        void onSlideshowReady(List<File> files);
    }

    public interface ValidateCallback {
        void onSuccess(String normalizedUrl, int photoCount);
        void onError(String errorMessage);
    }

    public static boolean isValidGooglePhotosDomain(String urlString) {
        if (urlString == null) return false;
        String trimmed = urlString.trim();
        if (trimmed.isEmpty()) return false;
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }
        try {
            URL u = new URL(trimmed);
            String host = u.getHost();
            if (host == null) return false;
            host = host.toLowerCase();
            return host.equals("photos.app.goo.gl")
                    || host.equals("photos.google.com")
                    || host.endsWith(".photos.google.com");
        } catch (Exception e) {
            return false;
        }
    }

    public static String normalizeUrl(String urlString) {
        if (urlString == null) return "";
        String trimmed = urlString.trim();
        if (!trimmed.isEmpty() && !trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }
        return trimmed;
    }

    /**
     * Validates that the album belongs to Google Photos, is public, and contains photos.
     * Only if photos are successfully downloaded will the new URL be saved into SharedPreferences.
     */
    public static void validateAndSyncAlbum(Context context, String inputUrl, ValidateCallback callback) {
        if (inputUrl == null || inputUrl.trim().isEmpty()) {
            if (callback != null) callback.onError(context.getString(R.string.leo_album_enter_link));
            return;
        }

        final String normalizedUrl = normalizeUrl(inputUrl);
        if (!isValidGooglePhotosDomain(normalizedUrl)) {
            if (callback != null) {
                callback.onError(context.getString(R.string.leo_album_invalid_domain));
            }
            return;
        }

        final Context appContext = context.getApplicationContext();

        EXECUTOR.execute(() -> {
            try {
                Log.i(TAG, "Starting validation for Google Photos album: " + normalizedUrl);
                ValidationFetchResult fetchResult = fetchHtmlSafely(context, normalizedUrl, 5);

                if (fetchResult.error != null) {
                    postToMain(() -> {
                        if (callback != null) callback.onError(fetchResult.error);
                    });
                    return;
                }

                if (fetchResult.finalUrl != null && fetchResult.finalUrl.contains("accounts.google.com")) {
                    postToMain(() -> {
                        if (callback != null) {
                            callback.onError(context.getString(R.string.leo_album_not_public));
                        }
                    });
                    return;
                }

                if (fetchResult.html == null || fetchResult.html.trim().isEmpty()) {
                    postToMain(() -> {
                        if (callback != null) callback.onError(context.getString(R.string.leo_album_read_failed));
                    });
                    return;
                }

                LinkedHashSet<String> urls = new LinkedHashSet<>();
                Matcher m = PHOTO_PW_PATTERN.matcher(fetchResult.html);
                while (m.find()) {
                    urls.add(m.group(0));
                }

                if (urls.isEmpty()) {
                    postToMain(() -> {
                        if (callback != null) {
                            callback.onError(context.getString(R.string.leo_album_no_photos));
                        }
                    });
                    return;
                }

                List<String> photos = new ArrayList<>(urls);
                Log.i(TAG, "Album validated! Found " + photos.size() + " photo URLs. Downloading to cache...");

                File dir = new File(appContext.getFilesDir(), SLIDESHOW_DIR_NAME);
                if (!dir.exists()) dir.mkdirs();

                List<File> downloaded = new ArrayList<>();
                for (int i = 0; i < photos.size(); i++) {
                    String base = photos.get(i);
                    String downloadUrl = base + "=w1920-h1080-no";
                    File target = new File(dir, "slide_" + i + ".jpg");
                    File temp = new File(dir, "temp_" + i + ".jpg");
                    try {
                        downloadFile(downloadUrl, temp);
                        if (temp.length() > 1024) {
                            if (temp.renameTo(target) || copyFile(temp, target)) {
                                temp.delete();
                                downloaded.add(target);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error downloading slide " + i, e);
                    }
                }

                File[] existing = dir.listFiles((d, name) -> name.startsWith("slide_") && name.endsWith(".jpg"));
                if (existing != null) {
                    for (File f : existing) {
                        if (!downloaded.contains(f)) f.delete();
                    }
                }

                if (downloaded.isEmpty()) {
                    postToMain(() -> {
                        if (callback != null) callback.onError(context.getString(R.string.leo_album_download_failed));
                    });
                    return;
                }

                // Cache first photo as main single wallpaper
                File targetFile = new File(appContext.getFilesDir(), CACHED_FILE_NAME);
                copyFile(downloaded.get(0), targetFile);

                // Validation & download successful! ONLY NOW save to SharedPreferences
                appContext.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                        .edit().putString("google_photos_album", normalizedUrl)
                        .putLong("wallpaper_revision", System.currentTimeMillis()).apply();

                final int count = downloaded.size();
                postToMain(() -> {
                    if (callback != null) callback.onSuccess(normalizedUrl, count);
                });

            } catch (Exception e) {
                Log.e(TAG, "Validation failed", e);
                postToMain(() -> {
                    if (callback != null) {
                        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        callback.onError(context.getString(R.string.leo_album_check_failed) + msg);
                    }
                });
            }
        });
    }

    private static class ValidationFetchResult {
        final String html;
        final String finalUrl;
        final String error;

        ValidationFetchResult(String html, String finalUrl, String error) {
            this.html = html;
            this.finalUrl = finalUrl;
            this.error = error;
        }
    }

    private static ValidationFetchResult fetchHtmlSafely(Context context, String urlString, int maxRedirects) {
        String current = urlString;
        for (int i = 0; i < maxRedirects; i++) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(current).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", USER_AGENT);

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM
                        || code == HttpURLConnection.HTTP_MOVED_TEMP
                        || code == HttpURLConnection.HTTP_SEE_OTHER
                        || code == 307) {
                    String loc = conn.getHeaderField("Location");
                    if (loc == null) {
                        return new ValidationFetchResult(null, current, context.getString(R.string.leo_album_redirect_missing));
                    }
                    if (!loc.startsWith("http")) loc = new URL(new URL(current), loc).toString();
                    current = loc;
                    if (current.contains("accounts.google.com")) {
                        return new ValidationFetchResult(null, current, context.getString(R.string.leo_album_private));
                    }
                    continue;
                }

                if (code == HttpURLConnection.HTTP_OK) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                    }
                    return new ValidationFetchResult(sb.toString(), current, null);
                } else if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                    return new ValidationFetchResult(null, current, context.getString(R.string.leo_album_not_found));
                } else if (code == HttpURLConnection.HTTP_FORBIDDEN) {
                    return new ValidationFetchResult(null, current, context.getString(R.string.leo_album_forbidden));
                } else {
                    return new ValidationFetchResult(null, current, context.getString(R.string.leo_album_http_error) + code);
                }
            } catch (java.net.UnknownHostException e) {
                return new ValidationFetchResult(null, current, context.getString(R.string.leo_album_connection_failed));
            } catch (java.net.SocketTimeoutException e) {
                return new ValidationFetchResult(null, current, context.getString(R.string.leo_album_timeout));
            } catch (Exception e) {
                return new ValidationFetchResult(null, current, context.getString(R.string.leo_connection_error) + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return new ValidationFetchResult(null, current, context.getString(R.string.leo_album_redirect_limit));
    }

    private GooglePhotosWallpaperLoader() {
    }

    /**
     * Returns the cached wallpaper file if it exists and is non-empty.
     */
    public static File getCachedWallpaper(Context context) {
        File f = new File(context.getFilesDir(), CACHED_FILE_NAME);
        return (f.isFile() && f.length() > 1024) ? f : null;
    }

    /**
     * Returns all cached slideshow photos if they exist.
     */
    public static List<File> getSlideshowFiles(Context context) {
        List<File> list = new ArrayList<>();
        File dir = new File(context.getFilesDir(), SLIDESHOW_DIR_NAME);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.startsWith("slide_") && name.endsWith(".jpg"));
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.length() > 1024) list.add(f);
                }
            }
        }
        Collections.sort(list, (a, b) -> a.getName().compareTo(b.getName()));
        if (list.isEmpty()) {
            File single = getCachedWallpaper(context);
            if (single != null) list.add(single);
        }
        return list;
    }

    /**
     * Asynchronously syncs the wallpaper from the given Google Photos album URL.
     */
    public static void sync(Context context, String albumUrl, Callback callback) {
        syncSlideshow(context, albumUrl, files -> {
            if (callback != null && files != null && !files.isEmpty()) {
                callback.onWallpaperLoaded(files.get(0));
            }
        });
    }

    /**
     * Asynchronously syncs all photos from the Google Photos album for slideshow playback.
     */
    public static void syncSlideshow(Context context, String albumUrl, SlideshowCallback callback) {
        if (albumUrl == null || albumUrl.trim().isEmpty()) return;
        final String targetUrl = albumUrl.trim();
        final Context appContext = context.getApplicationContext();

        EXECUTOR.execute(() -> {
            try {
                Log.i(TAG, "Starting syncSlideshow for: " + targetUrl);
                List<String> photos = fetchPhotoUrls(targetUrl);
                if (photos.isEmpty()) {
                    Log.w(TAG, "No photos found in Google Photos album: " + targetUrl);
                    return;
                }
                Log.i(TAG, "Found " + photos.size() + " photos in album for slideshow");

                File dir = new File(appContext.getFilesDir(), SLIDESHOW_DIR_NAME);
                if (!dir.exists()) dir.mkdirs();

                List<File> downloaded = new ArrayList<>();
                for (int i = 0; i < photos.size(); i++) {
                    String base = photos.get(i);
                    String downloadUrl = base + "=w1920-h1080-no";
                    File target = new File(dir, "slide_" + i + ".jpg");
                    File temp = new File(dir, "temp_" + i + ".jpg");
                    try {
                        Log.i(TAG, "Starting download for slide " + i + ": " + downloadUrl);
                        downloadFile(downloadUrl, temp);
                        Log.i(TAG, "Finished download for slide " + i + ", temp size: " + temp.length());
                        if (temp.length() > 1024) {
                            if (temp.renameTo(target) || copyFile(temp, target)) {
                                temp.delete();
                                downloaded.add(target);
                                Log.i(TAG, "Successfully cached slide " + i + " to " + target.getAbsolutePath());
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error downloading slide " + i, e);
                    }
                }

                // Clean up any extra old slides if album was shrunk
                File[] existing = dir.listFiles((d, name) -> name.startsWith("slide_") && name.endsWith(".jpg"));
                if (existing != null) {
                    for (File f : existing) {
                        if (!downloaded.contains(f)) f.delete();
                    }
                }

                if (!downloaded.isEmpty()) {
                    // Update legacy single-wallpaper cache with first photo
                    File targetFile = new File(appContext.getFilesDir(), CACHED_FILE_NAME);
                    copyFile(downloaded.get(0), targetFile);
                }

                postToMain(() -> {
                    if (callback != null) callback.onSlideshowReady(downloaded);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed syncSlideshow", e);
            }
        });
    }

    private static List<String> fetchPhotoUrls(String albumUrl) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        try {
            String html = fetchHtmlWithRedirects(albumUrl, 5);
            if (html == null) {
                Log.w(TAG, "fetchHtmlWithRedirects returned null for " + albumUrl);
                return Collections.emptyList();
            }

            Matcher m = PHOTO_PW_PATTERN.matcher(html);
            while (m.find()) {
                urls.add(m.group(0));
            }
            Log.i(TAG, "Regex matched " + urls.size() + " unique photo URLs");
        } catch (Exception e) {
            Log.e(TAG, "Error fetching album HTML", e);
        }
        return new ArrayList<>(urls);
    }

    private static String fetchHtmlWithRedirects(String urlString, int maxRedirects) throws Exception {
        String current = urlString;
        for (int i = 0; i < maxRedirects; i++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", USER_AGENT);

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER
                    || code == 307) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null) break;
                if (!loc.startsWith("http")) loc = new URL(new URL(current), loc).toString();
                current = loc;
                continue;
            }

            if (code == HttpURLConnection.HTTP_OK) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                } finally {
                    conn.disconnect();
                }
                return sb.toString();
            }
            conn.disconnect();
            break;
        }
        return null;
    }

    private static void downloadFile(String urlString, File dest) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", USER_AGENT);

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static boolean copyFile(File src, File dst) {
        try (InputStream in = new java.io.FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
