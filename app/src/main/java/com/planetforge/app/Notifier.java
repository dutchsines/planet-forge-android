package com.planetforge.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import java.io.File;

/** Posts the world's news: events (eras, minds, wars...), big moments, and photos of the planet. */
final class Notifier {
    static final String CHANNEL = "planet_events";
    static final String BIG = "planet_big";
    static final String PHOTOS = "planet_photos";
    private static int next = 100;

    static void channel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Planet events", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("New eras, wars, nations and disasters on your planet");
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(BIG) == null) {
            NotificationChannel ch = new NotificationChannel(BIG, "Big moments", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Super minds, supervolcanoes, mass extinctions and other world-changing events");
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(PHOTOS) == null) {
            NotificationChannel ch = new NotificationChannel(PHOTOS, "World photos", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("A picture of your world every few hours, with something interesting about it");
            nm.createNotificationChannel(ch);
        }
    }

    private static Notification.Builder base(Context c, String channel, String title, String text) {
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, fl);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, channel) : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle(title).setContentText(text.split("\n")[0]).setContentIntent(pi).setAutoCancel(true);
        return b;
    }

    static void post(Context c, String title, String text) { post(c, title, text, false); }

    static void post(Context c, String title, String text, boolean big) {
        try {
            channel(c);
            Notification.Builder b = base(c, big ? BIG : CHANNEL, title, text);
            b.setStyle(new Notification.BigTextStyle().bigText(text));
            if (big && Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH);
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(next++, b.build());
        } catch (Exception e) { /* no permission or no notification service: nothing to do */ }
    }

    /** A picture of the world with a few interesting facts under it. */
    static void photo(Context c, String title, String text, File jpg) {
        try {
            channel(c);
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inSampleSize = 1;
            Bitmap bmp = BitmapFactory.decodeFile(jpg.getAbsolutePath(), o);
            if (bmp == null) { post(c, title, text, false); return; }
            Notification.Builder b = base(c, PHOTOS, title, text);
            b.setLargeIcon(Bitmap.createScaledBitmap(bmp, 256, Math.max(1, 256 * bmp.getHeight() / bmp.getWidth()), true));
            b.setStyle(new Notification.BigPictureStyle().bigPicture(bmp).setSummaryText(text));
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(next++, b.build());
        } catch (Exception e) { post(c, title, text, false); }
    }
}
