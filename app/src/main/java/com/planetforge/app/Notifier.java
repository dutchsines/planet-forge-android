package com.planetforge.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Posts the "new era" / "intelligent life" notifications. */
final class Notifier {
    static final String CHANNEL = "planet_events";
    private static int next = 100;

    static void channel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Planet events", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("New eras and the arrival of intelligent life on your planet");
            nm.createNotificationChannel(ch);
        }
    }

    static void post(Context c, String title, String text) {
        try {
            channel(c);
            Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            PendingIntent pi = PendingIntent.getActivity(c, 0, open, fl);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
            b.setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle(title).setContentText(text)
             .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(pi).setAutoCancel(true);
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(next++, b.build());
        } catch (Exception e) { /* no permission or no notification service: nothing to do */ }
    }
}
