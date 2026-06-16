package com.organclock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Main screen: every organ listed one after another (a colored element dot,
 * organ name, element · emotion · time window, herbs). Opened from the widget
 * or a notification, it scrolls to and highlights the organ active right now.
 * The overflow menu leads to Settings.
 *
 * The whole activity is localized in {@link #attachBaseContext} so the action
 * bar, menu, and lists all follow the user-chosen language.
 */
public class DetailActivity extends Activity {

    private float density;
    private String builtLang;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(OrganClockWidget.localized(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        builtLang = OrganClockWidget.prefs(this).getString(OrganClockWidget.KEY_LANG, "");

        Resources res = getResources();
        setTitle(res.getString(R.string.app_name));

        String[] windows = res.getStringArray(R.array.windows);
        String[] organs = res.getStringArray(R.array.organs);
        String[] elements = res.getStringArray(R.array.elements);
        String[] emotions = res.getStringArray(R.array.emotions);
        String[] herbs = res.getStringArray(R.array.herbs);
        String nowLabel = res.getString(R.string.now);

        int active = OrganClockWidget.currentSlot();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        View activeBlock = null;
        for (int i = 0; i < organs.length; i++) {
            boolean isNow = (i == active);
            View block = buildBlock(
                    isNow ? nowLabel : null,
                    organs[i],
                    elements[i] + "  ·  " + emotions[i] + "  ·  " + windows[i],
                    herbs[i],
                    OrganClockWidget.ELEMENT_COLOR[i]);
            root.addView(block);
            if (isNow) {
                activeBlock = block;
            }
        }

        final ScrollView scroll = new ScrollView(this);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(scroll);

        final View target = activeBlock;
        if (target != null) {
            scroll.post(() -> scroll.scrollTo(0, Math.max(0, target.getTop() - dp(12))));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If the language was changed in Settings while we were paused, rebuild.
        String lang = OrganClockWidget.prefs(this).getString(OrganClockWidget.KEY_LANG, "");
        if (!lang.equals(builtLang)) {
            recreate();
        }
    }

    private View buildBlock(String nowTag, String organ, String meta, String herbs, int color) {
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
        name.setCompoundDrawablesWithIntrinsicBounds(elementDot(color), null, null, null);
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

    /** A solid colored circle used as the element marker beside each organ. */
    private GradientDrawable elementDot(int color) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        int s = dp(16);
        dot.setSize(s, s);
        dot.setBounds(0, 0, s, s);
        return dot;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, getString(R.string.menu_settings))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private int dp(int v) {
        return Math.round(v * density);
    }
}
