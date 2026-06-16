package com.organclock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.widget.RemoteViews;

import java.util.Calendar;
import java.util.Locale;

/**
 * TCM "organ clock" home-screen widget.
 *
 * The day is split into twelve 2-hour windows, each linked to an organ that is
 * considered most active during that window. We show the active organ (large)
 * and a few supporting herbs (small), and can optionally post a notification
 * when a user-selected organ becomes active. The app UI lives in MainActivity.
 */
public class OrganClockWidget extends AppWidgetProvider {

    static final String ACTION_TICK = "com.organclock.TICK";

    // Shared preferences (also read/written by MainActivity).
    static final String PREFS = "organclock";
    static final String KEY_LANG = "lang";            // "" = system, "en", "cs"
    static final String KEY_THEME = "theme";          // "system", "light", "dark"
    static final String KEY_NOTIFY = "notify_";       // + slot index -> boolean
    static final String KEY_LAST_SLOT = "last_slot";  // last slot we notified for
    static final String KEY_SOUND = "sound";          // alarm sound URI ("" = default)

    // Five-element accent color per slot (Wood/Fire/Earth/Metal/Water).
    static final int[] ELEMENT_COLOR = {
            0xFF4CAF50, // Gallbladder  – Wood
            0xFF4CAF50, // Liver        – Wood
            0xFF90A4AE, // Lung         – Metal
            0xFF90A4AE, // Large Int.   – Metal
            0xFFC8A24B, // Stomach      – Earth
            0xFFC8A24B, // Spleen       – Earth
            0xFFE53935, // Heart        – Fire
            0xFFE53935, // Small Int.   – Fire
            0xFF1E88E5, // Bladder      – Water
            0xFF1E88E5, // Kidney       – Water
            0xFFE53935, // Pericardium  – Fire
            0xFFE53935, // Triple Burner– Fire
    };

    // Organ/window/herb text lives in res/values*/strings.xml so it can be
    // localized (see values-cs/ for Czech). Slots are indexed 0..11 starting at
    // the 23:00 window (Gallbladder).

    /** Map a 24h hour (0..23) to its 2-hour slot index. */
    static int slotForHour(int hour) {
        return ((hour + 1) / 2) % 12;
    }

    static int currentSlot() {
        return slotForHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Returns a context whose resources use the user-chosen language override. */
    static Context localized(Context ctx) {
        String lang = prefs(ctx).getString(KEY_LANG, "");
        if (lang.isEmpty()) {
            return ctx;
        }
        Configuration cfg = new Configuration(ctx.getResources().getConfiguration());
        cfg.setLocale(new Locale(lang));
        return ctx.createConfigurationContext(cfg);
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        updateAll(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String action = intent.getAction();
        if (ACTION_TICK.equals(action) || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            updateAll(ctx);
        }
    }

    @Override
    public void onDisabled(Context ctx) {
        // Keep the alarm alive if the user still wants notifications without a widget.
        if (anyNotifyEnabled(ctx)) {
            scheduleNextBoundary(ctx);
        } else {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                am.cancel(tickIntent(ctx));
            }
        }
    }

    static void updateAll(Context ctx) {
        Context l = localized(ctx);
        Resources res = l.getResources();
        String[] windows = res.getStringArray(R.array.windows);
        String[] organs = res.getStringArray(R.array.organs);
        String[] herbs = res.getStringArray(R.array.herbs);
        String[] emotions = res.getStringArray(R.array.emotions);

        int slot = currentSlot();
        String subtitle = windows[slot] + "  ·  " + emotions[slot];

        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, OrganClockWidget.class));
        for (int id : ids) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
            v.setTextViewText(R.id.window, subtitle);
            v.setTextViewText(R.id.organ, organs[slot]);
            v.setTextViewText(R.id.herbs, herbs[slot]);
            v.setOnClickPendingIntent(R.id.root, detailIntent(ctx));
            mgr.updateAppWidget(id, v);
        }

        // Notify only on an actual slot transition, so the periodic safety
        // refresh and boundary alarm never produce duplicate notifications.
        SharedPreferences sp = prefs(ctx);
        if (sp.getInt(KEY_LAST_SLOT, -1) != slot) {
            sp.edit().putInt(KEY_LAST_SLOT, slot).apply();
            maybeNotify(ctx, slot, organs, herbs, subtitle);
        }

        scheduleNextBoundary(ctx);
    }

    static boolean anyNotifyEnabled(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        for (int i = 0; i < 12; i++) {
            if (sp.getBoolean(KEY_NOTIFY + i, false)) {
                return true;
            }
        }
        return false;
    }

    /** Fire the alarm for a newly-active organ: start the foreground service
     *  that plays the chosen sound on the alarm stream and shows a notification. */
    static void maybeNotify(Context ctx, int slot, String[] organs, String[] herbs, String subtitle) {
        if (!prefs(ctx).getBoolean(KEY_NOTIFY + slot, false)) {
            return;
        }
        Intent i = new Intent(ctx, AlarmSoundService.class);
        i.putExtra(AlarmSoundService.EXTRA_TITLE, organs[slot]);
        i.putExtra(AlarmSoundService.EXTRA_TEXT, herbs[slot]);
        i.putExtra(AlarmSoundService.EXTRA_SUB, subtitle);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Exception e) {
            // a background start can be blocked in rare states; ignore
        }
    }

    /** Wake at the next 2-hour window boundary so the widget flips on time. */
    static void scheduleNextBoundary(Context ctx) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        // Window boundaries fall on odd hours (23, 1, 3, ...). Advance to the next one.
        c.add(Calendar.HOUR_OF_DAY, 1);
        while (c.get(Calendar.HOUR_OF_DAY) % 2 == 0) {
            c.add(Calendar.HOUR_OF_DAY, 1);
        }

        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        long t = c.getTimeInMillis();
        if (anyNotifyEnabled(ctx)) {
            // Exact alarm clock: wakes the device, fires in Doze, bypasses DND as
            // an alarm, and lets us start the foreground sound service from the
            // resulting broadcast. Needs no SCHEDULE_EXACT_ALARM permission.
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, detailIntent(ctx)), tickIntent(ctx));
        } else {
            // Widget-only: inexact and battery-friendly.
            am.setAndAllowWhileIdle(AlarmManager.RTC, t, tickIntent(ctx));
        }
    }

    static PendingIntent tickIntent(Context ctx) {
        Intent i = new Intent(ctx, OrganClockWidget.class);
        i.setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(
                ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent detailIntent(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_PAGE, 0); // open on the "Now" tab
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(
                ctx, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
