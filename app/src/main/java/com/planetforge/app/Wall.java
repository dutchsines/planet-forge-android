package com.planetforge.app;

import android.content.Context;
import android.util.Base64;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

/** Storage for the live wallpaper: a ring of pictures of the planet turning (written by the game page, shown by PlanetWallpaperService). */
final class Wall {
    static File dir(Context c) { File d = new File(c.getFilesDir(), "wall"); d.mkdirs(); return d; }
    static String name(String p, int i) { return String.format(Locale.US, "%s_%02d.jpg", p, i); }

    /** One picture of the set; written under a temporary name so the wallpaper never sees a half-finished set. */
    static void frame(Context c, int i, int n, String dataUrl) {
        try {
            byte[] b = Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), Base64.DEFAULT);
            FileOutputStream o = new FileOutputStream(new File(dir(c), name("n", i))); o.write(b); o.close();
        } catch (Exception e) { }
    }

    /** The whole set arrived: swap it in. */
    static void done(Context c, int n) {
        try {
            File d = dir(c);
            for (int i = 0; i < n; i++) { File s = new File(d, name("n", i)), t = new File(d, name("f", i)); if (s.exists()) { t.delete(); s.renameTo(t); } }
            for (int i = n; i < 99; i++) { File t = new File(d, name("f", i)); if (t.exists()) t.delete(); }
            c.getSharedPreferences("pf", Context.MODE_PRIVATE).edit().putInt("wallN", n).putLong("wallAt", System.currentTimeMillis()).putBoolean("wallOn", true).apply();
        } catch (Exception e) { }
    }
}
