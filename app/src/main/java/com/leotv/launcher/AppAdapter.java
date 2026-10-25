package com.leotv.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * App tiles with tvOS 18 horizontal scrolling Dock (Max 10 apps) and alphabetical/card Grid.
 */
public class AppAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_DOCK = 0;
    public static final int VIEW_TYPE_GRID = 1;

    public interface TileListener {
        void onTileFocused(int position, View card, boolean isDock);
        boolean onTileVerticalKey(int position, int keyCode, boolean isDock);
    }

    public static final class Entry {
        public static final int APP = 0, HOME_SETTINGS = 1, CARD = 2;

        public final int kind;
        public final String label;
        public final ComponentName component;
        public final String pkg;
        public final Drawable banner;
        public final Drawable icon;
        public final String name;
        int ambient;

        public Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this(APP, label, component, banner, icon, null, 0);
        }

        public Entry(int kind, String label, ComponentName component, Drawable banner, Drawable icon, String caption, int accent) {
            this.kind = kind;
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
            this.name = caption == null ? label : label + " \u00B7 " + caption;
            this.ambient = accent;
        }
    }

    static final float FOCUS_SCALE = 1.18f;
    static final float DESIGN_W_DP = 220f, DESIGN_H_DP = 132f;

    public static int tileWidthPx(int innerWidthPx, int columns, float density) {
        int design = Math.round(DESIGN_W_DP * density);
        if (innerWidthPx <= 0 || columns <= 0) return design;
        int cell = innerWidthPx / columns;
        int fit = (int) ((cell - 12 * density) / FOCUS_SCALE);
        return Math.max(Math.round(80 * density), Math.min(design, fit));
    }

    static int dockEdgePaddingPx(int tileWidth, float density) {
        return (int) Math.ceil(tileWidth * (FOCUS_SCALE - 1f) / 2f) + Math.round(12 * density);
    }

    public static int tileHeightPx(int tileWidthPx) {
        return Math.round(tileWidthPx * DESIGN_H_DP / DESIGN_W_DP);
    }

    public static int itemHeightPx(int tileHeightPx, float density) {
        return tileHeightPx + Math.round(45 * density);
    }

    private final Context ctx;
    private final TileListener listener;
    private final int columns;
    private final List<String> favourites = new ArrayList<>();
    private final Set<String> hidden;
    private final boolean animations;
    private final boolean advancedEffects;
    private final boolean showHomeSettings;
    private final boolean reducedMotion;

    private final List<Entry> dockItems = new ArrayList<>();
    private final List<Entry> gridItems = new ArrayList<>();
    private DockRowHolder activeDockRowHolder;

    public AppAdapter(Context ctx, TileListener listener, int columns, Config cfg) {
        this.ctx = ctx;
        this.listener = listener;
        this.columns = columns;
        this.favourites.addAll(cfg.dockApps);
        this.hidden = new HashSet<>(cfg.hiddenApps);
        this.animations = cfg.animations;
        this.advancedEffects = cfg.animations && cfg.advancedEffects;
        this.showHomeSettings = cfg.showHomeSettingsShortcut;
        this.reducedMotion = cfg.reducedMotion;
        setHasStableIds(true);
    }

    public void reload(List<Cards.Card> cards) {
        favourites.clear();
        favourites.addAll(Config.getDockApps(ctx));
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        Map<String, Cards.Card> cardByPkg = new HashMap<>();
        for (Cards.Card c : cards) cardByPkg.put(c.pkg, c);

        List<Entry> allEntries = new ArrayList<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            Cards.Card card = cardByPkg.get(pkg);
            if (card != null && !card.component.equals(component)) continue;
            Drawable banner = loadBanner(pm, ri);
            Drawable icon = ri.loadIcon(pm);
            if (isSmartYouTube(pkg)) {
                banner = ctx.getDrawable(R.drawable.banner_youtube);
            }
            if ("com.android.vending".equals(pkg)) {
                banner = new AppStoreBanner(ctx);
                icon = ctx.getDrawable(R.drawable.official_app_store);
            }
            if (card != null) {
                allEntries.add(new Entry(Entry.CARD, card.label, component, banner, icon, card.caption, card.accent));
            } else {
                String appLabel;
                if (isSmartYouTube(pkg)) {
                    appLabel = "YouTube";
                } else if ("com.android.vending".equals(pkg)) {
                    appLabel = "App Store";
                } else {
                    appLabel = Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg);
                }
                allEntries.add(new Entry(appLabel, component, banner, icon));
            }
        }

        List<Entry> freshDock = new ArrayList<>();
        List<Entry> freshGrid = new ArrayList<>();

        for (String favPkg : favourites) {
            for (Entry e : allEntries) {
                if (e.pkg.equals(favPkg) && !freshDock.contains(e) && freshDock.size() < columns) {
                    freshDock.add(e);
                    break;
                }
            }
        }

        for (Entry e : allEntries) {
            if (!freshDock.contains(e)) {
                freshGrid.add(e);
            }
        }

        Collections.sort(freshGrid, (a, b) -> {
            boolean ca = a.kind == Entry.CARD, cb = b.kind == Entry.CARD;
            if (ca != cb) return ca ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });


        if (showHomeSettings && pm.resolveActivity(new Intent(Settings.ACTION_HOME_SETTINGS), 0) != null) {
            freshGrid.add(new Entry(Entry.HOME_SETTINGS, ctx.getString(R.string.home_settings),
                    new ComponentName(ctx, HomeSettingsActivity.class), null,
                    ctx.getDrawable(R.drawable.ic_home_settings), null, 0));
        }

        List<String> savedOrder = gridOrder(ctx);
        Collections.sort(freshGrid, (a, b) -> {
            int ai = savedOrder.indexOf(a.component.flattenToShortString());
            int bi = savedOrder.indexOf(b.component.flattenToShortString());
            if (ai < 0) ai = Integer.MAX_VALUE;
            if (bi < 0) bi = Integer.MAX_VALUE;
            return Integer.compare(ai, bi);
        });

        dockItems.clear();
        dockItems.addAll(freshDock);

        gridItems.clear();
        gridItems.addAll(freshGrid);

        if (activeDockRowHolder != null) {
            activeDockRowHolder.bind(dockItems);
        }
        notifyDataSetChanged();
    }

    private static boolean isSmartYouTube(String pkg) {
        return "org.smarttube.stable".equals(pkg) || "org.smarttube.beta".equals(pkg)
                || "com.liskovsoft.smarttubetv".equals(pkg) || "com.liskovsoft.smarttubetv.beta".equals(pkg)
                || "com.teamsmart.videomanager.tv".equals(pkg);
    }

    private Drawable loadBanner(PackageManager pm, ResolveInfo ri) {
        if ("com.android.vending".equals(ri.activityInfo.packageName)) {
            return new AppStoreBanner(ctx);
        }
        int id = ri.activityInfo.banner;
        if (id == 0) id = ri.activityInfo.applicationInfo.banner;
        if (id != 0) {
            try {
                Resources resources = pm.getResourcesForApplication(ri.activityInfo.applicationInfo);
                int density = Math.max(DisplayMetrics.DENSITY_XHIGH, ctx.getResources().getDisplayMetrics().densityDpi);
                return resources.getDrawableForDensity(id, density, null);
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
            }
        }
        return ri.activityInfo.loadBanner(pm);
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_DOCK : VIEW_TYPE_GRID;
    }

    @Override
    public int getItemCount() {
        return 1 + gridItems.size();
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) return 999999L;
        int gridIndex = position - 1;
        if (gridIndex >= 0 && gridIndex < gridItems.size()) {
            return gridItems.get(gridIndex).component.flattenToShortString().hashCode();
        }
        return position;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_DOCK) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dock_row, parent, false);
            return new DockRowHolder(v, ctx, listener, animations, advancedEffects, reducedMotion, columns);
        }
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        Typography.apply(v);
        float density = parent.getResources().getDisplayMetrics().density;
        int width = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
        int inner = width - parent.getPaddingLeft() - parent.getPaddingRight();
        int w = tileWidthPx(inner, columns, density), h = tileHeightPx(w);
        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        cardLp.width = w;
        cardLp.height = h;
        card.setLayoutParams(cardLp);
        View name = v.findViewById(R.id.name);
        ViewGroup.MarginLayoutParams nameLp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
        nameLp.width = (int) (w * 1.15f);
        nameLp.topMargin = Math.round(14 * density);
        name.setLayoutParams(nameLp);
        return new Holder(v, listener, animations, advancedEffects, reducedMotion, false);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder rawHolder, int position) {
        if (rawHolder instanceof DockRowHolder) {
            activeDockRowHolder = (DockRowHolder) rawHolder;
            activeDockRowHolder.bind(dockItems);
            return;
        }
        Holder h = (Holder) rawHolder;
        int gridIndex = position - 1;
        if (gridIndex < 0 || gridIndex >= gridItems.size()) return;
        Entry e = gridItems.get(gridIndex);
        h.card.setVisibility(View.VISIBLE);
        h.card.setFocusable(true);
        h.name.setText(e.name);
        h.name.setVisibility(View.VISIBLE);
        boolean focused = h.card.isFocused();
        h.name.setAlpha(focused ? 1f : 0f);
        h.card.setScaleX(focused && animations ? focusScale() : 1f);
        h.card.setScaleY(focused && animations ? focusScale() : 1f);
        h.card.setTranslationY(0f);
        h.card.setTranslationZ(0f);
        h.card.setElevation(0f);
        h.banner.setScaleX(1f);
        h.banner.setScaleY(1f);
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.label.setVisibility(View.GONE);
        }
        h.card.setOnClickListener(v -> launchApp(ctx, e, advancedEffects));
        h.card.setOnLongClickListener(v -> {
            if (ctx instanceof MainActivity) {
                ((MainActivity) ctx).showAppActionDialog(e);
            }
            return true;
        });
    }

    private List<Entry> originalDock, originalGrid;
    public void beginMove() {
        originalDock = new ArrayList<>(dockItems);
        originalGrid = new ArrayList<>(gridItems);
    }
    public int[] location(String key) {
        for (int i=0;i<dockItems.size();i++) if (dockItems.get(i).component.flattenToShortString().equals(key)) return new int[]{1,i};
        for (int i=0;i<gridItems.size();i++) if (gridItems.get(i).component.flattenToShortString().equals(key)) return new int[]{0,i};
        return null;
    }
    public void move(String key, int direction) {
        int[] at = location(key);
        if (at == null) return;
        boolean dock = at[0] == 1;
        int from=at[1];
        int count=dock ? dockItems.size() : gridItems.size();
        int to=AppMoveTarget.target(dock,from,count,columns,direction);
        if (from != to) {
            Collections.swap(dock ? dockItems : gridItems,from,to);
            if (dock) {
                if (activeDockRowHolder != null) activeDockRowHolder.dockAdapter.swap(from,to);
            } else {
                // Swap the two positions while retaining RecyclerView's stable view identities.
                notifyItemMoved(from+1,to+1);
                notifyItemMoved(to > from ? to : to+2,from+1);
            }
        }
    }
    public void finishMove(boolean save) {
        if (originalDock == null) return;
        if (save) {
            List<String> dock = new ArrayList<>(), order = new ArrayList<>();
            for (Entry entry : dockItems) dock.add(entry.pkg);
            for (Entry entry : gridItems) order.add(entry.component.flattenToShortString());
            Config.setDockApps(ctx,dock);
            ctx.getSharedPreferences(Config.SETTINGS_NAME,Context.MODE_PRIVATE).edit()
                    .putString("grid_app_order",android.text.TextUtils.join("\n",order)).apply();
        } else {
            dockItems.clear(); dockItems.addAll(originalDock);
            gridItems.clear(); gridItems.addAll(originalGrid);
            notifyDataSetChanged();
        }
        originalDock = originalGrid = null;
    }
    private static List<String> gridOrder(Context context) {
        String value = context.getSharedPreferences(Config.SETTINGS_NAME,Context.MODE_PRIVATE).getString("grid_app_order", "");
        return value.isEmpty() ? new ArrayList<>() : new ArrayList<>(java.util.Arrays.asList(value.split("\n")));
    }

    public static void launchApp(Context ctx, Entry e, boolean advancedEffects) {
        if (e.kind == Entry.HOME_SETTINGS) {
            ctx.startActivity(new Intent(ctx, HomeSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
            if (advancedEffects && ctx instanceof Activity) {
                ((Activity) ctx).overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
            }
        } catch (Exception ex) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    public int getDockCount() {
        return dockItems.size();
    }

    public int getGridItemCount() {
        return gridItems.size();
    }

    public String dockKeyAt(int pos) {
        return pos >= 0 && pos < dockItems.size() ? dockItems.get(pos).component.flattenToShortString() : null;
    }

    public String dockPkgAt(int pos) {
        return pos >= 0 && pos < dockItems.size() ? dockItems.get(pos).pkg : null;
    }

    public String dockLabelAt(int pos) {
        return pos >= 0 && pos < dockItems.size() ? dockItems.get(pos).label : "";
    }

    public String gridKeyAt(int pos) {
        return pos >= 0 && pos < gridItems.size() ? gridItems.get(pos).component.flattenToShortString() : null;
    }

    public String gridPkgAt(int pos) {
        return pos >= 0 && pos < gridItems.size() ? gridItems.get(pos).pkg : null;
    }

    public String gridLabelAt(int pos) {
        return pos >= 0 && pos < gridItems.size() ? gridItems.get(pos).label : "";
    }

    public int gridAmbientColorAt(int pos) {
        return pos >= 0 && pos < gridItems.size() ? ambientColorFor(gridItems.get(pos)) : 0xFF444444;
    }

    public String keyAt(int position) {
        if (position == 0) return dockItems.isEmpty() ? null : dockItems.get(0).component.flattenToShortString();
        int idx = position - 1;
        return idx >= 0 && idx < gridItems.size() ? gridItems.get(idx).component.flattenToShortString() : null;
    }

    public String pkgAt(int position) {
        if (position == 0) return dockItems.isEmpty() ? null : dockItems.get(0).pkg;
        int idx = position - 1;
        return idx >= 0 && idx < gridItems.size() ? gridItems.get(idx).pkg : null;
    }

    public String labelAt(int position) {
        if (position == 0) return dockItems.isEmpty() ? "" : dockItems.get(0).label;
        int idx = position - 1;
        return idx >= 0 && idx < gridItems.size() ? gridItems.get(idx).label : "";
    }

    public int ambientColorAt(int position) {
        if (position == 0) return dockItems.isEmpty() ? 0xFF444444 : ambientColorFor(dockItems.get(0));
        int idx = position - 1;
        return idx >= 0 && idx < gridItems.size() ? ambientColorFor(gridItems.get(idx)) : 0xFF444444;
    }

    public int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < dockItems.size(); i++) {
            if (dockItems.get(i).component.flattenToShortString().equals(key)) return 0;
        }
        for (int i = 0; i < gridItems.size(); i++) {
            if (gridItems.get(i).component.flattenToShortString().equals(key)) return 1 + i;
        }
        return -1;
    }

    public int dockPositionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < dockItems.size(); i++) {
            if (dockItems.get(i).component.flattenToShortString().equals(key)) return i;
        }
        return -1;
    }

    public int dockFilled() {
        return dockItems.size();
    }

    public int focusablePosition(int position) {
        return position;
    }

    public static float focusScale() {
        return FOCUS_SCALE;
    }

    public int ambientColorFor(Entry e) {
        if (e == null) return 0xFF444444;
        if (e.ambient == 0) e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    public static float cornerRadius(int tileWidthPx, float density) {
        return 15f * density * Math.min(1f, tileWidthPx / (DESIGN_W_DP * density));
    }

    public static final class DockRowHolder extends RecyclerView.ViewHolder {
        public final RecyclerView dockRecycler;
        public final DockAdapter dockAdapter;
        private final Context ctx;
        private final int columns;

        public DockRowHolder(@NonNull View itemView, Context ctx, TileListener listener,
                             boolean animations, boolean advancedEffects, boolean reducedMotion, int columns) {
            super(itemView);
            this.ctx = ctx;
            this.columns = columns;
            dockRecycler = itemView.findViewById(R.id.dockRecyclerView);
            LinearLayoutManager lm = new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false);
            dockRecycler.setLayoutManager(lm);
            dockRecycler.setHasFixedSize(false);
            dockRecycler.setClipChildren(false);
            dockRecycler.setClipToPadding(false);
            dockAdapter = new DockAdapter(ctx, listener, animations, advancedEffects, reducedMotion, columns);
            dockRecycler.setAdapter(dockAdapter);
            dockRecycler.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                           @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    float density = ctx.getResources().getDisplayMetrics().density;
                    int width = parent.getWidth() > 0 ? parent.getWidth() : ctx.getResources().getDisplayMetrics().widthPixels - (int) (66 * density);
                    int w = tileWidthPx(width, columns, density);
                    int pad = dockEdgePaddingPx(w, density);
                    int gap = Math.max(Math.round(12 * density),
                            (width - 2 * pad - columns * w) / Math.max(1, columns - 1));
                    int pos = parent.getChildAdapterPosition(view);
                    if (pos < state.getItemCount() - 1) {
                        outRect.right = gap;
                    } else {
                        outRect.right = 0;
                    }
                }
            });
        }

        public void bind(List<Entry> dockList) {
            dockAdapter.setItems(dockList);
            updateDockPadding();
        }

        public void updateDockPadding() {
            dockRecycler.post(() -> {
                int count = dockAdapter.getItemCount();
                if (count == 0) return;
                float density = ctx.getResources().getDisplayMetrics().density;
                int width = dockRecycler.getWidth();
                if (width <= 0) {
                    width = ctx.getResources().getDisplayMetrics().widthPixels - (int) (66 * density);
                }
                int w = tileWidthPx(width, columns, density);
                // Reserve the zoom overhang plus a visible glass margin at both ends.
                int pad = dockEdgePaddingPx(w, density);
                if (dockRecycler.getPaddingLeft() != pad || dockRecycler.getPaddingRight() != pad) {
                    dockRecycler.setPadding(pad, 0, pad, 0);
                    dockRecycler.requestLayout();
                }
            });
        }

        public void focusTile(int pos) {
            if (pos < 0 || pos >= dockAdapter.getItemCount()) return;
            dockRecycler.scrollToPosition(pos);
            dockRecycler.post(() -> {
                RecyclerView.ViewHolder vh = dockRecycler.findViewHolderForAdapterPosition(pos);
                if (vh != null) {
                    View card = vh.itemView.findViewById(R.id.card);
                    if (card != null) card.requestFocus();
                }
            });
        }
    }

    public static final class DockAdapter extends RecyclerView.Adapter<Holder> {
        private final Context ctx;
        private final TileListener listener;
        private final boolean animations, advancedEffects, reducedMotion;
        private final int columns;
        private final List<Entry> items = new ArrayList<>();

        public DockAdapter(Context ctx, TileListener listener, boolean animations,
                           boolean advancedEffects, boolean reducedMotion, int columns) {
            this.ctx = ctx;
            this.listener = listener;
            this.animations = animations;
            this.advancedEffects = advancedEffects;
            this.reducedMotion = reducedMotion;
            this.columns = columns;
            setHasStableIds(true);
        }

        void swap(int from, int to) {
            Collections.swap(items,from,to);
            notifyItemMoved(from,to);
            notifyItemMoved(to > from ? to-1 : to+1,from);
        }

        public void setItems(List<Entry> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).component.flattenToShortString().hashCode();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
            Typography.apply(v);
            float density = parent.getResources().getDisplayMetrics().density;
            int width = parent.getWidth() > 0 ? parent.getWidth() : parent.getResources().getDisplayMetrics().widthPixels - (int) (66 * density);
            int inner = width;
            int w = tileWidthPx(inner, columns, density), h = tileHeightPx(w);

            ViewGroup.LayoutParams vLp = v.getLayoutParams();
            if (vLp == null) {
                vLp = new RecyclerView.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
            } else {
                vLp.width = w;
                vLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
            v.setLayoutParams(vLp);

            View card = v.findViewById(R.id.card);
            ViewGroup.LayoutParams cardLp = card.getLayoutParams();
            cardLp.width = w;
            cardLp.height = h;
            card.setLayoutParams(cardLp);
            View name = v.findViewById(R.id.name);
            name.setVisibility(View.GONE);
            return new Holder(v, listener, animations, advancedEffects, reducedMotion, true);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Entry e = items.get(position);
            h.card.setVisibility(View.VISIBLE);
            h.card.setFocusable(true);
            h.name.setVisibility(View.GONE);
            boolean focused = h.card.isFocused();
            h.card.setScaleX(focused && animations ? focusScale() : 1f);
            h.card.setScaleY(focused && animations ? focusScale() : 1f);
            h.card.setTranslationY(0f);
            h.card.setTranslationZ(0f);
            h.card.setElevation(0f);
            h.banner.setScaleX(1f);
            h.banner.setScaleY(1f);
            if (e.banner != null) {
                h.banner.setImageDrawable(e.banner);
                h.banner.setVisibility(View.VISIBLE);
                h.icon.setVisibility(View.GONE);
                h.label.setVisibility(View.GONE);
            } else {
                h.banner.setImageDrawable(null);
                h.banner.setVisibility(View.GONE);
                h.icon.setImageDrawable(e.icon);
                h.icon.setVisibility(View.VISIBLE);
                h.label.setVisibility(View.GONE);
            }
            h.card.setOnClickListener(v -> launchApp(ctx, e, advancedEffects));
            h.card.setOnLongClickListener(v -> {
                if (ctx instanceof MainActivity) {
                    ((MainActivity) ctx).showAppActionDialog(e);
                }
                return true;
            });
        }
    }

    public static final class Holder extends RecyclerView.ViewHolder {
        public final View card;
        public final ImageView banner;
        public final ImageView icon;
        public final TextView label;
        public final TextView name;
        public final ShineView shine;
        public final boolean animations;
        public final boolean advancedEffects;
        public final boolean reducedMotion;
        public final boolean isDock;

        public Holder(View v, TileListener listener, boolean animations, boolean advancedEffects,
                      boolean reducedMotion, boolean isDock) {
            super(v);
            this.isDock = isDock;
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);
            shine = v.findViewById(R.id.shine);
            this.animations = animations;
            this.advancedEffects = advancedEffects;
            this.reducedMotion = reducedMotion;

            final float density = v.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius(view.getWidth(), density));
                }
            });
            card.setClipToOutline(true);

            card.setOnHoverListener((view, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_HOVER_ENTER) view.requestFocus();
                return false;
            });

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                        && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) pos = getAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode, isDock);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                view.animate().cancel();
                if (name != null) name.animate().cancel();
                if (shine != null) {
                    if (focused) {
                        shine.startShine();
                    } else {
                        shine.cancelShine();
                    }
                }

                view.setPivotX(view.getWidth() / 2f);
                view.setPivotY(view.getHeight() / 2f);

                float s = focused ? focusScale() : 1f;
                if (!animations) {
                    view.setScaleX(s);
                    view.setScaleY(s);
                    view.setTranslationY(0f);
                    view.setTranslationZ(0f);
                    if (name != null && !isDock) name.setAlpha(focused ? 1f : 0f);
                } else if (reducedMotion) {
                    view.animate().scaleX(s).scaleY(s)
                            .translationY(0f)
                            .translationZ(0f)
                            .setDuration(focused ? 140 : 100)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    if (name != null && !isDock) name.animate().alpha(focused ? 1f : 0f).setDuration(100).start();
                } else {
                    view.animate().scaleX(s).scaleY(s)
                            .translationY(0f)
                            .translationZ(0f)
                            .setDuration(focused ? 200 : 160)
                            .setInterpolator(new DecelerateInterpolator(1.4f))
                            .start();
                    if (name != null && !isDock) name.animate().alpha(focused ? 1f : 0f)
                            .setDuration(focused ? 180 : 120).start();
                }
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) pos = getAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view, isDock);
                }
            });
        }
    }
}
