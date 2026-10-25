package com.leotv.launcher;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Apple TV tvOS 18 Dock Apps Manager dialog.
 * Allows organizing and toggling apps on the top dock row with strict slot limit.
 */
public class DockManagerDialog extends Dialog {

    private final Runnable onChanged;
    private LinearLayout listContainer;
    private TextView summaryText;

    public DockManagerDialog(Context context, Runnable onChanged) {
        super(context);
        this.onChanged = onChanged;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_dock_manager);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.85f);
            window.setGravity(Gravity.CENTER);
        }

        listContainer = findViewById(R.id.dockAppsListContainer);
        summaryText = findViewById(R.id.dockSummaryText);
        TextView btnReset = findViewById(R.id.btnResetDock);

        populateApps(null);

        btnReset.setOnClickListener(v -> {
            Context ctx = getContext();
            ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                    .edit().remove("custom_dock_apps").apply();
            if (onChanged != null) onChanged.run();
            Toast.makeText(ctx, ctx.getString(R.string.leo_dock_restored), Toast.LENGTH_SHORT).show();
            populateApps(null);
        });

        setOnDismissListener(d -> {
            if (onChanged != null) onChanged.run();
        });
    }

    private void populateApps(String targetFocusPkg) {
        listContainer.removeAllViews();
        Context ctx = getContext();
        PackageManager pm = ctx.getPackageManager();

        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(probe, 0);

        List<String> dockList = new ArrayList<>(Config.getDockApps(ctx));
        int maxDock = Config.load(ctx).columns;
        if (dockList.size()>maxDock) dockList.subList(maxDock,dockList.size()).clear();

        // Separate installed activities into Docked and Available
        List<ResolveInfo> dockedInfos = new ArrayList<>();
        List<ResolveInfo> otherInfos = new ArrayList<>();

        for (ResolveInfo ri : activities) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName())) continue;

            if (dockList.contains(pkg)) {
                dockedInfos.add(ri);
            } else {
                otherInfos.add(ri);
            }
        }

        // Sort dockedInfos strictly matching the dockList order
        Collections.sort(dockedInfos, (a, b) -> {
            int ia = dockList.indexOf(a.activityInfo.packageName);
            int ib = dockList.indexOf(b.activityInfo.packageName);
            return Integer.compare(ia, ib);
        });

        // Sort others alphabetically
        Collections.sort(otherInfos, (a, b) ->
                String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));

        int iconSize = (int) (24 * ctx.getResources().getDisplayMetrics().density);
        View viewToFocus = null;

        // SECTION 1: DOCKED APPS
        addSectionHeader(ctx.getString(R.string.leo_dock_section_prefix) + dockedInfos.size() + "/" + maxDock + ")");
        if (dockedInfos.isEmpty()) {
            addEmptyHint(ctx.getString(R.string.leo_dock_empty));
        } else {
            for (int i = 0; i < dockedInfos.size(); i++) {
                final int index = i;
                ResolveInfo ri = dockedInfos.get(i);
                String pkg = ri.activityInfo.packageName;
                String label = getDisplayLabel(ri, pm);

                LinearLayout itemRow = new LinearLayout(ctx);
                itemRow.setOrientation(LinearLayout.HORIZONTAL);
                itemRow.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) (40 * ctx.getResources().getDisplayMetrics().density)
                );
                rowLp.bottomMargin = (int) (6 * ctx.getResources().getDisplayMetrics().density);
                itemRow.setLayoutParams(rowLp);

                TextView labelView = createRowView(ctx);
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                labelLp.setMarginEnd((int) (6 * ctx.getResources().getDisplayMetrics().density));
                labelView.setLayoutParams(labelLp);
                labelView.setText(ctx.getString(R.string.leo_position_prefix) + (i + 1) + ":  " + label);
                labelView.setSelected(true);

                Drawable icon = getAppIcon(ctx, ri, pm, iconSize);
                if (icon != null) {
                    labelView.setCompoundDrawables(icon, null, null, null);
                    labelView.setCompoundDrawablePadding((int) (10 * ctx.getResources().getDisplayMetrics().density));
                }

                // Action buttons: Left (◀), Right (▶), Remove (✕)
                TextView btnLeft = createActionButton(ctx, "◀", index > 0);
                if (index > 0) {
                    btnLeft.setOnClickListener(v -> {
                        Config.moveDockApp(ctx, pkg, -1);
                        if (onChanged != null) onChanged.run();
                        populateApps(pkg);
                    });
                }

                TextView btnRight = createActionButton(ctx, "▶", index < dockedInfos.size() - 1);
                if (index < dockedInfos.size() - 1) {
                    btnRight.setOnClickListener(v -> {
                        Config.moveDockApp(ctx, pkg, 1);
                        if (onChanged != null) onChanged.run();
                        populateApps(pkg);
                    });
                }

                TextView btnDel = createActionButton(ctx, "✕", true);
                btnDel.setOnClickListener(v -> {
                    Config.removeDockApp(ctx, pkg);
                    if (onChanged != null) onChanged.run();
                    Toast.makeText(ctx, ctx.getString(R.string.leo_removed_prefix) + label + ctx.getString(R.string.leo_removed_suffix), Toast.LENGTH_SHORT).show();
                    populateApps(null);
                });

                itemRow.addView(labelView);
                itemRow.addView(btnLeft);
                itemRow.addView(btnRight);
                itemRow.addView(btnDel);
                listContainer.addView(itemRow);

                if (pkg.equals(targetFocusPkg)) viewToFocus = labelView;
                else if (viewToFocus == null && targetFocusPkg == null) viewToFocus = labelView;
            }
        }

        // SECTION 2: OTHER AVAILABLE APPS
        addSectionHeader(ctx.getString(R.string.leo_other_apps_section));
        for (ResolveInfo ri : otherInfos) {
            String pkg = ri.activityInfo.packageName;
            String label = getDisplayLabel(ri, pm);

            TextView row = createRowView(ctx);
            Drawable icon = getAppIcon(ctx, ri, pm, iconSize);
            if (icon != null) {
                icon.setBounds(0, 0, iconSize, iconSize);
                row.setCompoundDrawables(icon, null, null, null);
                row.setCompoundDrawablePadding((int) (12 * ctx.getResources().getDisplayMetrics().density));
            }

            row.setText("+  " + label);
            row.setSelected(false);

            row.setOnClickListener(v -> {
                List<String> currentDock = Config.getDockApps(ctx);
                if (currentDock.size() >= maxDock) {
                    Toast.makeText(ctx, ctx.getString(R.string.leo_dock_full_prefix) + maxDock + ctx.getString(R.string.leo_dock_full_suffix), Toast.LENGTH_LONG).show();
                    return;
                }
                Config.addDockApp(ctx, pkg);
                if (onChanged != null) onChanged.run();
                Toast.makeText(ctx, ctx.getString(R.string.leo_added_prefix) + label + ctx.getString(R.string.leo_added_suffix), Toast.LENGTH_SHORT).show();
                populateApps(pkg);
            });

            listContainer.addView(row);
            if (pkg.equals(targetFocusPkg)) viewToFocus = row;
            else if (viewToFocus == null && targetFocusPkg == null) viewToFocus = row;
        }

        summaryText.setText(ctx.getString(R.string.leo_dock_summary_prefix) + maxDock + ctx.getString(R.string.leo_dock_summary_suffix) + dockedInfos.size() + "/" + maxDock);

        if (viewToFocus != null) {
            final View fv = viewToFocus;
            fv.post(fv::requestFocus);
        }
    }

    private TextView createRowView(Context ctx) {
        TextView row = new TextView(ctx, null, 0, R.style.SettingsRow);
        row.setBackgroundResource(R.drawable.settings_choice_bg);
        row.setTextColor(ctx.getResources().getColorStateList(R.color.control_label));
        row.setFocusable(true);
        row.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (38 * ctx.getResources().getDisplayMetrics().density)
        );
        lp.bottomMargin = (int) (5 * ctx.getResources().getDisplayMetrics().density);
        row.setLayoutParams(lp);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                (int) (14 * ctx.getResources().getDisplayMetrics().density), 0,
                (int) (14 * ctx.getResources().getDisplayMetrics().density), 0
        );
        return row;
    }

    private TextView createActionButton(Context ctx, String text, boolean enabled) {
        TextView btn = new TextView(ctx, null, 0, R.style.SettingsRow);
        btn.setBackgroundResource(R.drawable.settings_choice_bg);
        btn.setTextColor(ctx.getResources().getColorStateList(R.color.control_label));
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, 0, 0, 0);
        btn.setText(text);
        btn.setTextSize(13);
        int size = (int) (38 * ctx.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginEnd((int) (4 * ctx.getResources().getDisplayMetrics().density));
        btn.setLayoutParams(lp);
        btn.setFocusable(enabled);
        btn.setClickable(enabled);
        if (!enabled) {
            btn.setAlpha(0.25f);
        }
        return btn;
    }

    private void addSectionHeader(String title) {
        Context ctx = getContext();
        TextView header = new TextView(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = (int) (14 * ctx.getResources().getDisplayMetrics().density);
        lp.bottomMargin = (int) (6 * ctx.getResources().getDisplayMetrics().density);
        header.setLayoutParams(lp);
        header.setText(title);
        header.setTextColor(Color.parseColor("#80FFFFFF"));
        header.setTextSize(12);
        header.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.setLetterSpacing(0.04f);
        header.setPadding((int) (4 * ctx.getResources().getDisplayMetrics().density), 0, 0, 0);
        listContainer.addView(header);
    }

    private void addEmptyHint(String hint) {
        Context ctx = getContext();
        TextView tv = new TextView(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = (int) (8 * ctx.getResources().getDisplayMetrics().density);
        tv.setLayoutParams(lp);
        tv.setText(hint);
        tv.setTextColor(Color.parseColor("#55FFFFFF"));
        tv.setTextSize(13);
        tv.setPadding((int) (4 * ctx.getResources().getDisplayMetrics().density), 0, 0, 0);
        listContainer.addView(tv);
    }

    private String getDisplayLabel(ResolveInfo ri, PackageManager pm) {
        String pkg = ri.activityInfo.packageName;
        if ("org.smarttube.stable".equals(pkg)) {
            return "YouTube";
        } else if ("com.android.vending".equals(pkg)) {
            return "App Store";
        }
        return Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg);
    }

    private Drawable getAppIcon(Context ctx, ResolveInfo ri, PackageManager pm, int iconSize) {
        String pkg = ri.activityInfo.packageName;
        Drawable icon = "com.android.vending".equals(pkg) ?
                ctx.getDrawable(R.drawable.ic_app_store) : ri.loadIcon(pm);
        if (icon != null) {
            icon.setBounds(0, 0, iconSize, iconSize);
        }
        return icon;
    }
}
