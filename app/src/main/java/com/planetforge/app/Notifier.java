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
    static final String CHANNEL = "planet_events2";       // quiet: no sound, no pop-up
    static final String BIG = "planet_big2";
    static final String PHOTOS = "planet_photos2";
    private static final int ID_NEWS = 100, ID_PHOTO = 101, ID_BIG = 102;
    private static final int MAX_PER_DAY = 6;
    private static final long GAP_NEWS = 45 * 60000L, GAP_BIG = 15 * 60000L, SAME_TITLE = 6 * 3600000L;

    static void channel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        try { nm.deleteNotificationChannel("planet_events"); nm.deleteNotificationChannel("planet_big"); nm.deleteNotificationChannel("planet_photos"); } catch (Exception e) { }   // the first versions were too loud
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Planet events", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("New eras, wars, nations and disasters on your planet");
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(BIG) == null) {
            NotificationChannel ch = new NotificationChannel(BIG, "Big moments", NotificationManager.IMPORTANCE_DEFAULT);
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

    static void post(Context c, String title, String text) { show(c, title, text, null, false, ID_NEWS); }

    private static void show(Context c, String title, String text, String[] more, boolean big, int id) {
        try {
            channel(c);
            Notification.Builder b = base(c, big ? BIG : CHANNEL, title, text);
            if (more != null && more.length > 0) {
                Notification.InboxStyle in = new Notification.InboxStyle(); in.setBigContentTitle(title); in.addLine(text);
                for (String m : more) in.addLine(m);
                b.setStyle(in);
            } else b.setStyle(new Notification.BigTextStyle().bigText(text));
            b.setOnlyAlertOnce(true);
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, b.build());   // fixed id: a new one replaces the last, they never pile up
        } catch (Exception e) { /* no permission or no notification service: nothing to do */ }
    }

    /**
     * What happened while you were away, as ONE notification (best item first). Rate limited so it can never turn into spam:
     * at most 6 a day, 45 minutes apart (15 for big moments), the same headline at most every 6 hours, and respecting the
     * level you chose in the menu (0 off, 1 big moments only, 2 all).
     */
    static void news(Context c, org.json.JSONArray a) {
        try {
            android.content.SharedPreferences sp = c.getSharedPreferences("pf", Context.MODE_PRIVATE);
            int level = sp.getInt("nLevel", 2); if (level == 0 || a == null || a.length() == 0) return;
            long now = System.currentTimeMillis();
            String day = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(new java.util.Date());
            int count = day.equals(sp.getString("nDay", "")) ? sp.getInt("nCount", 0) : 0;
            if (count >= MAX_PER_DAY) return;
            org.json.JSONObject seen = new org.json.JSONObject(sp.getString("nSeen", "{}"));
            java.util.ArrayList<org.json.JSONObject> keep = new java.util.ArrayList<org.json.JSONObject>();
            boolean big = false;
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.getJSONObject(i); boolean bg = o.optBoolean("big", false);
                if (level == 1 && !bg) continue;
                if (now - seen.optLong(o.optString("title"), 0) < SAME_TITLE) continue;
                keep.add(o); big |= bg;
            }
            if (keep.isEmpty() || now - sp.getLong("nLast", 0) < (big ? GAP_BIG : GAP_NEWS)) return;
            org.json.JSONObject first = keep.get(0);
            String[] more = new String[Math.min(2, keep.size() - 1)];
            for (int i = 0; i < more.length; i++) more[i] = keep.get(i + 1).optString("title") + ": " + keep.get(i + 1).optString("text");
            for (org.json.JSONObject o : keep) seen.put(o.optString("title"), now);
            java.util.Iterator<String> it = seen.keys(); java.util.ArrayList<String> old = new java.util.ArrayList<String>();
            while (it.hasNext()) { String k = it.next(); if (now - seen.optLong(k) > 24 * 3600000L) old.add(k); }
            for (String k : old) seen.remove(k);
            sp.edit().putString("nDay", day).putInt("nCount", count + 1).putLong("nLast", now).putString("nSeen", seen.toString()).apply();
            show(c, first.optString("title"), first.optString("text"), more, big, big ? ID_BIG : ID_NEWS);
        } catch (Exception e) { }
    }

    /** A picture of the world with a few interesting facts under it. */
    static void photo(Context c, String title, String text, File jpg) {
        try {
            channel(c);
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inSampleSize = 1;
            Bitmap bmp = BitmapFactory.decodeFile(jpg.getAbsolutePath(), o);
            if (bmp == null) { show(c, title, text, null, false, ID_PHOTO); return; }
            Notification.Builder b = base(c, PHOTOS, title, text);
            b.setLargeIcon(Bitmap.createScaledBitmap(bmp, 256, Math.max(1, 256 * bmp.getHeight() / bmp.getWidth()), true));
            b.setStyle(new Notification.BigPictureStyle().bigPicture(bmp).setSummaryText(text));
            b.setOnlyAlertOnce(true);
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(ID_PHOTO, b.build());
        } catch (Exception e) { show(c, title, text, null, false, ID_PHOTO); }
    }
}
