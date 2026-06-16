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

    // Slots are indexed 0..11 starting at the 23:00 window (Gallbladder).
    static final String[] WINDOWS = {
            "23:00 – 01:00",
            "01:00 – 03:00",
            "03:00 – 05:00",
            "05:00 – 07:00",
            "07:00 – 09:00",
            "09:00 – 11:00",
            "11:00 – 13:00",
            "13:00 – 15:00",
            "15:00 – 17:00",
            "17:00 – 19:00",
            "19:00 – 21:00",
            "21:00 – 23:00",
    };

    static final String[] ORGANS = {
            "Gallbladder",
            "Liver",
            "Lung",
            "Large Intestine",
            "Stomach",
            "Spleen",
            "Heart",
            "Small Intestine",
            "Bladder",
            "Kidney",
            "Pericardium",
            "Triple Burner",
    };

    static final String[] HERBS = {
            "Dandelion root · Milk thistle · Bupleurum · Turmeric",   // Gallbladder
            "Milk thistle · Bupleurum · Schisandra · Dandelion",      // Liver
            "Mullein · Astragalus · Licorice root · Elecampane",      // Lung
            "Rhubarb · Slippery elm · Psyllium · Cascara",           // Large Intestine
            "Ginger · Peppermint · Citrus peel · Licorice",          // Stomach
            "Ginseng · Atractylodes · Codonopsis · Ginger",          // Spleen
            "Hawthorn · Motherwort · Reishi · Schisandra",           // Heart
            "Fennel · Chamomile · Caraway · Dandelion",              // Small Intestine
            "Cornsilk · Uva ursi · Plantain · Marshmallow",          // Bladder
            "Rehmannia · Cordyceps · Goji · Nettle root",            // Kidney
            "Hawthorn · Rose · Salvia (Dan Shen) · Linden",          // Pericardium
            "Cinnamon · Bupleurum · Ginger · Fennel",                // Triple Burner
    };

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

        int slot = slotForHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        for (int id : ids) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
            v.setTextViewText(R.id.window, WINDOWS[slot]);
            v.setTextViewText(R.id.organ, ORGANS[slot]);
            v.setTextViewText(R.id.herbs, HERBS[slot]);
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
