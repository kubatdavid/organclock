package com.organclock;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Settings screen: pick the display language and choose which organs should
 * raise a notification when they become active. The UI is built in code to keep
 * the project tiny (no layout XML, no AppCompat dependency).
 */
public class SettingsActivity extends Activity {

    private SharedPreferences sp;
    private float density;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(OrganClockWidget.localized(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        sp = OrganClockWidget.prefs(this);
        density = getResources().getDisplayMetrics().density;

        Resources res = getResources();
        setTitle(res.getString(R.string.app_name));
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        // --- Language ---
        root.addView(header(res.getString(R.string.settings_language)));

        final String[] codes = {"", "en", "cs"};
        String[] labels = {
                res.getString(R.string.lang_system),
                res.getString(R.string.lang_en),
                res.getString(R.string.lang_cs),
        };
        String current = sp.getString(OrganClockWidget.KEY_LANG, "");

        final RadioGroup group = new RadioGroup(this);
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
            sp.edit().putString(OrganClockWidget.KEY_LANG, (String) rb.getTag()).apply();
            applyChanges();
            recreate(); // rebuild this screen in the newly chosen language
        });
        root.addView(group);

        // --- Notifications ---
        root.addView(header(res.getString(R.string.settings_notify)));

        String[] organs = res.getStringArray(R.array.organs);
        String[] windows = res.getStringArray(R.array.windows);
        for (int i = 0; i < organs.length; i++) {
            final int slot = i;
            CheckBox cb = new CheckBox(this);
            cb.setText(organs[i] + "   ·   " + windows[i]);
            cb.setChecked(sp.getBoolean(OrganClockWidget.KEY_NOTIFY + slot, false));
            cb.setPadding(0, dp(6), 0, dp(6));
            cb.setOnCheckedChangeListener((v, checked) -> {
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
        setContentView(scroll);
    }

    private TextView header(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setPadding(0, dp(18), 0, dp(8));
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        return tv;
    }

    /** Persist a "don't notify immediately" marker, then refresh widget + alarm. */
    private void applyChanges() {
        // Set last_slot to the current slot so toggling a setting never fires an
        // instant notification for the organ that is already active.
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

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private int dp(int v) {
        return Math.round(v * density);
    }
}
