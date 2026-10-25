package com.leotv.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads, caches and provides high-definition (1080p Full HD) curated cinematic
 * backdrops for installed apps when focused on the Apple TV dock or grid.
 */
public final class AppBackdropManager {

    private static final String TAG = "AppBackdropManager";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, String> CURATED_URLS = new HashMap<>();
    private static final Map<String, Bitmap> BITMAP_CACHE = new ConcurrentHashMap<>();

    static {
        // SmartTube & YouTube (4K Cinema Movie Theater / Film Auditorium)
        CURATED_URLS.put("org.smarttube.stable", "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?q=80&w=1920&auto=format&fit=crop");
        CURATED_URLS.put("org.smarttube.beta", "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?q=80&w=1920&auto=format&fit=crop");
        CURATED_URLS.put("com.google.android.youtube.tv", "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?q=80&w=1920&auto=format&fit=crop");

        // Plex (Classic Cinema Theater Red Auditorium & Big Screen)
        CURATED_URLS.put("com.plexapp.android", "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?q=80&w=1920&auto=format&fit=crop");

        // MyTV (Vibrant Television Broadcast Studio & Entertainment Lights)
        CURATED_URLS.put("vn.mytvnet.b2cott", "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?q=80&w=1920&auto=format&fit=crop");

        // Google Play Store (Vibrant Ambient Aurora / Gaming Synthwave)
        CURATED_URLS.put("com.android.vending", "https://images.unsplash.com/photo-1550745165-9bc0b252726f?q=80&w=1920&auto=format&fit=crop");

        // TiviMate (Live Sports Stadium Broadcast Television)
        CURATED_URLS.put("ar.tvplayer.tv", "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?q=80&w=1920&auto=format&fit=crop");

        // Downloader (Modern Orange / Dark Tech Studio)
        CURATED_URLS.put("com.esaba.downloader", "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=1920&auto=format&fit=crop");

        // X-plore (Matrix Cyber Dark Code / Storage Grid)
        CURATED_URLS.put("com.lonelycatgames.Xplore", "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?q=80&w=1920&auto=format&fit=crop");
    }

    public interface Callback {
        void onBackdropLoaded(String pkg, Bitmap bitmap);
    }

    private AppBackdropManager() {}

    public static boolean hasBackdrop(String pkg) {
        return pkg != null && CURATED_URLS.containsKey(pkg);
    }

    public static Bitmap getCachedBitmap(String pkg) {
        return pkg != null ? BITMAP_CACHE.get(pkg) : null;
    }

    public static void preloadAll(Context context) {
        final Context ctx = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            for (String pkg : CURATED_URLS.keySet()) {
                loadBackdrop(ctx, pkg, null);
            }
        });
    }

    public static void loadBackdrop(Context context, String pkg, Callback callback) {
        if (pkg == null) return;
        Bitmap cached = BITMAP_CACHE.get(pkg);
        if (cached != null) {
            if (callback != null) callback.onBackdropLoaded(pkg, cached);
            return;
        }

        final Context appContext = context.getApplicationContext();
        File cacheDir = new File(appContext.getFilesDir(), "backdrops");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        final File file = new File(cacheDir, pkg.replace('.', '_') + ".jpg");

        EXECUTOR.execute(() -> {
            Bitmap bmp = null;
            if (file.exists() && file.length() > 1024) {
                bmp = decodeFile(file, appContext);
            }

            if (bmp == null && CURATED_URLS.containsKey(pkg)) {
                String url = CURATED_URLS.get(pkg);
                if (download(url, file)) {
                    bmp = decodeFile(file, appContext);
                }
            }

            if (bmp != null) {
                BITMAP_CACHE.put(pkg, bmp);
                final Bitmap res = bmp;
                MAIN.post(() -> {
                    if (callback != null) callback.onBackdropLoaded(pkg, res);
                });
            }
        });
    }

    private static boolean download(String urlStr, File dest) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                File temp = new File(dest.getParentFile(), dest.getName() + ".tmp");
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(temp)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        out.write(buf, 0, len);
                    }
                }
                return temp.renameTo(dest);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed downloading backdrop: " + urlStr, e);
        }
        return false;
    }

    private static Bitmap decodeFile(File file, Context context) {
        try {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), opts);
            int w = opts.outWidth, h = opts.outHeight;
            if (w <= 0 || h <= 0) return null;

            int sample = WallpaperSize.sampleSize(w, h, dm.widthPixels, dm.heightPixels);
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565; // Optimal for 1GB/2GB Android TV RAM
            return BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (Exception | OutOfMemoryError e) {
            Log.e(TAG, "Failed decoding backdrop: " + file.getName(), e);
            return null;
        }
    }
}
