package com.organclock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/**
 * Full-screen "alarm ringing" screen, styled like the rest of the app (light or
 * dark per the user's theme), showing the time and active organ with a large
 * Stop button. Appears over the lock screen so the alarm is always easy to
 * silence.
 */
public class AlarmStopActivity extends Activity {

    static final String EXTRA_TITLE = "title";
    static final String EXTRA_COLOR = "color";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(OrganClockWidget.localized(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        boolean dark = resolveDark();
        setTheme(dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        super.onCreate(state);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int bg = dark ? 0xFF121212 : 0xFFFAFAFA;
        int textPrimary = dark ? 0xFFFFFFFF : 0xFF1A1A1A;
        int textDim = dark ? 0xFFB0B0B0 : 0xFF666666;
        int accent = getIntent().getIntExtra(EXTRA_COLOR,
                dark ? 0xFF9FA8DA : 0xFF3F51B5);

        float d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(bg);
        int pad = Math.round(28 * d);
        root.setPadding(pad, pad, pad, pad);

        Calendar c = Calendar.getInstance();
        TextView time = new TextView(this);
        time.setText(String.format(Locale.US, "%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)));
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        time.setTextColor(textPrimary);
        time.setGravity(Gravity.CENTER);
        root.addView(time);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        TextView name = new TextView(this);
        name.setText(title != null ? title : getString(R.string.app_name));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);
        name.setTextColor(textDim);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setCompoundDrawablesWithIntrinsicBounds(dot(accent, Math.round(16 * d)), null, null, null);
        name.setCompoundDrawablePadding(Math.round(10 * d));
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = Math.round(8 * d);
        nameLp.bottomMargin = Math.round(56 * d);
        root.addView(name, nameLp);

        Button stop = new Button(this);
        stop.setText(getString(R.string.alarm_stop));
        stop.setAllCaps(false);
        stop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        stop.setTextColor(Color.WHITE);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(accent);
        pill.setCornerRadius(48 * d);
        stop.setBackground(pill);
        stop.setStateListAnimator(null);
        stop.setOnClickListener(v -> {
            stopAlarm();
            finish();
        });
        root.addView(stop, new LinearLayout.LayoutParams(
                Math.round(240 * d), Math.round(96 * d)));

        setContentView(root);
    }

    private boolean resolveDark() {
        String t = OrganClockWidget.prefs(this).getString(OrganClockWidget.KEY_THEME, "system");
        if ("dark".equals(t)) {
            return true;
        }
        if ("light".equals(t)) {
            return false;
        }
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private GradientDrawable dot(int color, int sizePx) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setSize(sizePx, sizePx);
        g.setBounds(0, 0, sizePx, sizePx);
        return g;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private void stopAlarm() {
        Intent i = new Intent(this, AlarmSoundService.class).setAction(AlarmSoundService.ACTION_STOP);
        try {
            startService(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        stopAlarm();
        super.onBackPressed();
    }
}
