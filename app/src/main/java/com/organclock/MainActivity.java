package com.organclock;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Single-activity app with a custom bottom navigation bar (no external UI
 * dependencies). Four pages:
 *   0 Now      – every organ listed, scrolled to the active one
 *   1 Elements – the five-element generating/controlling diagram, drawn in code
 *   2 Alerts   – per-organ notification toggles
 *   3 Settings – theme + language
 *
 * Localized via attachBaseContext; light/dark theme chosen in Settings and
 * applied to the window plus all custom-drawn colors via a small palette.
 */
public class MainActivity extends Activity {

    static final String EXTRA_PAGE = "page";

    // Element accent colors in canonical order: Wood, Fire, Earth, Metal, Water.
    static final int[] ELEMENT5 = {0xFF4CAF50, 0xFFE53935, 0xFFC8A24B, 0xFF90A4AE, 0xFF1E88E5};

    // Element index (0=Wood,1=Fire,2=Earth,3=Metal,4=Water) per organ slot.
    static final int[] SLOT_ELEMENT = {0, 0, 3, 3, 2, 2, 1, 1, 4, 4, 1, 1};
    // Controlling cycle: Wood→Earth→Water→Fire→Metal→Wood.
    static final int[] CONTROLS = {2, 3, 4, 0, 1};      // element -> element it controls
    static final int[] CONTROLLED_BY = {3, 4, 0, 1, 2}; // element -> element controlling it

    // Schematic meridian routes per slot: normalized (x,y) points on a front-facing
    // figure, traced on the correct aspect of the limb. Approximate, not atlas-grade.
    static final float[][] MERIDIAN_PATH = {
            {0.44f, 0.11f, 0.40f, 0.14f, 0.40f, 0.35f, 0.40f, 0.50f, 0.39f, 0.72f, 0.385f, 0.90f, 0.40f, 0.96f}, // Gallbladder
            {0.45f, 0.965f, 0.455f, 0.90f, 0.465f, 0.72f, 0.475f, 0.54f, 0.49f, 0.49f, 0.47f, 0.40f},           // Liver
            {0.43f, 0.30f, 0.37f, 0.20f, 0.31f, 0.35f, 0.29f, 0.52f, 0.278f, 0.56f},                            // Lung
            {0.285f, 0.56f, 0.30f, 0.52f, 0.32f, 0.35f, 0.37f, 0.19f, 0.46f, 0.13f, 0.49f, 0.10f},              // Large Intestine
            {0.47f, 0.12f, 0.46f, 0.30f, 0.45f, 0.48f, 0.44f, 0.55f, 0.435f, 0.72f, 0.43f, 0.88f, 0.425f, 0.96f}, // Stomach
            {0.455f, 0.96f, 0.45f, 0.90f, 0.46f, 0.72f, 0.47f, 0.54f, 0.47f, 0.45f, 0.46f, 0.33f},              // Spleen
            {0.45f, 0.27f, 0.38f, 0.30f, 0.35f, 0.40f, 0.33f, 0.52f, 0.32f, 0.56f},                             // Heart
            {0.32f, 0.56f, 0.345f, 0.52f, 0.36f, 0.35f, 0.40f, 0.20f, 0.44f, 0.24f, 0.47f, 0.13f},              // Small Intestine
            {0.485f, 0.11f, 0.50f, 0.05f, 0.50f, 0.16f, 0.47f, 0.30f, 0.46f, 0.45f, 0.45f, 0.52f, 0.43f, 0.72f, 0.42f, 0.88f, 0.40f, 0.96f}, // Bladder
            {0.44f, 0.975f, 0.46f, 0.90f, 0.47f, 0.72f, 0.48f, 0.54f, 0.485f, 0.45f, 0.47f, 0.30f},             // Kidney
            {0.45f, 0.30f, 0.36f, 0.22f, 0.325f, 0.35f, 0.305f, 0.52f, 0.30f, 0.575f},                          // Pericardium
            {0.305f, 0.565f, 0.325f, 0.52f, 0.345f, 0.35f, 0.385f, 0.20f, 0.44f, 0.12f, 0.46f, 0.10f},          // Triple Burner
    };
    // Channels running on the back of the body/limb are drawn dashed.
    static final boolean[] MERIDIAN_DASHED = {
            false, false, false, true, false, false, false, true, true, false, false, true};

    private SharedPreferences sp;
    private float density;
    private String builtLang;
    private int page;
    private boolean showingMeridian;

    private FrameLayout content;
    private LinearLayout[] tabViews;

    // Theme palette (filled by computePalette()).
    private boolean dark;
    private int colDim, colTag, colOverlay, colBar, colAccent, colIdle, colGen, colCtrl;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(OrganClockWidget.localized(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        sp = OrganClockWidget.prefs(this);
        applyTheme(); // sets window theme + palette before inflating anything
        super.onCreate(state);

        density = getResources().getDisplayMetrics().density;
        builtLang = sp.getString(OrganClockWidget.KEY_LANG, "");
        setTitle(getString(R.string.app_name));

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);

        content = new FrameLayout(this);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        outer.addView(content);
        outer.addView(buildBottomBar());

        setContentView(outer);

        page = getIntent().getIntExtra(EXTRA_PAGE, 0);
        showPage(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!sp.getString(OrganClockWidget.KEY_LANG, "").equals(builtLang)) {
            getIntent().putExtra(EXTRA_PAGE, page);
            recreate();
        }
    }

    // ---- Theme -------------------------------------------------------------

    private void applyTheme() {
        String t = sp.getString(OrganClockWidget.KEY_THEME, "system");
        if ("dark".equals(t)) {
            dark = true;
        } else if ("light".equals(t)) {
            dark = false;
        } else {
            int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            dark = mode == Configuration.UI_MODE_NIGHT_YES;
        }
        setTheme(dark ? android.R.style.Theme_DeviceDefault
                : android.R.style.Theme_DeviceDefault_Light);
        computePalette();
    }

    private void computePalette() {
        if (dark) {
            colDim = 0xFFB0B0B0;
            colTag = 0xCCFFFFFF;
            colOverlay = 0x22FFFFFF;
            colBar = 0xFF1E1E1E;
            colAccent = 0xFF9FA8DA;
            colIdle = 0xFF8A8A8A;
            colGen = 0xFFB0BEC5;
            colCtrl = 0xFFEF5350;
        } else {
            colDim = 0xFF666666;
            colTag = 0xB4000000;
            colOverlay = 0x1C000000;
            colBar = 0xFFF5F5F5;
            colAccent = 0xFF3F51B5;
            colIdle = 0xFF9E9E9E;
            colGen = 0xFF455A64;
            colCtrl = 0xFFC62828;
        }
    }

    // ---- Bottom navigation -------------------------------------------------

    private LinearLayout buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(colBar);
        int v = dp(6);
        bar.setPadding(0, v, 0, v);

        int[] icons = {R.drawable.ic_now, R.drawable.ic_elements,
                R.drawable.ic_alerts, R.drawable.ic_settings};
        String[] labels = {
                getString(R.string.now),
                getString(R.string.screen_elements),
                getString(R.string.screen_alerts),
                getString(R.string.menu_settings),
        };
        tabViews = new LinearLayout[icons.length];
        for (int i = 0; i < icons.length; i++) {
            final int idx = i;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            tab.setClickable(true);

            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
            tab.addView(icon);

            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            label.setGravity(Gravity.CENTER);
            tab.addView(label);

            tab.setOnClickListener(view -> showPage(idx));
            tabViews[i] = tab;
            bar.addView(tab);
        }
        return bar;
    }

    private void showPage(int p) {
        page = p;
        showingMeridian = false;
        for (int i = 0; i < tabViews.length; i++) {
            int tint = (i == p) ? colAccent : colIdle;
            ((ImageView) tabViews[i].getChildAt(0)).setColorFilter(tint);
            ((TextView) tabViews[i].getChildAt(1)).setTextColor(tint);
        }
        content.removeAllViews();
        View view;
        if (p == 0) {
            view = buildNowPage();
        } else if (p == 1) {
            view = buildElementsPage();
        } else if (p == 2) {
            view = buildAlertsPage();
        } else {
            view = buildSettingsPage();
        }
        content.addView(view);
    }

    // ---- Page: Now ---------------------------------------------------------

    private View buildNowPage() {
        Resources res = getResources();
        String[] windows = res.getStringArray(R.array.windows);
        String[] organs = res.getStringArray(R.array.organs);
        String[] elements = res.getStringArray(R.array.elements);
        String[] emotions = res.getStringArray(R.array.emotions);
        String[] herbs = res.getStringArray(R.array.herbs);
        String nowLabel = res.getString(R.string.now);
        String controlsLabel = res.getString(R.string.rel_controls);
        String controlledByLabel = res.getString(R.string.rel_controlled_by);
        int active = OrganClockWidget.currentSlot();

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        list.setPadding(pad, pad, pad, pad);

        View activeBlock = null;
        for (int i = 0; i < organs.length; i++) {
            boolean isNow = (i == active);
            int e = SLOT_ELEMENT[i];
            String relations =
                    controlsLabel + ": " + organsForElement(CONTROLS[e], organs) + "\n"
                    + controlledByLabel + ": " + organsForElement(CONTROLLED_BY[e], organs);
            final int slot = i;
            View block = organBlock(
                    isNow ? nowLabel : null,
                    organs[i],
                    elements[i] + "  ·  " + emotions[i] + "  ·  " + windows[i],
                    herbs[i],
                    relations,
                    OrganClockWidget.ELEMENT_COLOR[i]);
            block.setOnClickListener(v -> showMeridian(slot));
            list.addView(block);
            if (isNow) {
                activeBlock = block;
            }
        }

        final ScrollView scroll = new ScrollView(this);
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final View target = activeBlock;
        if (target != null) {
            scroll.post(() -> scroll.scrollTo(0, Math.max(0, target.getTop() - dp(12))));
        }
        return scroll;
    }

    private String organsForElement(int elem, String[] organs) {
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < SLOT_ELEMENT.length; s++) {
            if (SLOT_ELEMENT[s] == elem) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(organs[s]);
            }
        }
        return sb.toString();
    }

    private View organBlock(String nowTag, String organ, String meta, String herbs,
                            String relations, int color) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14);
        block.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        block.setLayoutParams(lp);

        if (nowTag != null) {
            block.setBackgroundColor(colOverlay);
            TextView tag = new TextView(this);
            tag.setText(nowTag.toUpperCase());
            tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tag.setLetterSpacing(0.15f);
            tag.setTextColor(colTag);
            block.addView(tag);
        }

        TextView name = new TextView(this);
        name.setText(organ);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);
        name.setCompoundDrawablesWithIntrinsicBounds(dot(color, dp(16)), null, null, null);
        name.setCompoundDrawablePadding(dp(10));
        block.addView(name);

        TextView metaView = new TextView(this);
        metaView.setText(meta);
        metaView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        metaView.setTextColor(colDim);
        metaView.setPadding(0, dp(2), 0, dp(4));
        block.addView(metaView);

        TextView herbsView = new TextView(this);
        herbsView.setText(herbs);
        herbsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        block.addView(herbsView);

        if (relations != null) {
            TextView rel = new TextView(this);
            rel.setText(relations);
            rel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            rel.setTextColor(colDim);
            rel.setLineSpacing(0, 1.1f);
            rel.setPadding(0, dp(6), 0, 0);
            block.addView(rel);
        }
        return block;
    }

    // ---- Meridian detail (opened by tapping an organ) ----------------------

    private void showMeridian(int slot) {
        showingMeridian = true;
        Resources res = getResources();
        String[] organs = res.getStringArray(R.array.organs);
        String[] elements = res.getStringArray(R.array.elements);
        String[] routes = res.getStringArray(R.array.meridian_route);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        col.setPadding(pad, pad, pad, pad);

        TextView back = new TextView(this);
        back.setText("‹ " + res.getString(R.string.now));
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        back.setTextColor(colAccent);
        back.setPadding(0, 0, 0, dp(10));
        back.setOnClickListener(v -> showPage(0));
        col.addView(back);

        TextView name = new TextView(this);
        name.setText(organs[slot]);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);
        name.setCompoundDrawablesWithIntrinsicBounds(
                dot(OrganClockWidget.ELEMENT_COLOR[slot], dp(16)), null, null, null);
        name.setCompoundDrawablePadding(dp(10));
        col.addView(name);

        TextView caption = new TextView(this);
        caption.setText(elements[slot] + "  ·  " + routes[slot]);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        caption.setTextColor(colDim);
        caption.setPadding(0, dp(2), 0, dp(10));
        col.addView(caption);

        int limbColor = dark ? 0x33FFFFFF : 0x1F000000;
        MeridianView mv = new MeridianView(this, MERIDIAN_PATH[slot],
                OrganClockWidget.ELEMENT_COLOR[slot], MERIDIAN_DASHED[slot], limbColor);
        mv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(mv);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.removeAllViews();
        content.addView(scroll);
    }

    @Override
    public void onBackPressed() {
        if (showingMeridian) {
            showPage(0);
        } else {
            super.onBackPressed();
        }
    }

    // ---- Page: Elements ----------------------------------------------------

    private View buildElementsPage() {
        Resources res = getResources();
        String[] names = res.getStringArray(R.array.element_names);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        col.setPadding(pad, pad, pad, pad);

        TextView intro = new TextView(this);
        intro.setText(res.getString(R.string.elements_intro));
        intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        intro.setPadding(0, 0, 0, dp(8));
        col.addView(intro);

        FiveElementView diagram = new FiveElementView(this, names, ELEMENT5, colGen, colCtrl);
        diagram.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(diagram);

        col.addView(legendRow(colGen, res.getString(R.string.cycle_generating)));
        col.addView(legendRow(colCtrl, res.getString(R.string.cycle_controlling)));

        col.addView(cycleLine(names[0] + " → " + names[1] + " → " + names[2] + " → "
                + names[3] + " → " + names[4] + " → " + names[0], colGen));
        col.addView(cycleLine(names[0] + " → " + names[2] + " → " + names[4] + " → "
                + names[1] + " → " + names[3] + " → " + names[0], colCtrl));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return scroll;
    }

    private View legendRow(int color, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, 0);
        ImageView swatch = new ImageView(this);
        swatch.setImageDrawable(dot(color, dp(14)));
        swatch.setLayoutParams(new LinearLayout.LayoutParams(dp(14), dp(14)));
        row.addView(swatch);
        TextView t = new TextView(this);
        t.setText("   " + text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        row.addView(t);
        return row;
    }

    private View cycleLine(String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(color);
        t.setPadding(0, dp(8), 0, 0);
        return t;
    }

    // ---- Page: Alerts ------------------------------------------------------

    private View buildAlertsPage() {
        Resources res = getResources();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        root.addView(header(res.getString(R.string.settings_notify)));
        String[] organs = res.getStringArray(R.array.organs);
        String[] windows = res.getStringArray(R.array.windows);
        for (int i = 0; i < organs.length; i++) {
            final int slot = i;
            CheckBox cb = new CheckBox(this);
            cb.setText(organs[i] + "   ·   " + windows[i]);
            cb.setChecked(sp.getBoolean(OrganClockWidget.KEY_NOTIFY + slot, false));
            cb.setPadding(0, dp(6), 0, dp(6));
            cb.setOnCheckedChangeListener((view, checked) -> {
                sp.edit().putBoolean(OrganClockWidget.KEY_NOTIFY + slot, checked).apply();
                if (checked) {
                    ensureNotificationPermission();
                }
                applyChanges();
            });
            root.addView(cb);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return scroll;
    }

    // ---- Page: Settings ----------------------------------------------------

    private View buildSettingsPage() {
        Resources res = getResources();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        addRadioSection(root,
                res.getString(R.string.settings_theme),
                new String[]{"system", "light", "dark"},
                new String[]{
                        res.getString(R.string.theme_system),
                        res.getString(R.string.theme_light),
                        res.getString(R.string.theme_dark)},
                OrganClockWidget.KEY_THEME, false);

        addRadioSection(root,
                res.getString(R.string.settings_language),
                new String[]{"", "en", "cs"},
                new String[]{
                        res.getString(R.string.lang_system),
                        res.getString(R.string.lang_en),
                        res.getString(R.string.lang_cs)},
                OrganClockWidget.KEY_LANG, true);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return scroll;
    }

    /** A header + radio group bound to a preference key; rebuilds on change. */
    private void addRadioSection(LinearLayout root, String title, final String[] codes,
                                 String[] labels, final String prefKey, final boolean refreshWidget) {
        root.addView(header(title));
        String current = sp.getString(prefKey, codes[0]);
        RadioGroup group = new RadioGroup(this);
        for (int i = 0; i < codes.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(labels[i]);
            rb.setId(View.generateViewId());
            rb.setTag(codes[i]);
            group.addView(rb);
            if (codes[i].equals(current)) {
                group.check(rb.getId());
            }
        }
        group.setOnCheckedChangeListener((g, checkedId) -> {
            RadioButton rb = g.findViewById(checkedId);
            if (rb == null) {
                return;
            }
            sp.edit().putString(prefKey, (String) rb.getTag()).apply();
            if (refreshWidget) {
                applyChanges();
            }
            getIntent().putExtra(EXTRA_PAGE, page);
            recreate();
        });
        root.addView(group);
    }

    private void applyChanges() {
        sp.edit().putInt(OrganClockWidget.KEY_LAST_SLOT, OrganClockWidget.currentSlot()).apply();
        OrganClockWidget.updateAll(this);
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    // ---- Shared helpers ----------------------------------------------------

    private TextView header(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setPadding(0, dp(18), 0, dp(8));
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        return tv;
    }

    private GradientDrawable dot(int color, int sizePx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setSize(sizePx, sizePx);
        d.setBounds(0, 0, sizePx, sizePx);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    // ---- Five-element diagram ---------------------------------------------

    /** Draws the five elements in a pentagon with generating (perimeter) and
     *  controlling (star) arrows. Nodes are in order Wood, Fire, Earth, Metal,
     *  Water clockwise from the top, so edges = generating and chords = control. */
    static class FiveElementView extends View {
        private final String[] names;
        private final int[] colors;
        private final Paint node = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ctrl = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ctrlHead = new Paint(Paint.ANTI_ALIAS_FLAG);

        FiveElementView(Context c, String[] names, int[] colors, int genColor, int ctrlColor) {
            super(c);
            this.names = names;
            this.colors = colors;
            node.setStyle(Paint.Style.FILL);
            label.setColor(Color.WHITE);
            label.setTextAlign(Paint.Align.CENTER);
            label.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            gen.setColor(genColor);
            gen.setStyle(Paint.Style.STROKE);
            ctrl.setColor(ctrlColor);
            ctrl.setStyle(Paint.Style.STROKE);
            ctrl.setPathEffect(new DashPathEffect(new float[]{14, 10}, 0));
            ctrlHead.setColor(ctrlColor);
            ctrlHead.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            setMeasuredDimension(w, w); // square
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float cx = w / 2f, cy = w / 2f;
            float ring = w * 0.34f;
            float r = w * 0.11f;
            float stroke = Math.max(3f, w * 0.010f);
            gen.setStrokeWidth(stroke);
            ctrl.setStrokeWidth(stroke);
            ctrlHead.setStrokeWidth(stroke);
            label.setTextSize(w * 0.045f);

            float[] x = new float[5], y = new float[5];
            for (int i = 0; i < 5; i++) {
                double a = Math.toRadians(-90 + 72 * i);
                x[i] = cx + ring * (float) Math.cos(a);
                y[i] = cy + ring * (float) Math.sin(a);
            }

            // Controlling cycle (star chords) underneath.
            for (int i = 0; i < 5; i++) {
                arrow(c, x[i], y[i], x[(i + 2) % 5], y[(i + 2) % 5], r, ctrl, ctrlHead, stroke);
            }
            // Generating cycle (perimeter) on top.
            for (int i = 0; i < 5; i++) {
                arrow(c, x[i], y[i], x[(i + 1) % 5], y[(i + 1) % 5], r, gen, gen, stroke);
            }
            // Nodes + labels.
            for (int i = 0; i < 5; i++) {
                node.setColor(colors[i]);
                c.drawCircle(x[i], y[i], r, node);
                float ty = y[i] - (label.descent() + label.ascent()) / 2f;
                c.drawText(names[i], x[i], ty, label);
            }
        }

        private void arrow(Canvas c, float x1, float y1, float x2, float y2,
                           float nodeR, Paint line, Paint head, float stroke) {
            double a = Math.atan2(y2 - y1, x2 - x1);
            float gap = nodeR + stroke * 1.5f;
            float sx = x1 + (float) Math.cos(a) * gap;
            float sy = y1 + (float) Math.sin(a) * gap;
            float ex = x2 - (float) Math.cos(a) * gap;
            float ey = y2 - (float) Math.sin(a) * gap;
            c.drawLine(sx, sy, ex, ey, line);
            float h = nodeR * 0.55f;
            double a1 = a - Math.toRadians(22);
            double a2 = a + Math.toRadians(22);
            c.drawLine(ex, ey, ex - (float) Math.cos(a1) * h, ey - (float) Math.sin(a1) * h, head);
            c.drawLine(ex, ey, ex - (float) Math.cos(a2) * h, ey - (float) Math.sin(a2) * h, head);
        }
    }
}
