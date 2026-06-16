package com.organclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Foreground service that plays the user-chosen sound like an alarm: on the
 * alarm audio stream (loud, audible through silent/DND), looping until stopped,
 * with a high-priority notification carrying a Stop action. Auto-stops after a
 * safety timeout so it never rings forever.
 */
public class AlarmSoundService extends Service {

    static final String ACTION_STOP = "com.organclock.STOP_ALARM";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_TEXT = "text";
    static final String EXTRA_SUB = "sub";

    private static final String CHANNEL = "organ_alarm";
    private static final int NOTIF_ID = 7;
    private static final long AUTO_STOP_MS = 60_000L;

    private MediaPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoStop = this::stopSelf;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String title = intent != null ? intent.getStringExtra(EXTRA_TITLE) : null;
        String text = intent != null ? intent.getStringExtra(EXTRA_TEXT) : null;
        String sub = intent != null ? intent.getStringExtra(EXTRA_SUB) : null;
        if (title == null) {
            title = getString(R.string.app_name);
        }
        if (text == null) {
            text = "";
        }

        Notification n = buildNotification(title, text, sub);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }

        startSound();
        handler.removeCallbacks(autoStop);
        handler.postDelayed(autoStop, AUTO_STOP_MS);
        return START_NOT_STICKY;
    }

    private void startSound() {
        stopSound();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(this, soundUri());
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            // unreadable/invalid sound: nothing to play
        }
    }

    private Uri soundUri() {
        String s = OrganClockWidget.prefs(this).getString(OrganClockWidget.KEY_SOUND, "");
        if (s != null && !s.isEmpty()) {
            try {
                return Uri.parse(s);
            } catch (Exception ignored) {
            }
        }
        Uri def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (def == null) {
            def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        return def;
    }

    private Notification buildNotification(String title, String text, String sub) {
        Context l = OrganClockWidget.localized(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(
                CHANNEL, l.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null); // we play the sound ourselves on the alarm stream
        nm.createNotificationChannel(ch);

        Intent stop = new Intent(this, AlarmSoundService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 0, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent open = new Intent(this, MainActivity.class);
        open.putExtra(MainActivity.EXTRA_PAGE, 0);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setContentIntent(openPi)
                .addAction(0, l.getString(R.string.alarm_stop), stopPi);
        if (sub != null) {
            b.setSubText(sub);
        }
        return b.build();
    }

    private void stopSound() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(autoStop);
        stopSound();
        super.onDestroy();
    }
}
