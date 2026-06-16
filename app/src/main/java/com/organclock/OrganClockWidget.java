package com.organclock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.Calendar;

/**
 * TCM "organ clock" home-screen widget.
 *
 * The day is split into twelve 2-hour windows, each linked to an organ that is
 * considered most active during that window. We show the active organ (large)
 * and a few supporting herbs (small). No network, no permissions, no UI activity.
 */
public class OrganClockWidget extends AppWidgetProvider {

    static final String ACTION_TICK = "com.organclock.TICK";

    // The organ/window/herb text lives in res/values*/strings.xml so it can be
    // localized (see values-cs/ for Czech). Slots are indexed 0..11 starting at
    // the 23:00 window (Gallbladder).

    /** Map a 24h hour (0..23) to its 2-hour slot index. */
    static int slotForHour(int hour) {
        return ((hour + 1) / 2) % 12;
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        updateAll(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_TICK.equals(intent.getAction())) {
            updateAll(ctx);
        }
    }

    @Override
    public void onDisabled(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(tickIntent(ctx));
        }
    }

    static void updateAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, OrganClockWidget.class));

        String[] windows = ctx.getResources().getStringArray(R.array.windows);
        String[] organs = ctx.getResources().getStringArray(R.array.organs);
        String[] herbs = ctx.getResources().getStringArray(R.array.herbs);

        int slot = slotForHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        for (int id : ids) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
            v.setTextViewText(R.id.window, windows[slot]);
            v.setTextViewText(R.id.organ, organs[slot]);
            v.setTextViewText(R.id.herbs, herbs[slot]);
            v.setOnClickPendingIntent(R.id.root, tickIntent(ctx));
            mgr.updateAppWidget(id, v);
        }

        scheduleNextBoundary(ctx);
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
        if (am != null) {
            // Inexact + doze-friendly: no SCHEDULE_EXACT_ALARM permission needed.
            am.setAndAllowWhileIdle(AlarmManager.RTC, c.getTimeInMillis(), tickIntent(ctx));
        }
    }

    static PendingIntent tickIntent(Context ctx) {
        Intent i = new Intent(ctx, OrganClockWidget.class);
        i.setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(
                ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
