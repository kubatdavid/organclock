package com.organclock;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
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
 * dependencies). Three pages:
 *   0 Now      – every organ listed, scrolled to the active one
 *   1 Elements – the five-element generating/controlling diagram, drawn in code
 *   2 Settings – language override + per-organ notifications
 *
 * The whole activity is localized via attachBaseContext.
 */
public class MainActivity extends Activity {

    static final String EXTRA_PAGE = "page";

    // Element accent colors in canonical order: Wood, Fire, Earth, Metal, Water.
    static final int[] ELEMENT5 = {0xFF4CAF50, 0xFFE53935, 0xFFC8A24B, 0xFF90A4AE, 0xFF1E88E5};

    private static final int ACCENT = 0xFF3F51B5;
    private static final int TAB_IDLE = 0xFF9E9E9E;

    private SharedPreferences sp;
    private float density;
    private String builtLang;
    private int page;

    private FrameLayout content;
    private LinearLayout[] tabViews;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(OrganClockWidget.localized(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        sp = OrganClockWidget.prefs(this);
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

    // ---- Bottom navigation -------------------------------------------------

    private LinearLayout buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xFFF5F5F5);
        int v = dp(6);
        bar.setPadding(0, v, 0, v);

        int[] icons = {R.drawable.ic_now, R.drawable.ic_elements, R.drawable.ic_settings};
        String[] labels = {
                getString(R.string.now),
                getString(R.string.screen_elements),
                getString(R.string.menu_settings),
        };
        tabViews = new LinearLayout[3];
        for (int i = 0; i < 3; i++) {
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
        for (int i = 0; i < tabViews.length; i++) {
            int tint = (i == p) ? ACCENT : TAB_IDLE;
            ((ImageView) tabViews[i].getChildAt(0)).setColorFilter(tint);
            ((TextView) tabViews[i].getChildAt(1)).setTextColor(tint);
        }
        content.removeAllViews();
        View view = (p == 0) ? buildNowPage() : (p == 1) ? buildElementsPage() : buildSettingsPage();
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
        int active = OrganClockWidget.currentSlot();

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        list.setPadding(pad, pad, pad, pad);

        View activeBlock = null;
        for (int i = 0; i < organs.length; i++) {
            boolean isNow = (i == active);
            View block = organBlock(
                    isNow ? nowLabel : null,
                    organs[i],
                    elements[i] + "  ·  " + emotions[i] + "  ·  " + windows[i],
                    herbs[i],
                    OrganClockWidget.ELEMENT_COLOR[i]);
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

    private View organBlock(String nowTag, String organ, String meta, String herbs, int color) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14);
        block.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        block.setLayoutParams(lp);

        if (nowTag != null) {
            block.setBackgroundColor(Color.argb(28, 0, 0, 0));
            TextView tag = new TextView(this);
            tag.setText(nowTag.toUpperCase());
            tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tag.setLetterSpacing(0.15f);
            tag.setTextColor(Color.argb(180, 0, 0, 0));
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
        metaView.setTextColor(Color.argb(150, 0, 0, 0));
        metaView.setPadding(0, dp(2), 0, dp(4));
        block.addView(metaView);

        TextView herbsView = new TextView(this);
        herbsView.setText(herbs);
        herbsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        block.addView(herbsView);
        return block;
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

        FiveElementView diagram = new FiveElementView(this, names, ELEMENT5);
        diagram.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(diagram);

        // Legend
        col.addView(legendRow(0xFF455A64, res.getString(R.string.cycle_generating)));
        col.addView(legendRow(0xFFC62828, res.getString(R.string.cycle_controlling)));

        // Cycles written out, language-aware.
        col.addView(cycleLine(names[0] + " → " + names[1] + " → " + names[2] + " → "
                + names[3] + " → " + names[4] + " → " + names[0], 0xFF455A64));
        col.addView(cycleLine(names[0] + " → " + names[2] + " → " + names[4] + " → "
                + names[1] + " → " + names[3] + " → " + names[0], 0xFFC62828));

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

    // ---- Page: Settings ----------------------------------------------------

    private View buildSettingsPage() {
        Resources res = getResources();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        root.addView(header(res.getString(R.string.settings_language)));
        final String[] codes = {"", "en", "cs"};
        String[] langLabels = {
                res.getString(R.string.lang_system),
                res.getString(R.string.lang_en),
                res.getString(R.string.lang_cs),
        };
        String current = sp.getString(OrganClockWidget.KEY_LANG, "");
        final RadioGroup group = new RadioGroup(this);
        for (int i = 0; i < codes.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(langLabels[i]);
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
            sp.edit().putString(OrganClockWidget.KEY_LANG, (String) rb.getTag()).apply();
            applyChanges();
            getIntent().putExtra(EXTRA_PAGE, page);
            recreate();
        });
        root.addView(group);

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

        FiveElementView(Context c, String[] names, int[] colors) {
            super(c);
            this.names = names;
            this.colors = colors;
            node.setStyle(Paint.Style.FILL);
            label.setColor(Color.WHITE);
            label.setTextAlign(Paint.Align.CENTER);
            label.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            gen.setColor(0xFF455A64);
            gen.setStyle(Paint.Style.STROKE);
            ctrl.setColor(0xFFC62828);
            ctrl.setStyle(Paint.Style.STROKE);
            ctrl.setPathEffect(new DashPathEffect(new float[]{14, 10}, 0));
            ctrlHead.setColor(0xFFC62828);
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
