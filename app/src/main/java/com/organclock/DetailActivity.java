package com.organclock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
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
 * Main screen: every organ listed one after another (organ name, element ·
 * emotion · time window, herbs). Opened from the widget or a notification, it
 * scrolls to and highlights the organ that is active right now. The overflow
 * menu leads to Settings.
 */
public class DetailActivity extends Activity {

    private float density;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;

        Context l = OrganClockWidget.localized(this);
        Resources res = l.getResources();
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
                    herbs[i]);
            root.addView(block);
            if (isNow) {
                activeBlock = block;
            }
        }

        final ScrollView scroll = new ScrollView(this);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(scroll);

        // Scroll to the active organ once layout is measured.
        final View target = activeBlock;
        if (target != null) {
            scroll.post(() -> scroll.scrollTo(0, Math.max(0, target.getTop() - dp(12))));
        }
    }

    private View buildBlock(String nowTag, String organ, String meta, String herbs) {
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        String title = OrganClockWidget.localized(this).getString(R.string.menu_settings);
        menu.add(Menu.NONE, 1, Menu.NONE, title)
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
