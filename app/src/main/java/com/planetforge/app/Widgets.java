package com.planetforge.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/** Home-screen widgets (Life, Planet, Timeline). Each is drawn into a bitmap sized to the widget, so any size works. */
public class Widgets {
    static final int LIFE = 0, PLANET = 1, TIME = 2;

    // ---- data ----------------------------------------------------------------------------------
    public static void save(Context c, String json) {
        c.getSharedPreferences("pf", Context.MODE_PRIVATE).edit().putString("wJson", json).putLong("wAt", System.currentTimeMillis()).apply();
        refreshAll(c);
    }

    static JSONObject load(Context c) {
        try {
            String s = c.getSharedPreferences("pf", Context.MODE_PRIVATE).getString("wJson", null);
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) { return null; }
    }

    public static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        Class<?>[] cls = { LifeWidget.class, PlanetWidget.class, TimeWidget.class };
        int[] kinds = { LIFE, PLANET, TIME };
        for (int i = 0; i < cls.length; i++) {
            int[] ids = m.getAppWidgetIds(new ComponentName(c, cls[i]));
            for (int id : ids) update(c, m, id, kinds[i]);
        }
    }

    static void update(Context c, AppWidgetManager m, int id, int kind) {
        try {
            Bundle o = m.getAppWidgetOptions(id);
            float d = c.getResources().getDisplayMetrics().density;
            boolean land = c.getResources().getConfiguration().orientation == 2;
            int wdp = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180);
            int hdp = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110);
            if (wdp < 40) wdp = 180; if (hdp < 40) hdp = 110;
            float sc = Math.min(d, 2.4f);
            int pw = Math.round(wdp * sc), ph = Math.round(hdp * sc);
            Bitmap bm = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bm);
            cv.scale(sc, sc);
            draw(cv, wdp, hdp, kind, load(c), c.getSharedPreferences("pf", Context.MODE_PRIVATE).getLong("wAt", 0));
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
            rv.setImageViewBitmap(R.id.img, bm);
            Intent in = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            rv.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, kind, in, fl));
            m.updateAppWidget(id, rv);
        } catch (Throwable t) { /* a widget must never crash the app */ }
    }

    // ---- providers ---------------------------------------------------------------------------------
    public static abstract class Base extends AppWidgetProvider {
        abstract int kind();
        @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { for (int id : ids) update(c, m, id, kind()); }
        @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) { update(c, m, id, kind()); }
    }
    public static class LifeWidget extends Base { int kind() { return LIFE; } }
    public static class PlanetWidget extends Base { int kind() { return PLANET; } }
    public static class TimeWidget extends Base { int kind() { return TIME; } }

    // ---- drawing (all coordinates in dp) -----------------------------------------------------------
    static final int INK = 0xFFE8EEFC, DIM = 0xFF9FB0D4, TRACK = 0x24789ADC;
    static final int GR1 = 0xFF1F7A3A, GR2 = 0xFF58E07A, BL1 = 0xFF2A4F9C, BL2 = 0xFF6AAEFF, CY1 = 0xFF127A7A, CY2 = 0xFF3DE0D0,
            AM1 = 0xFF9A5A14, AM2 = 0xFFFFB04A, VI1 = 0xFF5A2A9C, VI2 = 0xFFC08AFF, GY1 = 0xFF6A7FA8, GY2 = 0xFFBCD4FF, RD1 = 0xFF3A9A3A, RD2 = 0xFFFF5A3A;

    static Paint P(int color, float size, boolean bold) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color); p.setTextSize(size); p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return p;
    }

    static String fit(String s, Paint p, float w) {
        if (s == null) return "";
        return TextUtils.ellipsize(s, (TextPaint) p, Math.max(w, 1), TextUtils.TruncateAt.END).toString();
    }

    static void text(Canvas c, String s, float x, float y, Paint p, float w) { c.drawText(fit(s, p, w), x, y, p); }

    static void textR(Canvas c, String s, float xr, float y, Paint p, float w) {
        String t = fit(s, p, w);
        c.drawText(t, xr - p.measureText(t), y, p);
    }

    static void bar(Canvas c, float x, float y, float w, float h, float frac, String label, String value, int c1, int c2) {
        Paint tr = new Paint(Paint.ANTI_ALIAS_FLAG); tr.setColor(TRACK);
        float r = Math.min(7, h / 2.5f);
        c.drawRoundRect(new RectF(x, y, x + w, y + h), r, r, tr);
        frac = Math.max(0f, Math.min(1f, frac));
        if (frac > 0.01f) {
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            f.setShader(new LinearGradient(x, 0, x + w, 0, c1, c2, Shader.TileMode.CLAMP)); f.setAlpha(170);
            c.drawRoundRect(new RectF(x, y, x + Math.max(w * frac, r * 2), y + h), r, r, f);
        }
        float ts = Math.max(8.5f, Math.min(12f, h * 0.55f));
        Paint pl = P(0xFFCFD9F2, ts, false), pv = P(0xFFFFFFFF, ts, true);
        float base = y + h / 2 + ts * 0.36f;
        float vw = pv.measureText(value);
        textR(c, value, x + w - 7, base, pv, w * 0.6f);
        text(c, label, x + 7, base, pl, w - 14 - Math.min(vw, w * 0.6f) - 4);
    }

    static void card(Canvas c, float w, float h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(0xE60A0F1E);
        float r = Math.min(24, Math.min(w, h) / 3f);
        c.drawRoundRect(new RectF(0, 0, w, h), r, r, p);
    }

    static String mins(long at) {
        if (at <= 0) return "";
        long m = (System.currentTimeMillis() - at) / 60000;
        if (m < 2) return "just now";
        if (m < 90) return m + " min ago";
        return (m / 60) + " h ago";
    }

    static String myr(int v) { return String.format(Locale.US, "%,d Myr", v); }
    static String tempF(JSONObject j) { int c = j.optInt("tempC"); return Math.round(c * 9f / 5f + 32) + "°F"; }
    static String tempBoth(JSONObject j) { return tempF(j) + " (" + j.optInt("tempC") + " °C)"; }
    static float tempFrac(JSONObject j) { return Math.max(0f, Math.min(1f, (j.optInt("tempC") + 40f) / 120f)); }
    static int tempCol(float f) { // cold blue -> teal -> yellow -> red
        int[] cs = { 0xFF3A6FFF, 0xFF3DE0D0, 0xFFFFD24A, 0xFFFF4A3A };
        float x = f * 3; int i = Math.min(2, (int) x); float t = x - i;
        int a = cs[i], b = cs[i + 1], out = 0xFF000000;
        for (int s = 0; s <= 16; s += 8) { int ca = (a >> s) & 255, cb = (b >> s) & 255; out |= ((int) (ca + (cb - ca) * t) & 255) << s; }
        return out;
    }

    static void tempBar(Canvas c, float x, float y, float w, float h, JSONObject j, String label, String value) {
        float f = tempFrac(j);
        bar(c, x, y, w, h, f, label, value, 0xFF3A6FFF, tempCol(f));
    }

    static void draw(Canvas c, int w, int h, int kind, JSONObject j, long at) {
        card(c, w, h);
        if (j == null) {
            Paint p = P(INK, 12, true), q = P(DIM, 10.5f, false);
            text(c, "🪐 Planet Forge", 14, h / 2f - 2, p, w - 28);
            text(c, "Open the game to start your world", 14, h / 2f + 14, q, w - 28);
            return;
        }
        boolean time = kind == TIME;
        if (time) { if (h >= w) vtime(c, w, h, j); else htime(c, w, h, j); return; }
        int cols = w < 130 ? 2 : (w < 245 ? 3 : 4);
        if (cols == 2 || h < 80 && w < 160) small(c, w, h, kind, j);
        else if (kind == LIFE) life(c, w, h, j, cols); else planet(c, w, h, j, cols);
    }

    // header: icon + title left, subtitle right; returns y where content starts
    static float header(Canvas c, float w, String ico, String title, String sub) {
        Paint a = P(INK, 13, true), b = P(0xFF8EA0C4, 10, false);
        float subW = Math.min(w * 0.5f, b.measureText(sub));
        text(c, ico + " " + title, 12, 21, a, w - 24 - subW - 6);
        textR(c, sub, w - 12, 20.5f, b, w * 0.5f);
        return 28;
    }

    static void small(Canvas c, int w, int h, int kind, JSONObject j) {
        float cx = w / 2f;
        if (kind == LIFE) {
            Paint t = P(INK, 12, true); String hd = "🌱 Life";
            c.drawText(hd, cx - t.measureText(hd) / 2, 20, t);
            float r = Math.min(w, h - 50) / 2f - 8, cy = 28 + r + 6;
            if (r < 14) r = 14;
            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG); ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(Math.max(7, r * 0.22f)); ring.setColor(0x33789ADC);
            RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
            c.drawArc(o, 0, 360, false, ring);
            ring.setColor(GR2); ring.setStrokeCap(Paint.Cap.ROUND);
            c.drawArc(o, -90, 360f * Math.max(0.01f, j.optInt("life") / 100f), false, ring);
            Paint v = P(0xFFFFFFFF, Math.max(14, r * 0.62f), true); String s = j.optInt("life") + "%";
            c.drawText(s, cx - v.measureText(s) / 2, cy + v.getTextSize() * 0.36f, v);
            Paint d = P(DIM, 10, false); String s2 = j.optInt("hab") + "% habitable";
            c.drawText(fit(s2, d, w - 12), cx - Math.min(d.measureText(s2), w - 12) / 2, h - 10, d);
        } else {
            Paint t = P(INK, 12, true); String hd = "🪐 Planet";
            c.drawText(hd, cx - t.measureText(hd) / 2, 20, t);
            Paint v = P(0xFFFFFFFF, Math.min(34, Math.max(20, h * 0.27f)), true); String s = tempF(j);
            c.drawText(s, cx - v.measureText(s) / 2, h / 2f + 10, v);
            float bx = 14, bw = w - 28, by = h / 2f + 22;
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.setShader(new LinearGradient(bx, 0, bx + bw, 0, new int[]{0xFF3A6FFF, 0xFF3DE0D0, 0xFFFFD24A, 0xFFFF4A3A}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(bx, by, bx + bw, by + 6), 3, 3, g);
            Paint m = new Paint(Paint.ANTI_ALIAS_FLAG); m.setColor(0xFFFFFFFF);
            float mx = bx + bw * tempFrac(j);
            c.drawRoundRect(new RectF(mx - 2, by - 3, mx + 2, by + 9), 2, 2, m);
            Paint d = P(DIM, 10, false); String s2 = j.optString("era");
            if (h > 100) { String s3 = myr(j.optInt("myr")) + " · " + s2; c.drawText(fit(s3, d, w - 12), cx - Math.min(d.measureText(s3), w - 12) / 2, h - 10, d); }
        }
    }

    static void life(Canvas c, int w, int h, JSONObject j, int cols) {
        float y = header(c, w, "🌱", "Life", (cols >= 4 ? j.optString("name") + " · " : "") + myr(j.optInt("myr")));
        ArrayList<Object[]> b = new ArrayList<>();
        b.add(new Object[]{"Life", j.optInt("life") + "%", j.optInt("life") / 100f, GR1, GR2});
        b.add(new Object[]{"Habitable", j.optInt("hab") + "%", j.optInt("hab") / 100f, BL1, BL2});
        b.add(new Object[]{"O₂", j.optInt("o2") + "%", j.optInt("o2") / 42f, CY1, CY2});
        b.add(new Object[]{"Lineages", String.valueOf(j.optInt("lineages")), Math.min(1f, j.optInt("lineages") / 20f), VI1, VI2});
        String fl = j.optString("flora");
        b.add(new Object[]{"🌿 Plants", fl.length() == 0 ? "none yet" : fl, fl.length() == 0 ? 0f : j.optInt("life") / 100f, GR1, GR2});
        b.add(new Object[]{"🧠 Mind", j.optInt("nations") > 0 ? j.optInt("nations") + " nations" : j.optInt("mind") + "%", j.optInt("nations") > 0 ? 1f : j.optInt("mind") / 100f, AM1, AM2});
        footLines(c, w, h, y, b, cols, foot(j, true), j);
    }

    static String foot(JSONObject j, boolean life) {
        if (life) {
            String n = j.optString("newest"); int na = j.optInt("nations");
            String a = n.length() > 0 ? "✨ Newest: " + n : "✨ No animals yet";
            if (na > 0) return a + " · 🏛 " + na + (na == 1 ? " nation" : " nations") + " · " + compact(j.optDouble("pop")) + " people";
            return a + " · 🧠 no minds yet";
        }
        String s = "☄️ " + j.optInt("impacts") + " impacts";
        if (j.optString("star").length() > 0) s += " · ☀ " + j.optString("star") + " sun";
        s += " · ⏳ " + myr(j.optInt("myr"));
        if (j.optString("season").length() > 0) s += " · " + j.optString("season");
        return s;
    }

    static String compact(double v) {
        if (v >= 1e9) return String.format(Locale.US, "%.1fB", v / 1e9);
        if (v >= 1e6) return String.format(Locale.US, "%.1fM", v / 1e6);
        if (v >= 1e3) return String.format(Locale.US, "%.0fK", v / 1e3);
        return String.valueOf(Math.round(v));
    }

    /** bars in cols<4 ? 1 : 2 columns, as many rows as fit, then a footer line when there is room */
    static void footLines(Canvas c, int w, int h, float y, ArrayList<Object[]> bars, int cols, String foot, JSONObject j) {
        int ncol = cols >= 4 ? 2 : 1;
        float gap = 4, fh = h - y > 80 ? 16 : 0;
        float avail = h - y - 8 - fh;
        int maxRows = (bars.size() + ncol - 1) / ncol;
        float bh = Math.max(15, Math.min(22, (avail - gap * (maxRows - 1)) / maxRows));
        int rows = Math.max(1, Math.min(maxRows, (int) ((avail + gap) / (bh + gap))));
        float bw = (w - 24 - gap * (ncol - 1)) / ncol;
        int n = Math.min(bars.size(), rows * ncol);
        for (int i = 0; i < n; i++) {
            Object[] b = bars.get(i);
            int col = ncol == 1 ? 0 : i % 2, row = ncol == 1 ? i : i / 2;
            bar(c, 12 + col * (bw + gap), y + row * (bh + gap), bw, bh, (Float) b[2], (String) b[0], (String) b[1], (Integer) b[3], (Integer) b[4]);
        }
        if (fh > 0) {
            float fy = y + rows * (bh + gap) + 10;
            if (fy < h - 4) text(c, foot, 14, Math.min(fy + 2, h - 8), P(0xFFAEBBDC, 10.5f, false), w - 28);
        }
    }

    static void planet(Canvas c, int w, int h, JSONObject j, int cols) {
        String sub = (cols >= 4 ? j.optString("era") + " · " + j.optDouble("size") + "× Earth · " : "") + mins(j.optLong("at"));
        float y = header(c, w, "🪐", j.optString("name"), sub);
        float bh = Math.max(16, Math.min(22, (h - y - 30) / 5f));
        tempBar(c, 12, y, w - 24, bh, j, "🌡 Surface temp", tempBoth(j));
        y += bh + 4;
        ArrayList<Object[]> b = new ArrayList<>();
        b.add(new Object[]{"🌊 Ocean", j.optInt("ocean") + "%", j.optInt("ocean") / 100f, BL1, BL2});
        b.add(new Object[]{"🫁 Oxygen", j.optInt("o2") + "%", j.optInt("o2") / 42f, CY1, CY2});
        b.add(new Object[]{"🫧 CO₂", j.optInt("co2") + "%", j.optInt("co2") / 100f, CY1, CY2});
        b.add(new Object[]{"☁ Clouds", j.optInt("clouds") + "%", j.optInt("clouds") / 100f, GY1, GY2});
        b.add(new Object[]{"🕓 Day", j.optDouble("dayH") + " h", j.optDouble("dayH") <= 0 ? 0f : (float) Math.min(1, j.optDouble("dayH") / 48), AM1, AM2});
        b.add(new Object[]{"🌋 Volcanoes", String.valueOf(j.optInt("vol")), Math.min(1f, j.optInt("vol") / 25f), AM1, AM2});
        b.add(new Object[]{"🌙 Moons", j.optInt("moons") + (j.optBoolean("ring") ? " + ring" : ""), Math.min(1f, j.optInt("moons") / 4f), VI1, VI2});
        b.add(new Object[]{"🏭 Pollution", j.optInt("poll") + "%", j.optInt("poll") / 100f, RD1, RD2});
        footLines(c, w, h, y, b, cols, foot(j, false), j);
    }

    // ---- timeline ------------------------------------------------------------------------------------
    static class Ev { String ico, name, when; boolean done, now; }

    static ArrayList<Ev> miles(JSONObject j) {
        ArrayList<Ev> l = new ArrayList<>();
        JSONObject ms = j.optJSONObject("ms");
        l.add(ev("🌍", "Forming", "0 Myr", true));
        if (j.optInt("moons") > 0 || (ms != null && !ms.isNull("moon"))) l.add(ev("🌙", "Moon", when(ms, "moon"), true));
        l.add(ev("🌊", "Oceans", when(ms, "oceans"), j.optInt("ocean") > 5));
        l.add(ev("🌱", "Life", when(ms, "life"), j.optInt("life") > 1));
        l.add(ev("🧠", "Minds", when(ms, "minds"), j.optInt("nations") > 0));
        l.add(ev("🏛", "Nations", when(ms, "nations"), j.optInt("nations") > 1));
        int last = 0; for (int i = 0; i < l.size(); i++) if (l.get(i).done) last = i;
        for (int i = last + 1; i < l.size(); i++) if (l.get(i).when.length() == 0 || !l.get(i).done) l.get(i).when = i == last + 1 && i == 4 ? "readiness " + j.optInt("mind") + "%" : "not yet";
        l.get(last).now = true;
        return l;
    }

    static Ev ev(String ico, String name, String when, boolean done) { Ev e = new Ev(); e.ico = ico; e.name = name; e.when = done ? when : "not yet"; e.done = done; return e; }

    static String when(JSONObject ms, String k) {
        if (ms == null || ms.isNull(k)) return "";
        return String.format(Locale.US, "%,d Myr", ms.optInt(k));
    }

    static void dot(Canvas c, float x, float y, Ev e) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (e.now) { p.setColor(0x7358E07A); c.drawCircle(x, y, 8, p); p.setColor(0xFFFFFFFF); }
        else p.setColor(e.done ? GR2 : 0xFF2A3558);
        c.drawCircle(x, y, 5, p);
    }

    static void vtime(Canvas c, int w, int h, JSONObject j) {
        Paint hp = P(INK, 12, true);
        c.drawText(fit("⏳ Time", hp, w - 16), 10, 20, hp);
        boolean events = w >= 110;
        ArrayList<Ev> l = new ArrayList<>();
        if (events) {
            JSONArray hs = j.optJSONArray("hist");
            if (hs != null) for (int i = Math.min(hs.length(), 8) - 1; i >= 0; i--) { JSONObject e = hs.optJSONObject(i); if (e == null) continue; Ev x = new Ev(); x.ico = ""; x.name = e.optString("text"); x.when = String.format(Locale.US, "%,d Myr", e.optInt("age")); x.done = true; l.add(x); }
            if (l.isEmpty()) events = false;
        }
        if (!events) l = miles(j);
        else { Ev n = new Ev(); n.name = "Now"; n.when = myr(j.optInt("myr")); n.done = true; n.now = true; l.add(n); }
        float step = events ? 40 : 38, top = 38, lx = 17;
        int fit = Math.max(1, (int) ((h - top - 6) / step));
        int from = Math.max(0, l.size() - fit);
        int cnt = l.size() - from;
        Paint ln = new Paint(Paint.ANTI_ALIAS_FLAG); ln.setColor(0x40789ADC); ln.setStrokeWidth(3);
        c.drawLine(lx, top, lx, top + (cnt - 1) * step, ln);
        for (int i = 0; i < cnt; i++) {
            Ev e = l.get(from + i); float y = top + i * step;
            dot(c, lx, y, e);
            Paint a = P(e.now ? GR2 : (e.done ? INK : DIM), 11, true), b = P(DIM, 9.5f, false);
            float tw = w - 36 - 6;
            if (events) {
                text(c, e.name, 32, y - 1, a, tw); text(c, e.when, 32, y + 12, b, tw);
            } else {
                text(c, e.ico + " " + e.name, 32, y - 1, a, tw); text(c, e.when, 32, y + 12, b, tw);
            }
        }
    }

    static void htime(Canvas c, int w, int h, JSONObject j) {
        ArrayList<Ev> l = miles(j);
        int fit = Math.max(2, Math.min(l.size(), w / 62));
        int from = Math.max(0, Math.min(l.size() - fit, indexNow(l) - fit / 2));
        float cell = (w - 16) / (float) fit, y = h * 0.42f;
        Paint ln = new Paint(Paint.ANTI_ALIAS_FLAG); ln.setColor(0x40789ADC); ln.setStrokeWidth(3);
        c.drawLine(8 + cell / 2, y, 8 + cell * (fit - 0.5f), y, ln);
        for (int i = 0; i < fit; i++) {
            Ev e = l.get(from + i); float cx = 8 + cell * (i + 0.5f);
            dot(c, cx, y, e);
            Paint a = P(e.now ? GR2 : (e.done ? INK : DIM), 10.5f, true), b = P(DIM, 9, false);
            String t = e.ico + " " + e.name; float tw = cell - 4;
            c.drawText(fit(t, a, tw), cx - Math.min(a.measureText(t), tw) / 2, y + 20, a);
            if (h > 56) c.drawText(fit(e.when, b, tw), cx - Math.min(b.measureText(e.when), tw) / 2, y + 32, b);
        }
    }

    static int indexNow(ArrayList<Ev> l) { for (int i = 0; i < l.size(); i++) if (l.get(i).now) return i; return 0; }
}
