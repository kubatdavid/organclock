package com.organclock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Full-screen "alarm ringing" screen with a large Stop button. Shown when an
 * organ alarm fires (and as the notification's tap target), so the alarm is
 * always easy to silence — even over the lock screen.
 */
public class AlarmStopActivity extends Activity {

    static final String EXTRA_TITLE = "title";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(OrganClockWidget.localized(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        float d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = Math.round(24 * d);
        root.setPadding(pad, pad, pad, pad);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        TextView t = new TextView(this);
        t.setText(title != null ? title : getString(R.string.app_name));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, Math.round(36 * d));
        root.addView(t);

        Button stop = new Button(this);
        stop.setText(getString(R.string.alarm_stop));
        stop.setAllCaps(false);
        stop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        stop.setOnClickListener(v -> {
            stopAlarm();
            finish();
        });
        root.addView(stop, new LinearLayout.LayoutParams(
                Math.round(220 * d), Math.round(96 * d)));

        setContentView(root);
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
