package com.leotv.launcher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.SoundEffectConstants;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    private IpWeather cityWeather;
    private TextView weatherLabel;

    private static final int LAUNCHER_SETTINGS_REQUEST = 42;
    /** Sent by QuickSettingsActivity: open Launcher settings from here so Home rebuilds when they close. */
    static final String ACTION_LAUNCHER_SETTINGS = "com.leotv.launcher.action.LAUNCHER_SETTINGS";
    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private boolean rtlAppGrid;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;
    private String liveTvAction;
    private String inputAction;
    private String sourcePanelService;
    private String sourcePanelAction;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private ImageView wallpaperA;
    private ImageView wallpaperB;
    private int activeWallpaperLayer = 0;
    private List<File> slideshowFiles = new ArrayList<>();
    private long wallpaperRevision;
    private int currentSlideIndex = 0;
    private SlideshowTransition slideshowTransition = SlideshowTransition.FADE;
    private long slideshowIntervalMs = 15_000L;
    private final Runnable slideshowRunnable = this::advanceSlideshow;
    private ImageView appBackdropView;
    private View wallpaperDimOverlay;
    private boolean appBackdrops = true;
    private View statusBarPlatter;
    private View dateGroup;
    private View controlsGroup;
    private boolean isStatusBarExpanded = false;
    private View ambient;
    private TextView heroName;
    private int ambientColor = -1;
    private ValueAnimator ambientAnimator;
    private boolean heroShown;
    private boolean lastFocusedIsDock = true;
    private int lastFocusedDockPos = 0;
    private int lastFocusedGridPos = 1;
    private boolean animations;
    private boolean advancedEffects;
    private TextView greeting;
    private View btnInputs;
    private TextView btnWifi;
    private TextView btnProfile;
    private TextView btnSettings;
    private TextView shamsiDate;
    private boolean shamsi;
    /** Optional time-of-day backdrop (config.xml, sky_intensity); 0 keeps it hidden. */
    private SkyView sky;
    private String vpnApp;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    // How far the grid has scrolled the glass. springY works without it, in grid content space, so a
    // scroll moves the glass in lockstep with its tile instead of becoming a moving spring target.
    private float glassScrollShift;
    private boolean glassShown;
    // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
    // or Developer options → "Don't keep activities"); they only reset when the process dies.
    private static int lastFocused = 0;
    private static long lastHomePressTime = 0;
    /** App of the last focused tile, so focus follows it when the app list changes. */
    private static String lastFocusedKey;
    private float density;
    /** Module cards (res/values/config.xml, module_cards): discovered on resume, shown only after the owner's Show. */
    private boolean moduleCards;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    private boolean reducedMotion;
    /** Festive skin from the theme (see Festival); applied while it is active today. */
    private Festival festival = Festival.NONE;
    private boolean festive;
    private DiyaDrawable diya;
    private BellDrawable bell;
    private MarigoldDrawable marigold;
    private int greetingColor;
    /** The day the petal shower last played, so it greets the first Home of each day only. */
    private static String lastPetalShower;
    /** hidden_apps as the adapter sees them (config.xml or a theme's config.json): a hidden app gets no card either. */
    private Set<String> hiddenApps = Collections.emptySet();
    private List<Cards.Card> cards = Collections.emptyList();
    /** The grid has been laid out and focused since the last resume; the card prompt waits for this. */
    private boolean tilesReady;
    private Dialog cardPrompt;
    /** Set when a decision could not be saved: no more prompts until the next resume, instead of asking in a loop. */
    private boolean promptsPaused;
    /** Rebuild the tiles on resume: Home was fully hidden by an app, or Home was pressed. Not after a side panel such as Wi-Fi settings. */
    private boolean rebuildOnResume = true;
    /** The Inputs or Module cards dropdown while one is open, so onPause can close it. */
    private PopupWindow openPopup;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::weatherNetworkChanged);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::weatherNetworkChanged);
        }
    };
    /** Shows the Inputs pill once the TV registers an input: at boot HDMI inputs can appear after Home is shown. */
    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputAdded(String inputId) {
            updateInputsButton();
        }

        @Override
        public void onInputRemoved(String inputId) {
            updateInputsButton();
        }
    };
    /** Listens for any VPN coming up or going down, even one this app's traffic is excluded from. */
    private final ConnectivityManager.NetworkCallback vpnCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::weatherNetworkChanged);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::weatherNetworkChanged);
        }
    };
    private final IntentFilter packageFilter = new IntentFilter();
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Updates send REMOVED followed by ADDED; keep the tile until the new APK is ready.
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
            reloadApps(false);
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            refreshCityWeather();
            updateWifiLabel();
            sky.update();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Typography.apply(findViewById(R.id.root));
        weatherLabel = findViewById(R.id.weatherLabel);
        cityWeather = new IpWeather(this);
        density = getResources().getDisplayMetrics().density;
        // config.xml, overlaid by config.json if present (see Config).
        Config cfg = Config.load(this);
        COLUMNS = cfg.columns;
        animations = cfg.animations;
        advancedEffects = cfg.animations && cfg.advancedEffects;
        reducedMotion = cfg.reducedMotion;
        festival = cfg.festival;
        festive = false;
        liveTvPkg = cfg.liveTvPackage;
        liveTvAction = cfg.liveTvAction;
        inputAction = cfg.inputAction;
        sourcePanelService = cfg.sourcePanelService;
        sourcePanelAction = cfg.sourcePanelAction;

        root = findViewById(R.id.root);
        root.setBackgroundColor(cfg.backgroundColor);
        appBackdrops = cfg.appBackdrops;
        slideshowTransition = cfg.slideshowTransition;
        slideshowIntervalMs = cfg.slideshowInterval * 1000L;

        wallpaperA = findViewById(R.id.wallpaperA);
        wallpaperB = findViewById(R.id.wallpaperB);
        if (wallpaperA != null) {
            wallpaperA.setColorFilter(Color.parseColor("#33000000"), PorterDuff.Mode.DARKEN);
        }
        if (wallpaperB != null) {
            wallpaperB.setColorFilter(Color.parseColor("#33000000"), PorterDuff.Mode.DARKEN);
        }
        appBackdropView = findViewById(R.id.appBackdrop);
        if (appBackdropView != null) {
            appBackdropView.setColorFilter(Color.parseColor("#33000000"), PorterDuff.Mode.DARKEN);
        }
        wallpaperDimOverlay = findViewById(R.id.wallpaperDimOverlay);
        if (appBackdrops) {
            AppBackdropManager.preloadAll(this);
        }

        // Initialize slideshow files from cache
        wallpaperRevision = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getLong("wallpaper_revision", 0);
        slideshowFiles = GooglePhotosWallpaperLoader.getSlideshowFiles(this);
        android.util.Log.i("MainActivity", "Initial slideshowFiles: " + slideshowFiles.size());
        if (!slideshowFiles.isEmpty()) {
            showInitialWallpaper(slideshowFiles.get(0));
        } else if (cfg.wallpaper != null) {
            showInitialWallpaper(cfg.wallpaper);
        }

        if (cfg.googlePhotosAlbum != null && !cfg.googlePhotosAlbum.trim().isEmpty()) {
            GooglePhotosWallpaperLoader.syncSlideshow(this, cfg.googlePhotosAlbum, files -> {
                android.util.Log.i("MainActivity", "syncSlideshow completed with " + (files != null ? files.size() : 0) + " files");
                if (files != null && !files.isEmpty()) {
                    slideshowFiles = files;
                    currentSlideIndex = 0;
                    showInitialWallpaper(slideshowFiles.get(0));
                }
            });
        }
        sky = findViewById(R.id.sky);
        sky.setIntensity(cfg.skyIntensity);
        glass = findViewById(R.id.glass);
        glass.setVisibility(View.GONE);
        ambient = findViewById(R.id.ambient);
        ambient.setVisibility(View.GONE);
        heroName = findViewById(R.id.heroName);
        greeting = findViewById(R.id.greeting);
        greetingColor = greeting.getCurrentTextColor();
        btnWifi = findViewById(R.id.btnWifi);
        shamsiDate = findViewById(R.id.shamsiDate);
        shamsi = cfg.shamsiDate;
        if (shamsi) {
            shamsiDate.setVisibility(View.VISIBLE);
            TextView date = findViewById(R.id.date);
            date.setTextSize(11f);
            date.setPadding(0, (int) (4 * density), 0, 0);
        }
        vpnApp = cfg.vpnApp;
        moduleCards = cfg.moduleCards;
        hiddenApps = new HashSet<>(cfg.hiddenApps);

        grid = findViewById(R.id.grid);
        rtlAppGrid = getResources().getBoolean(R.bool.rtl_app_grid);
        // Mirror only the app grid; the header and other locales keep their own layouts.
        GridLayoutManager glm = new GridLayoutManager(this, COLUMNS) {
            @Override
            protected boolean isLayoutRTL() {
                return rtlAppGrid;
            }

            @Override
            public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                         @NonNull Rect rect, boolean immediate) {
                return true;
            }

            @Override
            public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                         @NonNull Rect rect, boolean immediate,
                                                         boolean focusedChildVisible) {
                return true;
            }
        };
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position == 0 ? COLUMNS : 1;
            }
        });
        grid.setLayoutManager(glm);
        grid.setHasFixedSize(false);
        grid.setItemAnimator(null);
        grid.addItemDecoration(new DockDecoration(COLUMNS, density));
        if (animations) {
            int enterAnim = reducedMotion ? R.anim.tile_enter_reduced : R.anim.tile_enter;
            float enterDelay = reducedMotion ? 0f : 0.06f;
            grid.setLayoutAnimation(new LayoutAnimationController(
                    AnimationUtils.loadAnimation(this, enterAnim), enterDelay));
        }
        adapter = new AppAdapter(this, this, COLUMNS, cfg);
        grid.setAdapter(adapter);

        // tvOS layout: push the dock row towards the bottom of the screen, leaving room for
        // rows_below_dock grid rows under it; the rest of the grid stays below until you press Down.
        int rowsBelowDock = cfg.rowsBelowDock;
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            if (h <= 0) return;
            // Item height follows the tile size, which follows the column count (AppAdapter.tileWidthPx).
            int inner = (r - l) - grid.getPaddingLeft() - grid.getPaddingRight();
            int tileW = AppAdapter.tileWidthPx(inner, COLUMNS, density);
            int tileH = AppAdapter.tileHeightPx(tileW);
            int itemH = AppAdapter.itemHeightPx(tileH, density);
            int dockRowH = tileH + (int) ((6 + 14) * density); // Dock has no label, only tile height + decoration padding
            int gridRows = itemH * rowsBelowDock;
            int bottomClearance = (int) (54 * density);
            int want = h - dockRowH - gridRows - bottomClearance;
            if (want < (int) (100 * density)) {
                want = (int) (100 * density);
            }
            int wantBottom = want;
            if (grid.getPaddingTop() != want || grid.getPaddingBottom() != wantBottom) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), wantBottom);
            }
        });

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                glassScrollShift -= dy;
                glass.setTranslationY(glass.getTranslationY() - dy);
            }
        });

        // Liquid feel: soft springs on the highlight's position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, new FloatPropertyCompat<View>("glassContentY") {
            @Override
            public float getValue(View v) {
                return v.getTranslationY() - glassScrollShift;
            }

            @Override
            public void setValue(View v, float value) {
                v.setTranslationY(value + glassScrollShift);
            }
        });
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            if (reducedMotion) {
                // Stiff and almost critically damped: settles in a few frames instead of oscillating,
                // and stops once the highlight is within 1.5 px of its tile.
                s.setSpring(new SpringForce().setStiffness(1400f).setDampingRatio(0.95f));
                s.setMinimumVisibleChange(1.5f);
            } else {
                s.setSpring(new SpringForce().setStiffness(advancedEffects ? 320f : 260f)
                        .setDampingRatio(advancedEffects ? 0.76f : 0.58f));
            }
        }

        btnInputs = findViewById(R.id.btnInputs);
        btnProfile = findViewById(R.id.btnProfile);
        btnSettings = findViewById(R.id.btnSettings);
        if (btnProfile != null) {
            btnProfile.setOnClickListener(v -> showSettingsMenu());
            btnProfile.setOnLongClickListener(v -> {
                startActivityForResult(new Intent(this, LauncherSettingsActivity.class), LAUNCHER_SETTINGS_REQUEST);
                return true;
            });
        }
        btnInputs.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> openNetworkPill());
        btnWifi.setOnLongClickListener(v -> {
            open(systemIntent(Settings.ACTION_WIFI_SETTINGS));
            return true;
        });
        btnSettings.setOnClickListener(v -> showSettingsMenu());
        btnSettings.setOnLongClickListener(v -> {
            showSettingsMenu();
            return true;
        });

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        boolean rtlMenuBar = getResources().getBoolean(R.bool.rtl_menu_bar);
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // Back to the dock tile Up came from, not wherever the focus finder lands.
                collapseStatusBar(true);
                focusDockTile(lastFocusedDockPos);
                return true;
            }
            int firstButton = btnInputs.getVisibility() == View.VISIBLE ? R.id.btnInputs : R.id.btnWifi;
            int leftButton = rtlMenuBar ? R.id.btnProfile : firstButton;
            int rightButton = rtlMenuBar ? firstButton : R.id.btnProfile;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return v.getId() == leftButton;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == rightButton;
        };
        if (btnProfile != null) btnProfile.setOnKeyListener(pillKeys);
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Collapsible Status Bar Platter: expand on hover/focus, collapse when idle
        statusBarPlatter = findViewById(R.id.statusBarPlatter);
        dateGroup = findViewById(R.id.dateGroup);
        controlsGroup = findViewById(R.id.controlsGroup);
        collapseStatusBar(false);

        if (statusBarPlatter != null) {
            statusBarPlatter.setOnHoverListener((v, event) -> {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_HOVER_ENTER) {
                    expandStatusBar(true);
                } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
                    handler.postDelayed(() -> {
                        if (!isStatusBarFocused() && !statusBarPlatter.isHovered()) {
                            collapseStatusBar(true);
                        }
                    }, 150);
                }
                return false;
            });

            statusBarPlatter.setOnClickListener(v -> {
                if (!isStatusBarExpanded) {
                    expandStatusBar(true);
                    TextView s = findViewById(R.id.btnSettings);
                    if (s != null) s.requestFocus();
                }
            });
        }

        // Fix disappearing icons on hover: change icon color to black when pill is focused, white when unfocused
        // Control Center & Status buttons: No shadow, clean zoom-in on hover and focus
        for (TextView button : new TextView[]{btnProfile, (TextView) btnInputs, btnWifi, btnSettings}) {
            if (button == null) continue;
            button.setElevation(0f);
            button.setTranslationZ(0f);
            button.setShadowLayer(0, 0, 0, 0);

            button.setOnHoverListener((view, event) -> {
                int action = event.getAction();
                boolean hovered = (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE);
                boolean exit = (action == MotionEvent.ACTION_HOVER_EXIT);
                if (hovered) {
                    expandStatusBar(true);
                    view.animate().scaleX(1.18f).scaleY(1.18f).setDuration(160).start();
                } else if (exit) {
                    if (!view.isFocused()) {
                        view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160).start();
                    }
                }
                for (Drawable drawable : ((TextView) view).getCompoundDrawables()) {
                    if (drawable != null) drawable.mutate().setColorFilter(
                            hovered || view.isFocused() ? Color.BLACK : Color.WHITE,
                            PorterDuff.Mode.SRC_IN);
                }
                return false;
            });
            button.setOnFocusChangeListener((view, focused) -> {
                if (focused) {
                    expandStatusBar(true);
                    view.animate().scaleX(1.18f).scaleY(1.18f).setDuration(160).start();
                } else if (!view.isHovered()) {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160).start();
                }
                Drawable[] drawables = ((TextView) view).getCompoundDrawables();
                for (Drawable d : drawables) {
                    if (d != null) {
                        d.mutate().setColorFilter(focused || view.isHovered() ? Color.BLACK : Color.WHITE, PorterDuff.Mode.SRC_IN);
                    }
                }
            });
        }
        if (festive) applyFestiveSkin((TextView) btnInputs, btnSettings);

        // Hide the glass when focus leaves the tiles; collapse status bar when focus returns to tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) { hideGlass(); hideHero(); }
            if (onTile && isStatusBarExpanded) {
                collapseStatusBar(true);
            }
        });

        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageFilter.addDataScheme("package");

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.registerCallback(inputCallback, handler);
        if (cm != null) {
            cm.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), networkCallback);
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), vpnCallback);
        }
        // Not after recreate(): the launch intent survives it and would open the settings again.
        if (savedInstanceState == null) openLauncherSettingsIfAsked(getIntent());
    }

    private boolean openLauncherSettingsIfAsked(Intent intent) {
        if (intent == null || !ACTION_LAUNCHER_SETTINGS.equals(intent.getAction())) return false;
        setIntent(new Intent(Intent.ACTION_MAIN));
        startActivityForResult(new Intent(this, LauncherSettingsActivity.class), LAUNCHER_SETTINGS_REQUEST);
        return true;
    }

    private void expandStatusBar(boolean animate) {
        if (isStatusBarExpanded) return;
        isStatusBarExpanded = true;
        if (animate && animations && statusBarPlatter instanceof ViewGroup) {
            android.transition.TransitionSet set = new android.transition.TransitionSet()
                    .setOrdering(android.transition.TransitionSet.ORDERING_TOGETHER)
                    .addTransition(new android.transition.ChangeBounds())
                    .addTransition(new android.transition.Fade())
                    .setDuration(240)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator());
            android.transition.TransitionManager.beginDelayedTransition((ViewGroup) statusBarPlatter, set);
        }
        if (controlsGroup != null) controlsGroup.setVisibility(View.VISIBLE);
    }

    private void collapseStatusBar(boolean animate) {
        if (!isStatusBarExpanded) return;
        isStatusBarExpanded = false;
        if (animate && animations && statusBarPlatter instanceof ViewGroup) {
            android.transition.TransitionSet set = new android.transition.TransitionSet()
                    .setOrdering(android.transition.TransitionSet.ORDERING_TOGETHER)
                    .addTransition(new android.transition.ChangeBounds())
                    .addTransition(new android.transition.Fade())
                    .setDuration(190)
                    .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            android.transition.TransitionManager.beginDelayedTransition((ViewGroup) statusBarPlatter, set);
        }
        if (controlsGroup != null) controlsGroup.setVisibility(View.GONE);
    }

    private boolean isStatusBarFocused() {
        View focused = getCurrentFocus();
        if (focused == null) return false;
        int id = focused.getId();
        return id == R.id.btnProfile || id == R.id.btnInputs
                || id == R.id.btnWifi || id == R.id.btnSettings
                || id == R.id.statusBarPlatter;
    }

    private Bitmap decodeWallpaper(File file) {
        if (file == null || !file.exists() || file.length() < 1024) return null;
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(file.getPath(), opts);
            int w = opts.outWidth, h = opts.outHeight;
            if (w <= 0 || h <= 0) return null;
            int sample = WallpaperSize.sampleSize(w, h, dm.widthPixels, dm.heightPixels);
            if (!WallpaperSize.accepted(w / sample, h / sample)) return null;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(w / sample, h / sample, dm.widthPixels, dm.heightPixels);
            if (scale != null) {
                opts.inScaled = true;
                opts.inDensity = scale[0];
                opts.inTargetDensity = scale[1];
            }
            opts.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
            return android.graphics.BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private void showInitialWallpaper(File file) {
        Bitmap bmp = decodeWallpaper(file);
        if (bmp != null && wallpaperA != null) {
            stopSlideshow();
            for (ImageView layer : new ImageView[]{wallpaperA, wallpaperB}) {
                if (layer == null) continue;
                layer.animate().withEndAction(null).cancel();
                layer.setScaleX(1f);
                layer.setScaleY(1f);
                layer.setTranslationX(0f);
                layer.setTranslationY(0f);
            }
            wallpaperA.setImageBitmap(bmp);
            wallpaperA.setAlpha(1f);
            if (wallpaperB != null) wallpaperB.setAlpha(0f);
            activeWallpaperLayer = 0;
            startSlideshow();
        }
    }

    private void startSlideshow() {
        if (slideshowFiles != null && slideshowFiles.size() > 1) {
            handler.removeCallbacks(slideshowRunnable);
            handler.postDelayed(slideshowRunnable, slideshowIntervalMs);
        }
    }

    private void stopSlideshow() {
        handler.removeCallbacks(slideshowRunnable);
    }

    private void advanceSlideshow() {
        handler.removeCallbacks(slideshowRunnable);
        if (slideshowFiles == null || slideshowFiles.size() <= 1) return;

        currentSlideIndex = (currentSlideIndex + 1) % slideshowFiles.size();
        File nextFile = slideshowFiles.get(currentSlideIndex);
        android.util.Log.i("MainActivity", "Advancing slideshow to " + nextFile.getName() + " with " + slideshowTransition.title);
        Bitmap nextBmp = decodeWallpaper(nextFile);
        if (nextBmp == null) {
            handler.postDelayed(slideshowRunnable, 3000);
            return;
        }

        ImageView currentView = (activeWallpaperLayer == 0) ? wallpaperA : wallpaperB;
        ImageView nextView = (activeWallpaperLayer == 0) ? wallpaperB : wallpaperA;
        activeWallpaperLayer = 1 - activeWallpaperLayer;

        SlideshowAnimator.apply(currentView, nextView, nextBmp, slideshowTransition, () -> {
            handler.postDelayed(slideshowRunnable, slideshowIntervalMs);
        });
    }

    /**
     * Home pressed while Home is already in front: singleTask delivers it here, then onResume runs.
     * Back to the top with the first dock tile focused, like the stock launcher. Back from an app
     * never comes this way, so returning to the app you left from is unchanged.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (openLauncherSettingsIfAsked(intent)) return;
        if (intent == null || !intent.hasCategory(Intent.CATEGORY_HOME)) return;
        grid.scrollToPosition(0);
        lastFocused = 0;
        lastFocusedIsDock = true;
        lastFocusedDockPos = 0;
        lastFocusedKey = adapter.getDockCount() > 0 ? adapter.dockKeyAt(0) : null;
        focusDockTile(0);
        rebuildOnResume = true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageReceiver, packageFilter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastHomePressTime = android.os.SystemClock.uptimeMillis();
        // A festival that started or ended while Home was in the background: rebuild with or without it.
        if (festive != false) {
            recreate();
            return;
        }
        promptsPaused = false;
        if (rebuildOnResume) {
            tilesReady = false;
            reloadApps(true);
        } else {
            // Always reload tiles on resume to immediately reflect dock changes made in Settings/Dialogs
            reloadTiles();
        }
        rebuildOnResume = false;
        updateGreeting();
        refreshCityWeather();
        updateWifiLabel();
        updateInputsButton();
        sky.update();
        long revision = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getLong("wallpaper_revision", 0);
        if (revision != wallpaperRevision) {
            wallpaperRevision = revision;
            slideshowFiles = GooglePhotosWallpaperLoader.getSlideshowFiles(this);
            currentSlideIndex = 0;
            if (!slideshowFiles.isEmpty()) showInitialWallpaper(slideshowFiles.get(0));
        }
        startSlideshow();
        handler.postDelayed(minuteTick, MINUTE_MS);
        if (festive && diya != null) {
            diya.start();
            String today = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(new java.util.Date());
            if (!reducedMotion && !today.equals(lastPetalShower)) {
                lastPetalShower = today;
                handler.postDelayed(this::playPetalShower, 450);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LAUNCHER_SETTINGS_REQUEST && resultCode == RESULT_OK) recreate();
    }

    private AppActionDialog appActionDialog;
    private String movingAppKey;
    private TextView moveHint;
    private long moveStarted;

    private void beginAppMove(AppAdapter.Entry entry) {
        movingAppKey = entry.component.flattenToShortString();
        moveStarted = android.os.SystemClock.uptimeMillis();
        adapter.beginMove();
        androidx.recyclerview.widget.DefaultItemAnimator moveAnimator = new androidx.recyclerview.widget.DefaultItemAnimator();
        moveAnimator.setSupportsChangeAnimations(false);
        moveAnimator.setMoveDuration(180);
        grid.setItemAnimator(moveAnimator);
        if (moveHint == null) {
            moveHint = new TextView(this);
            moveHint.setTextColor(Color.WHITE);
            moveHint.setTextSize(14);
            moveHint.setGravity(Gravity.CENTER);
            moveHint.setPadding((int)(20*density),(int)(10*density),(int)(20*density),(int)(10*density));
            moveHint.setBackgroundResource(R.drawable.menu_bar_bg);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
            lp.topMargin = (int)(82*density);
            root.addView(moveHint,lp);
            Typography.apply(moveHint);
        }
        int[] area=adapter.location(movingAppKey);
        String directions=area != null && area[0]==1 ? this.getString(R.string.leo_move_horizontal) : this.getString(R.string.leo_move_directions);
        moveHint.setText(this.getString(R.string.leo_move_prefix) + entry.label + " · " + directions + this.getString(R.string.leo_move_help_suffix));
        moveHint.setVisibility(View.VISIBLE);
        restoreMovingFocus();
    }

    private void restoreMovingFocus() {
        if (movingAppKey == null) return;
        int[] at = adapter.location(movingAppKey);
        if (at == null) return;
        if (at[0] == 1) focusDockTile(at[1]);
        else focusGridTile(at[1]+1);
    }

    private void endAppMove(boolean save) {
        if (movingAppKey == null) return;
        adapter.finishMove(save);
        if (moveHint != null) moveHint.setVisibility(View.GONE);
        restoreMovingFocus();
        movingAppKey = null;
        grid.postDelayed(() -> { if (movingAppKey == null) grid.setItemAnimator(null); },220);
    }

    public void showAppActionDialog(AppAdapter.Entry entry) {
        if (entry == null || entry.pkg == null) return;
        if (appActionDialog != null && appActionDialog.isShowing()) return;
        appActionDialog = new AppActionDialog(this, entry, () -> reloadApps(true), () -> beginAppMove(entry));
        appActionDialog.show();
    }

    /** Rebuilds the tiles (and the module cards), then puts focus back where it was. */
    private void reloadApps(boolean animate) {
        // Capture the app before rebinding can move focus and overwrite its key.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final boolean restoreFocus = animate || grid.hasFocus();
        if (restoreFocus) {
            glassShown = false;
            glass.setAlpha(0f);
            heroShown = false;
            heroName.setAlpha(0f);
            ambient.setAlpha(0f);
        }
        reloadTiles();
        if (animate && animations && !reducedMotion) grid.scheduleLayoutAnimation();

        if (restoreFocus) {
            // Follow the same app, or the nearest remaining slot when it was removed.
            restoreFocus();
        } else {
            // A package change must not steal focus from a top button or the Inputs popup.
            int pos = adapter.positionOf(returnKey);
            lastFocused = Math.max(0, Math.min(pos >= 0 ? pos : returnSlot, adapter.getItemCount() - 1));
            lastFocusedKey = adapter.keyAt(lastFocused);
            if (pos < 0) hideHero();
            // A newly installed app may have brought a card with it.
            tilesReady = true;
            showCardPrompt();
        }
    }

    /**
     * Rebuilds the tiles; the module cards the owner chose to show stand in for their app's tile.
     * Every step into the card code runs under CardFeature: if it fails, the tiles are the ordinary
     * ones and cards stay off for this process.
     */
    private void reloadTiles() {
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final List<Cards.Card> none = Collections.emptyList();
        cards = moduleCards ? CardFeature.run(() -> Cards.discover(this, hiddenApps, Cards.load(Cards.storeFile(this))), none) : none;
        adapter.reload(cards.isEmpty() ? none : CardFeature.run(() -> Cards.shown(cards), none));
        lastFocusedKey = returnKey;
        lastFocused = returnSlot;
    }

    /**
     * Return to the app we left from, or the same slot if that app is gone. Always refocus: after
     * reload() the view that holds focus may no longer show the app it had.
     */
    private void restoreFocus() {
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            focusTile(pos >= 0 ? pos : returnSlot);
            tilesReady = true;
            showCardPrompt();
        });
    }

    @Override
    protected void onPause() {
        endAppMove(false);
        super.onPause();
        stopAmbientAnimation();
        stopSlideshow();
        heroName.animate().cancel();
        ambient.animate().cancel();
        handler.removeCallbacks(minuteTick);
        if (diya != null) diya.stop();
        // Nothing of ours stays on screen over another app; an undecided prompt simply returns next time.
        if (cardPrompt != null) cardPrompt.dismiss();
        if (openPopup != null) openPopup.dismiss();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(packageReceiver);
        rebuildOnResume = true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) showCardPrompt();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cityWeather != null) cityWeather.close();
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.unregisterCallback(inputCallback);
        if (cm != null) {
            try { cm.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
            try { cm.unregisterNetworkCallback(vpnCallback); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onBackPressed() {
        if (!lastFocusedIsDock) {
            focusDockTile(0);
            return;
        }
        // Home screen on dock: Back goes nowhere.
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (movingAppKey != null) {
            int key = event.getKeyCode();
            if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_UP && android.os.SystemClock.uptimeMillis()-moveStarted > 220) {
                    endAppMove(key != KeyEvent.KEYCODE_BACK);
                }
                return true;
            }
            if (key >= KeyEvent.KEYCODE_DPAD_UP && key <= KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    adapter.move(movingAppKey,key);
                    grid.post(this::restoreMovingFocus);
                    grid.invalidateItemDecorations();
                }
                return true;
            }
            if (key == KeyEvent.KEYCODE_MENU) return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP) showSettingsMenu();
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                event.startTracking();
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            View focused = getCurrentFocus();
            if (focused != null) {
                View card = focused.getId() == R.id.card ? focused : focused.findViewById(R.id.card);
                if (card != null && card.performLongClick()) {
                    return true;
                }
            }
        }
        return super.onKeyLongPress(keyCode, event);
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card, boolean isDock) {
        lastFocusedIsDock = isDock;
        if (isDock) {
            lastFocusedDockPos = position;
            lastFocused = 0;
            lastFocusedKey = adapter.dockKeyAt(position);
        } else {
            lastFocusedGridPos = position;
            lastFocused = position;
            lastFocusedKey = adapter.gridKeyAt(position - 1);
        }
        if (glass != null) glass.setVisibility(View.GONE);
        if (ambient != null) ambient.setVisibility(View.GONE);

        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);

        View item = (View) card.getParent();
        findViewById(R.id.leftInfoCluster).setVisibility(isDock ? View.VISIBLE : View.INVISIBLE);
        if (statusBarPlatter != null) {
            statusBarPlatter.setVisibility(isDock ? View.VISIBLE : View.INVISIBLE);
        }
        if (isDock) {
            // Dock: clear wallpaper, show dynamic app backdrop if available
            if (wallpaperDimOverlay != null) {
                if (animations) wallpaperDimOverlay.animate().alpha(0.0f).setDuration(240).start();
                else wallpaperDimOverlay.setAlpha(0.0f);
            }
            if (appBackdrops && position < adapter.getDockCount()) {
                String pkg = adapter.dockPkgAt(position);
                if (AppBackdropManager.hasBackdrop(pkg)) {
                    final int targetPos = position;
                    AppBackdropManager.loadBackdrop(this, pkg, (loadedPkg, bmp) -> {
                        if (lastFocusedIsDock && lastFocusedDockPos == targetPos && appBackdropView != null && bmp != null) {
                            appBackdropView.setImageBitmap(bmp);
                            if (animations) appBackdropView.animate().alpha(1.0f).setDuration(300).start();
                            else appBackdropView.setAlpha(1.0f);
                        }
                    });
                } else if (appBackdropView != null) {
                    if (animations) appBackdropView.animate().alpha(0.0f).setDuration(200).start();
                    else appBackdropView.setAlpha(0.0f);
                }
            } else if (appBackdropView != null) {
                if (animations) appBackdropView.animate().alpha(0.0f).setDuration(200).start();
                else appBackdropView.setAlpha(0.0f);
            }

            // Dock: park it at the bottom of the screen and show the top shelf for this app.
            RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(0);
            int dy = 0;
            if (vh != null) {
                View dockRowItem = vh.itemView;
                int decoratedTop = dockRowItem.getTop() - (int) (6 * density);
                dy = decoratedTop - grid.getPaddingTop();
                boolean scroll = Math.abs(dy) > (int) (8 * density);
                if (scroll) {
                    scrollGrid(dy);
                }
            }
            showHero(adapter.dockLabelAt(position), dy != 0 ? r.top - dy : r.top);
        } else {
            // Lower Grid: dim/frosted background so apps and labels pop cleanly
            if (wallpaperDimOverlay != null) {
                if (animations) wallpaperDimOverlay.animate().alpha(0.85f).setDuration(240).start();
                else wallpaperDimOverlay.setAlpha(0.85f);
            }
            if (appBackdropView != null) {
                if (animations) appBackdropView.animate().alpha(0.0f).setDuration(200).start();
                else appBackdropView.setAlpha(0.0f);
            }

            hideHeroName();
            updateAmbientForTile(adapter.gridAmbientColorAt(position - 1));

            // Bottom padding is scroll range for returning to the dock, not an occluded area.
            // Keep the selected row below the header and scroll only when it leaves the viewport.
            int top = item.getTop() - Math.round(18 * density);
            int bottom = item.getBottom() + Math.round(18 * density);
            int limitTop = Math.round(26 * density);
            int limitBottom = grid.getHeight() - Math.round(18 * density);
            int dy = 0;
            int primaryTop = Math.round(grid.getHeight() * 0.40f);
            if (position <= COLUMNS || top < limitTop || bottom > limitBottom) {
                dy = item.getTop() - primaryTop;
            }
            if (Math.abs(dy) > Math.round(4 * density)) scrollGrid(dy);
        }
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private void showHero(String label, float dockTopInRoot) {
        final String text = label == null ? "" : label;
        int heroH = heroName.getHeight();
        if (heroH == 0) {
            heroName.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            heroH = heroName.getMeasuredHeight();
        }
        float y = dockTopInRoot - 14 * density - 26 * density - heroH;
        boolean changed = !text.contentEquals(heroName.getText());

        if (ambient != null) ambient.setVisibility(View.GONE);

        if (!animations || reducedMotion) {
            heroName.animate().cancel();
            heroName.setText(text);
            heroName.setY(y);
            heroName.setTranslationX(0f);
            heroName.setAlpha(1f);
            heroShown = true;
            return;
        }

        if (!heroShown) {
            heroName.animate().cancel();
            heroName.setText(text);
            heroName.setY(y);
            heroName.setTranslationX(0f);
            heroName.animate().alpha(1f).setDuration(260).start();
            heroShown = true;
        } else if (changed) {
            heroName.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction(() -> {
                heroName.setText(text);
                heroName.setY(y);
                heroName.setTranslationX(12 * density);
                heroName.animate().alpha(1f).translationX(0f).setDuration(200).start();
            }).start();
        } else {
            heroName.setY(y);
        }
    }

    /**
     * The glow used to be a full-screen radial GradientDrawable. On Android 10 that drawable is
     * drawn on the CPU, so fading it for half a second redrew 1920×1080 every frame and the
     * highlight looked like it was running at a few frames per second. A small bitmap stretched
     * over the screen is one texture. It is painted once in white and tinted with the app's colour,
     * so changing apps neither repaints it on the CPU (about 11 ms) nor uploads it again.
     */
    private void setAmbient(int color) {
        if (color == ambientColor && ambient.getBackground() != null) return;
        ambientColor = color;
        BitmapDrawable glow = (BitmapDrawable) ambient.getBackground();
        if (glow == null) {
            int w = 480;
            int h = 270;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(new RadialGradient(
                    w * 0.32f, h * 0.42f, w * 0.62f,
                    new int[]{0x5CFFFFFF, 0x26FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            glow = new BitmapDrawable(getResources(), bmp);
            glow.setGravity(Gravity.FILL);
            ambient.setBackground(glow);
        }
        // The default tint mode, SRC_IN, keeps the mask's alpha and takes the colour from the tint.
        glow.setTint(0xFF000000 | color);
    }

    private void transitionAmbient(int color) {
        // A rapid focus change continues from the colour actually displayed on screen.
        stopAmbientAnimation();
        ambient.animate().cancel();
        if (color == ambientColor) {
            ambient.animate().alpha(1f).setDuration(180).start();
            return;
        }
        ambient.animate().alpha(1f).setDuration(180).start();
        ambientAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), ambientColor, color);
        ambientAnimator.setDuration(360);
        ambientAnimator.addUpdateListener(animation -> setAmbient((int) animation.getAnimatedValue()));
        ambientAnimator.start();
    }

    private void stopAmbientAnimation() {
        if (ambientAnimator != null) {
            ambientAnimator.cancel();
            ambientAnimator = null;
        }
    }

    private void hideHeroName() {
        if (!heroShown) return;
        heroName.animate().cancel();
        heroShown = false;
        if (animations) {
            heroName.animate().alpha(0f).setDuration(reducedMotion ? 100 : 160).start();
        } else {
            heroName.setAlpha(0f);
        }
    }

    private void updateAmbientForTile(int color) {
        if (ambient != null) ambient.setVisibility(View.GONE);
    }

    private void hideHero() {
        if (!heroShown) return;
        stopAmbientAnimation();
        heroName.animate().cancel();
        ambient.animate().cancel();
        heroShown = false;
        if (animations) {
            heroName.animate().alpha(0f).setDuration(reducedMotion ? 100 : 160).start();
            ambient.animate().alpha(0f).setDuration(reducedMotion ? 140 : 300).start();
        } else {
            heroName.setAlpha(0f);
            ambient.setAlpha(0f);
        }
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().cancel();
        if (animations) glass.animate().alpha(0f).setDuration(160).start();
        else glass.setAlpha(0f);
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode, boolean isDock) {
        if (isDock) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (adapter.getGridItemCount() > 0) {
                    int col = Math.min(position, COLUMNS - 1);
                    int target = 1 + Math.min(col, adapter.getGridItemCount() - 1);
                    focusGridTile(target);
                    grid.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                    return true;
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                collapseStatusBar(true);
                btnProfile.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (position == 0) return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (position >= adapter.getDockCount() - 1) return true;
            }
            return false;
        } else {
            int gridIndex = position - 1;
            int gridCount = adapter.getGridItemCount();
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                return GridNavigation.atHorizontalEdge(gridIndex, gridCount, COLUMNS,
                        rtlAppGrid, keyCode == KeyEvent.KEYCODE_DPAD_LEFT);
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (gridIndex < COLUMNS) {
                    int targetDock = Math.min(gridIndex, adapter.getDockCount() - 1);
                    focusDockTile(targetDock);
                    grid.playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                    return true;
                }
                int target = position - COLUMNS;
                focusGridTile(target);
                grid.playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                int lastRowStart = (gridCount > 0 ? (gridCount - 1) / COLUMNS * COLUMNS : 0);
                if (gridIndex >= lastRowStart) return true;
                int target = 1 + Math.min(gridIndex + COLUMNS, gridCount - 1);
                focusGridTile(target);
                grid.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                return true;
            }
            return false;
        }
    }

    private void focusDockTile(int dockPos) {
        if (adapter.getDockCount() == 0) return;
        final int safePos = Math.max(0, Math.min(dockPos, adapter.getDockCount() - 1));
        lastFocusedIsDock = true;
        lastFocusedDockPos = safePos;
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(0);
        if (vh instanceof AppAdapter.DockRowHolder) {
            ((AppAdapter.DockRowHolder) vh).focusTile(safePos);
        } else {
            grid.scrollToPosition(0);
            grid.post(() -> {
                RecyclerView.ViewHolder vh2 = grid.findViewHolderForAdapterPosition(0);
                if (vh2 instanceof AppAdapter.DockRowHolder) {
                    ((AppAdapter.DockRowHolder) vh2).focusTile(safePos);
                }
            });
        }
    }

    private void focusGridTile(int targetGridPos) {
        if (adapter.getItemCount() <= 1) return;
        final int safePos = Math.max(1, Math.min(targetGridPos, adapter.getItemCount() - 1));
        lastFocusedIsDock = false;
        lastFocusedGridPos = safePos;
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(safePos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            if (card != null) card.requestFocus();
        } else {
            androidx.recyclerview.widget.LinearSmoothScroller scroller = new androidx.recyclerview.widget.LinearSmoothScroller(this) {
                @Override public int calculateDyToMakeVisible(View view,int snap) {
                    return Math.round(grid.getHeight()*0.40f)-view.getTop();
                }
                @Override protected float calculateSpeedPerPixel(android.util.DisplayMetrics metrics) {
                    return 100f/metrics.densityDpi;
                }
            };
            grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override public void onScrollStateChanged(@NonNull RecyclerView rv,int state) {
                    if (state != RecyclerView.SCROLL_STATE_IDLE) return;
                    rv.removeOnScrollListener(this);
                    RecyclerView.ViewHolder target=rv.findViewHolderForAdapterPosition(safePos);
                    if (target != null) target.itemView.findViewById(R.id.card).requestFocus();
                }
            });
            scroller.setTargetPosition(safePos);
            grid.getLayoutManager().startSmoothScroll(scroller);
        }
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) {
            btnWifi.requestFocus();
            return;
        }
        if (position == 0) {
            focusDockTile(lastFocusedDockPos);
        } else {
            focusGridTile(position);
        }
    }

    private void scrollGrid(int dy) {
        grid.stopScroll();
        if (!animations) grid.scrollBy(0, dy);
        else grid.smoothScrollBy(0, dy, new android.view.animation.DecelerateInterpolator(1.4f), 200);
    }

    private void weatherNetworkChanged() {
        if (isDestroyed()) return;
        updateWifiLabel();
        if (cityWeather != null) {
            cityWeather.networkChanged();
            if (hasWindowFocus()) refreshCityWeather();
        }
    }

    private void refreshCityWeather() {
        if (cityWeather != null && weatherLabel != null) {
            cityWeather.refresh(label -> {
                if (!isFinishing() && !isDestroyed()) {
                    weatherLabel.setText(label);
                    weatherLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            new WeatherGlyph(density,cityWeather.weatherCode(),cityWeather.isDay()),null,null,null);
                    weatherLabel.setCompoundDrawablePadding(Math.round(5*density));
                }
            });
        }
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateGreeting() {
        TextView lunar = findViewById(R.id.lunarDate);
        if (lunar != null) lunar.setText(VietnameseLunarDate.todayLabel()
                .replace("ÂL ", getString(R.string.leo_lunar_prefix))
                .replace(" nhuận", getString(R.string.leo_lunar_leap)));
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        String festiveGreeting = festive ? festival.greetingText() : "";
        if (!festiveGreeting.isEmpty()) {
            greeting.setText(festiveGreeting);
            greeting.setTextColor(liftTowardsWhite(festival.accentColor(), 0.25f));
        } else {
            greeting.setText(res);
            greeting.setTextColor(greetingColor);
        }
        if (shamsi) shamsiDate.setText(Shamsi.today());
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        boolean vpn = false, online = false;
        int icon = R.drawable.ic_wifi;
        // The diya's flame: 0 is out (offline), 1 to 4 follow the Wi-Fi signal, Ethernet burns steady at 4.
        int flame = 0;
        boolean steady = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean wifi = false, ethernet = false;
            // All networks, not just the active one: with a VPN up the active network is the VPN itself.
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null) continue;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpn = true;
                    continue;
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                online = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
            }
            if (!online) {
                text = getString(R.string.offline);
                vpn = false;
            } else if (ethernet) {
                text = getString(R.string.ethernet);
                icon = R.drawable.ic_ethernet;
                flame = 4;
                steady = true;
            } else if (wifi) {
                text = getString(R.string.wifi);
                flame = 3;
            }
        } catch (Exception ignored) {
        }
        // With a VPN app configured, a VPN that is down matters too: show a red "VPN off" capsule then.
        boolean vpnDown = !vpn && online && !vpnApp.isEmpty();
        if (vpn || vpnDown) {
            // Network name, then the green lock-and-VPN (or red "VPN off") capsule drawn in place of the word.
            String badge = getString(vpn ? R.string.vpn : R.string.vpn_off);
            VpnBadge span = new VpnBadge(vpn ? getDrawable(R.drawable.ic_lock) : null, density);
            // The pill has a maximum width and ellipsizes at the end, which would cut the badge off after
            // a long network name, so shorten the name to whatever room the badge leaves. The total paddings
            // include the Wi-Fi icon.
            TextPaint paint = btnWifi.getPaint();
            float room = btnWifi.getMaxWidth() - btnWifi.getTotalPaddingStart() - btnWifi.getTotalPaddingEnd()
                    - span.getSize(paint, badge, 0, badge.length(), null) - paint.measureText(" ") - 2 * density;
            text = TextUtils.ellipsize(text, paint, Math.max(0, room), TextUtils.TruncateAt.END).toString();
            SpannableString label = new SpannableString(text + " " + badge);
            label.setSpan(span, text.length() + 1, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            btnWifi.setText(label);
        } else {
            btnWifi.setText(text);
        }
        btnWifi.setContentDescription(btnWifi.getText());
        btnWifi.setText("");
        if (festive && diya != null) {
            if (online && flame == 0) flame = 3;   // online some other way (cellular, USB tethering)
            diya.setSteady(steady);
            diya.setFlame(flame);
        } else {
            btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        }
        Drawable[] drawables = btnWifi.getCompoundDrawables();
        for (Drawable d : drawables) {
            if (d != null) {
                d.mutate().setColorFilter(btnWifi.isFocused() ? Color.BLACK : Color.WHITE, PorterDuff.Mode.SRC_IN);
            }
        }
    }

    // ---- festive skin ------------------------------------------------------------------------------

    /**
     * Swaps the pill icons for the festival's: a bell for Inputs that swings when focused, a diya for the
     * network whose flame follows the connection, and a marigold for Settings that turns when focused.
     */
    private void applyFestiveSkin(TextView inputs, TextView settings) {
        bell = new BellDrawable(density);
        marigold = new MarigoldDrawable(density);
        diya = new DiyaDrawable(density);
        bell.setReducedMotion(reducedMotion);
        marigold.setReducedMotion(reducedMotion);
        diya.setReducedMotion(reducedMotion);
        setFestiveIcon(inputs, bell);
        setFestiveIcon(btnWifi, diya);
        setFestiveIcon(settings, marigold);
        addFocusAction(inputs, bell::ring);
        addFocusAction(settings, marigold::turn);
    }

    private static void setFestiveIcon(TextView pill, Drawable icon) {
        // The pills tint their icons to follow focus; the festive ones keep their own colours.
        // API 22 uses the drawable colors directly.
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
    }

    /** Runs {@code action} when the view gains focus, keeping any focus listener it already had. */
    private static void addFocusAction(View view, Runnable action) {
        View.OnFocusChangeListener previous = view.getOnFocusChangeListener();
        view.setOnFocusChangeListener((v, focused) -> {
            if (previous != null) previous.onFocusChange(v, focused);
            if (focused) action.run();
        });
    }

    private void playPetalShower() {
        if (isFinishing() || isDestroyed()) return;
        PetalShowerView shower = new PetalShowerView(this);
        root.addView(shower, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        shower.play(60, 2200, () -> root.removeView(shower));
    }

    private static int liftTowardsWhite(int argb, float amount) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r += (int) ((255 - r) * amount);
        g += (int) ((255 - g) * amount);
        b += (int) ((255 - b) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** The network pill opens the VPN app when one is configured and installed, otherwise Wi-Fi settings. */
    private void openNetworkPill() {
        Intent i = vpnApp.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(vpnApp);
        if (i == null && !vpnApp.isEmpty()) i = getPackageManager().getLaunchIntentForPackage(vpnApp);
        open(i != null ? i : systemIntent(Settings.ACTION_WIFI_SETTINGS));
    }


    /**
     * China-market MIUI TV firmware answers no standard Settings intents at all; open Xiaomi's own
     * settings app (com.xiaomi.mitv.settings) there, so the Settings and Wi-Fi pills keep working.
     * On firmware with a standard settings handler the behaviour is unchanged.
     */
    private Intent systemIntent(String standardAction) {
        Intent tv = new Intent(standardAction).setPackage("com.android.tv.settings");
        if (tv.resolveActivity(getPackageManager()) != null) return tv;
        Intent i = new Intent(standardAction);
        if (i.resolveActivity(getPackageManager()) != null) return i;
        i = new Intent().setComponent(new ComponentName(
                "com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.entry.MainActivity"));
        if (i.resolveActivity(getPackageManager()) != null) return i;
        return new Intent(standardAction);   // nothing found: let open() show the can't-open toast
    }

    /** Hides the Inputs pill when it would have nothing to list: streaming boxes have no HDMI-in, AV or tuner. */
    private void updateInputsButton() {
        boolean any = liveTvIntent() != null || !sourcePanelService.isEmpty();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (!any && tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput()) {
                    any = true;
                    break;
                }
            }
        }
        btnInputs.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    /** The Live TV app's launch intent: its leanback entry point, or the configured action if it has none. */
    private Intent liveTvIntent() {
        if (liveTvPkg.isEmpty()) return null;
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (i != null || liveTvAction.isEmpty()) return i;
        Intent byAction = new Intent(liveTvAction).setPackage(liveTvPkg);
        return getPackageManager().resolveActivity(byAction, 0) != null ? byAction : null;
    }

    /** The TV's own source-selection panel, for TVs that have no TvInputManager (TCL Android TV 9). */
    private void openSourcePanel() {
        Intent i = new Intent(sourcePanelAction);
        ComponentName c = ComponentName.unflattenFromString(sourcePanelService);
        if (c != null) i.setComponent(c);
        try {
            startService(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /** Switches to a passthrough input (HDMI, AV). The standard way is ACTION_VIEW on its channel URI,
     *  which the Live TV app handles; where nothing takes that, the configured `input_action` is sent
     *  with the same URI instead (XGIMI's HDMI player only answers com.xgimi.action.hdmiPlayer). */
    private void openInput(String inputId) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(view);
            return;
        } catch (ActivityNotFoundException e) {
            if (inputAction.isEmpty()) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        open(new Intent(inputAction).setDataAndType(uri, "vnd.android.cursor.item/channel"));
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View anchor = findViewById(R.id.btnInputs);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = glassPopup(panel, width);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        boolean tunerIsHw = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
            // so drop the HDMI status then. A TV with every port really in use loses it too.
            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    // Prefer a hardware tuner: the framework names those ".../HW<n>". System apps such as
                    // AirPlay also register software inputs of type TUNER and may sort first (seen on TCL G05).
                    boolean hw = info.getId().matches(".*/HW\\d+$");
                    if (tunerId == null || (hw && !tunerIsHw) || (hw == tunerIsHw && info.getId().compareTo(tunerId) < 0)) {
                        tunerId = info.getId();
                        tunerIsHw = hw;
                    }
                }
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> openInput(id));
            }
        }
        // Nothing to list where there is no TvInputManager: the TV's own source panel switches inputs.
        if (rows.getChildCount() == 0 && !sourcePanelService.isEmpty()) {
            addInputRow(rows, popup, R.drawable.ic_input, getString(R.string.inputs), -1, this::openSourcePanel);
        }
        Intent liveTv = liveTvIntent();
        if (liveTv != null && tunerId != null) {
            liveTv = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId), "vnd.android.cursor.item/channel")
                    .setPackage(liveTvPkg);
        }
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvIntent = liveTv;
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTvIntent));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(width, panel.getMeasuredWidth()));

        int alignment = getResources().getBoolean(R.bool.rtl_menu_bar) ? Gravity.END : Gravity.START;
        dropIn(popup, panel, anchor, alignment);
        rows.getChildAt(0).requestFocus();
    }

    private PopupWindow glassPopup(View panel, int width) {
        Typography.apply(panel);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (openPopup == popup) openPopup = null;
        });
        openPopup = popup;
        return popup;
    }

    /** Drop in: fade and unfold from the top edge, hanging under the anchor pill. */
    private void dropIn(PopupWindow popup, View panel, View anchor, int gravity) {
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), gravity);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        row.setElevation(0f);
        row.setTranslationZ(0f);
        row.setOnHoverListener((v, event) -> {
            int actionHover = event.getAction();
            if (actionHover == MotionEvent.ACTION_HOVER_ENTER || actionHover == MotionEvent.ACTION_HOVER_MOVE) {
                v.animate().scaleX(1.035f).scaleY(1.035f).setDuration(140).start();
            } else if (actionHover == MotionEvent.ACTION_HOVER_EXIT) {
                if (!v.isFocused()) {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start();
                }
            }
            return false;
        });
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    // ---- launcher settings and module cards -------------------------------------------------------

    /** Remote Menu and the status control open the launcher control panel. */
    private void showSettingsMenu() {
        View anchor = findViewById(R.id.statusBarPlatter);
        View panel = getLayoutInflater().inflate(R.layout.popup_settings, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = glassPopup(panel, Math.round(250 * density));
        addSettingsMenuRow(rows, popup, R.drawable.ic_home_settings, R.string.launcher_settings,
                () -> startActivityForResult(new Intent(this, LauncherSettingsActivity.class), LAUNCHER_SETTINGS_REQUEST));
        addSettingsMenuRow(rows, popup, R.drawable.ic_apps, R.string.menu_dock_apps,
                () -> new DockManagerDialog(this, () -> reloadApps(true)).show());
        addSettingsMenuRow(rows, popup, R.drawable.ic_wifi, R.string.quick_network,
                () -> open(systemIntent(Settings.ACTION_WIFI_SETTINGS)));
        addSettingsMenuRow(rows, popup, R.drawable.ic_settings, R.string.tv_settings,
                () -> open(systemIntent(Settings.ACTION_SETTINGS)));
        if (moduleCards) addSettingsMenuRow(rows, popup, R.drawable.ic_apps, R.string.module_cards, this::showModuleCards);
        // Keep the panel above the dock; scroll its rows on shorter screens.
        int[] anchorAt = new int[2];
        int[] rootAt = new int[2];
        anchor.getLocationOnScreen(anchorAt);
        root.getLocationOnScreen(rootAt);
        int limit = rootAt[1] + root.getHeight() - Math.round(16 * density);
        RecyclerView.ViewHolder dock = grid.findViewHolderForAdapterPosition(0);
        if (dock != null && dock.itemView.isShown()) {
            int[] dockAt = new int[2];
            dock.itemView.getLocationOnScreen(dockAt);
            if (dockAt[1] > anchorAt[1] + anchor.getHeight()) {
                limit = Math.min(limit, dockAt[1] - Math.round(28 * density));
            }
        }
        int room = Math.max(1, limit - anchorAt[1] - anchor.getHeight() - Math.round(10 * density));
        panel.measure(View.MeasureSpec.makeMeasureSpec(popup.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        popup.setHeight(Math.min(panel.getMeasuredHeight(), room));
        dropIn(popup, panel, anchor, Gravity.RIGHT);
        rows.getChildAt(0).requestFocus();
    }

    private void addSettingsMenuRow(LinearLayout rows, PopupWindow popup, int icon, int title, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        row.getLayoutParams().height = Math.round(40 * density);
        ((TextView) row.findViewById(R.id.title)).setTextSize(13);
        View iconView = row.findViewById(R.id.icon);
        iconView.getLayoutParams().width = Math.round(18 * density);
        iconView.getLayoutParams().height = Math.round(18 * density);
        row.setElevation(0f);
        row.setTranslationZ(0f);
        row.setOnHoverListener((v, event) -> {
            int actionHover = event.getAction();
            if (actionHover == MotionEvent.ACTION_HOVER_ENTER || actionHover == MotionEvent.ACTION_HOVER_MOVE) {
                v.animate().scaleX(1.035f).scaleY(1.035f).setDuration(140).start();
            } else if (actionHover == MotionEvent.ACTION_HOVER_EXIT) {
                if (!v.isFocused()) {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start();
                }
            }
            return false;
        });
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        // The OK-up after opening the menu must not activate its first row.
        final boolean[] pressed = {false};
        row.setOnKeyListener((v, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) pressed[0] = true;
                return true;
            }
            boolean activate = pressed[0];
            pressed[0] = false;
            if (activate) {
                popup.dismiss();
                action.run();
            }
            return true;
        });
        rows.addView(row);
    }

    /**
     * Asks about the next card that has no decision for its current signing key. One prompt at a
     * time, only over a laid out grid with window focus (not under a popup or another app), and
     * the next one follows as soon as this one is answered. Nothing is shown until the owner says Show.
     */
    private void showCardPrompt() {
        if (!moduleCards || !tilesReady || promptsPaused || cardPrompt != null || !hasWindowFocus() || isFinishing()) return;
        if (cards.isEmpty() || !CardFeature.working()) return;
        CardFeature.run(() -> {
            showCardPromptNow();
            return null;
        }, null);
    }

    private void showCardPromptNow() {
        final Cards.Card card = Cards.firstPending(cards);
        if (card == null) return;

        View panel = getLayoutInflater().inflate(R.layout.dialog_card_prompt, root, false);
        ((TextView) panel.findViewById(R.id.title)).setText(getString(R.string.card_prompt_title, card.appLabel));
        ((TextView) panel.findViewById(R.id.body)).setText(card.state == Cards.State.RESIGNED
                ? getString(R.string.card_prompt_resigned, card.appLabel)
                : card.state == Cards.State.RELABELLED
                ? getString(R.string.card_prompt_relabelled, card.appLabel)
                : getString(R.string.card_prompt_body, card.appLabel));
        TextView names = panel.findViewById(R.id.names);
        if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) names.setVisibility(View.GONE);
        else names.setText(getString(R.string.card_prompt_names, card.label, card.appLabel));
        ((TextView) panel.findViewById(R.id.identity)).setText(
                getString(R.string.card_prompt_identity, card.pkg, Cards.shortDigest(card.digest)));
        TextView keep = panel.findViewById(R.id.btnKeep);
        TextView show = panel.findViewById(R.id.btnShow);

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(false);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (440 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.6f);
        }
        // One decision per prompt: the buttons and Back (cancel) all end here.
        final boolean[] decided = {false};
        View.OnClickListener choose = v -> {
            if (decided[0]) return;
            decided[0] = true;
            cardPrompt = null;
            dialog.dismiss();
            decideCard(card, v.getId() == R.id.btnShow);   // guards its own card calls; the tile reload stays outside
            restoreFocus();   // the tiles changed under the prompt; this also chains the next prompt
        };
        keep.setOnClickListener(choose);
        show.setOnClickListener(choose);
        dialog.setOnCancelListener(d -> choose.onClick(keep));
        dialog.setOnDismissListener(d -> {
            if (cardPrompt == dialog) cardPrompt = null;
        });
        // Left and Right move between the two buttons; Up and Down stay put; the ends don't wrap.
        View.OnKeyListener keys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return v.getId() == R.id.btnKeep;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnShow;
        };
        keep.setOnKeyListener(keys);
        show.setOnKeyListener(keys);
        cardPrompt = dialog;
        dialog.show();
        keep.requestFocus();
    }

    /** Writes the owner's decision and rebuilds the tiles; focus is left where it is. */
    private void decideCard(Cards.Card card, boolean show) {
        boolean saved = CardFeature.run(() -> Cards.decide(Cards.storeFile(this), card, show), false);
        if (!saved) {
            Toast.makeText(this, R.string.card_not_saved, Toast.LENGTH_SHORT).show();
            promptsPaused = true;
        }
        reloadTiles();
    }

    /** Dropdown under the Settings pill (hold OK): every discovered card, its package and key; OK toggles Shown and Hidden. */
    private void showModuleCards() {
        if (cards.isEmpty() || !CardFeature.working()) {
            Toast.makeText(this, R.string.no_module_cards, Toast.LENGTH_SHORT).show();
            return;
        }
        CardFeature.run(() -> {
            showModuleCardsNow();
            return null;
        }, null);
    }

    private void showModuleCardsNow() {
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_cards, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = glassPopup(panel, (int) (400 * density));
        List<Cards.Card> listed = new ArrayList<>(cards);
        Collections.sort(listed, Cards::byLabel);
        for (Cards.Card card : listed) {
            View row = getLayoutInflater().inflate(R.layout.item_card_row, rows, false);
            row.setTag(card.pkg);
            ((TextView) row.findViewById(R.id.title)).setText(card.label);
            TextView app = row.findViewById(R.id.app);
            if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) app.setVisibility(View.GONE);
            else app.setText(getString(R.string.card_app_name, card.appLabel));
            ((TextView) row.findViewById(R.id.pkg)).setText(card.pkg);
            ((TextView) row.findViewById(R.id.digest)).setText(Cards.longDigest(card.digest));
            final String pkg = card.pkg;
            row.setOnClickListener(v -> CardFeature.run(() -> {
                // The list stays open; the decision is for the card as it is now, not as it was listed.
                Cards.Card now = cardFor(pkg);
                if (now == null) return null;
                decideCard(now, now.state != Cards.State.SHOWN);
                // Every row, not only this one: a toggle can move another card across the limit of 8.
                updateCardStates(rows);
                return null;
            }, null));
            rows.addView(row);
        }
        updateCardStates(rows);
        // Scroll inside the panel instead of running off the bottom of a 720p screen.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int room = root.getHeight() - (at[1] + anchor.getHeight()) - (int) (34 * density);
        popup.setHeight(Math.min(panel.getMeasuredHeight(), Math.max(room, (int) (120 * density))));
        dropIn(popup, panel, anchor, Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    private Cards.Card cardFor(String pkg) {
        for (Cards.Card c : cards) if (c.pkg.equals(pkg)) return c;
        return null;
    }

    /** Writes the state of every row in the Module cards list from the current card list. */
    private void updateCardStates(LinearLayout rows) {
        List<Cards.Card> drawn = Cards.shown(cards);
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            Cards.Card card = row.getTag() instanceof String ? cardFor((String) row.getTag()) : null;
            ((TextView) row.findViewById(R.id.state)).setText(card != null ? stateText(card, drawn.contains(card)) : "");
        }
    }

    /** The row's state: a card the owner set to Shown but which is beyond the eight drawn says so. */
    private String stateText(Cards.Card card, boolean drawn) {
        switch (card.state) {
            case SHOWN:
                return getString(drawn ? R.string.card_state_shown : R.string.card_state_over_limit);
            case HIDDEN:
                return getString(R.string.card_state_hidden);
            case RESIGNED:
                return getString(R.string.card_state_new_key);
            case RELABELLED:
                return getString(R.string.card_state_new_label);
            default:
                return getString(R.string.card_state_new);
        }
    }
}
