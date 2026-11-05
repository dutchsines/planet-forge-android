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
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/** Home-screen widgets (Life, Planet, Timeline). Each is drawn into a bitmap sized to the widget, so any size works. */
public class Widgets {
    static final int LIFE = 0, PLANET = 1, TIME = 2;
    static final String ACTION_REFRESH = "com.planetforge.app.WIDGET_REFRESH";

    // ---- data ----------------------------------------------------------------------------------
    public static void save(Context c, String json) {
        c.getSharedPreferences("pf", Context.MODE_PRIVATE).edit().putString("wJson", json).putLong("wAt", System.currentTimeMillis()).putLong("wBusy", 0).apply();
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
            SharedPreferences sp = c.getSharedPreferences("pf", Context.MODE_PRIVATE);
            busyFlag = System.currentTimeMillis() - sp.getLong("wBusy", 0) < 25000;
            refreshBtn = false;
            draw(cv, wdp, hdp, kind, load(c), sp.getLong("wAt", 0));
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
            rv.setImageViewBitmap(R.id.img, bm);
            Intent in = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            rv.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, kind, in, fl));
            rv.setViewVisibility(R.id.refresh, refreshBtn ? View.VISIBLE : View.GONE);
            Class<?> pc = kind == LIFE ? LifeWidget.class : kind == PLANET ? PlanetWidget.class : TimeWidget.class;
            Intent rf = new Intent(c, pc).setAction(ACTION_REFRESH);
            rv.setOnClickPendingIntent(R.id.refresh, PendingIntent.getBroadcast(c, 10 + kind, rf, fl));
            m.updateAppWidget(id, rv);
        } catch (Throwable t) { /* a widget must never crash the app */ }
    }

    // ---- providers ---------------------------------------------------------------------------------
    public static abstract class Base extends AppWidgetProvider {
        abstract int kind();
        @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { for (int id : ids) update(c, m, id, kind()); }
        @Override public void onReceive(Context c, Intent in) {
            if (ACTION_REFRESH.equals(in.getAction())) {
                c.getSharedPreferences("pf", Context.MODE_PRIVATE).edit().putLong("wBusy", System.currentTimeMillis()).apply();
                refreshAll(c);
                BgJobService.refreshNow(c);
                return;
            }
            super.onReceive(c, in);
        }
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
        float ts = Math.max(8.5f, Math.min(13f, h * 0.5f));
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
        refreshBtn = !(cols == 2 || h < 80 && w < 160) && w >= 150;
        if (cols == 2 || h < 80 && w < 160) small(c, w, h, kind, j);
        else if (kind == LIFE) life(c, w, h, j, cols); else planet(c, w, h, j, cols);
    }

    static boolean refreshBtn;
    // header: icon + title left, subtitle right (room kept for the refresh button); returns y where content starts
    static float header(Canvas c, float w, String ico, String title, String sub) {
        Paint a = P(INK, 13, true), b = P(0xFF8EA0C4, 10, false);
        float right = w - 12 - (refreshBtn ? 32 : 0), room = right - 12 - 8;
        String t = ico + " " + title;
        float tw = Math.min(a.measureText(t), Math.max(room * 0.62f, room - b.measureText(sub)));   // the name comes first; the subtitle gets what is left
        float sw = room - tw;
        text(c, t, 12, 21, a, tw + 1);
        if (sw > 24) textR(c, sub, right, 20.5f, b, sw);
        if (refreshBtn) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(0x38789ADC);
            c.drawCircle(w - 24, 17, 12, p);
            Paint r = P(0xFFE8EEFC, 15, true); String g = "⟳";
            c.drawText(g, w - 24 - r.measureText(g) / 2, 22.5f, r);
        }
        return 31;
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
        int na = j.optInt("nations"), wars = j.optInt("wars");
        String fl = j.optString("flora"), nw = j.optString("newest");
        b.add(new Object[]{"Life", j.optInt("life") + "%", j.optInt("life") / 100f, GR1, GR2});
        b.add(new Object[]{"Habitable", j.optInt("hab") + "%", j.optInt("hab") / 100f, BL1, BL2});
        b.add(new Object[]{"O₂", j.optInt("o2") + "%", j.optInt("o2") / 42f, CY1, CY2});
        b.add(new Object[]{"Lineages", String.valueOf(j.optInt("lineages")), Math.min(1f, j.optInt("lineages") / 20f), VI1, VI2});
        b.add(new Object[]{"🌿 Plants", fl.length() == 0 ? "none yet" : fl, fl.length() == 0 ? 0f : j.optInt("life") / 100f, GR1, GR2});
        b.add(new Object[]{"🧠 Mind", na > 0 ? "awake" : j.optInt("mind") + "%", na > 0 ? 1f : j.optInt("mind") / 100f, AM1, AM2});
        b.add(new Object[]{"🏛 Nations", String.valueOf(na), Math.min(1f, na / 8f), AM1, AM2});
        b.add(new Object[]{"👥 People", na > 0 ? compact(j.optDouble("pop")) : "0", na > 0 ? 0.5f : 0f, BL1, BL2});
        b.add(new Object[]{"⚔️ Wars", String.valueOf(wars), na > 0 ? Math.min(1f, wars / (float) na) : 0f, RD1, RD2});
        b.add(new Object[]{"🦎 Newest", nw.length() == 0 ? "none yet" : nw, nw.length() == 0 ? 0f : 0.6f, VI1, VI2});
        ArrayList<String> ch = new ArrayList<>();
        ch.add(nw.length() > 0 ? "✨ " + nw : "✨ No animals yet");
        ch.add(na > 0 ? "🏛 " + na + (na == 1 ? " nation" : " nations") : "🧠 No minds yet");
        if (na > 0) ch.add("👥 " + compact(j.optDouble("pop")));
        layout(c, w, h, y, null, b, cols >= 4 ? 2 : 1, ch);
    }

    static void planet(Canvas c, int w, int h, JSONObject j, int cols) {
        String sub = (cols >= 4 ? j.optString("era") + " · " : "") + (busy(c) ? "updating…" : mins(j.optLong("at")));
        float y = header(c, w, "🪐", j.optString("name"), sub);
        ArrayList<Object[]> b = new ArrayList<>();
        b.add(new Object[]{"🌊 Ocean", j.optInt("ocean") + "%", j.optInt("ocean") / 100f, BL1, BL2});
        b.add(new Object[]{"🫁 Oxygen", j.optInt("o2") + "%", j.optInt("o2") / 42f, CY1, CY2});
        b.add(new Object[]{"🫧 CO₂", j.optInt("co2") + "%", j.optInt("co2") / 100f, CY1, CY2});
        b.add(new Object[]{"☁ Clouds", j.optInt("clouds") + "%", j.optInt("clouds") / 100f, GY1, GY2});
        b.add(new Object[]{"🕓 Day", j.optDouble("dayH") + " h", j.optDouble("dayH") <= 0 ? 0f : (float) Math.min(1, j.optDouble("dayH") / 48), AM1, AM2});
        b.add(new Object[]{"🌋 Volcanoes", String.valueOf(j.optInt("vol")), Math.min(1f, j.optInt("vol") / 25f), AM1, AM2});
        b.add(new Object[]{"🌙 Moons", j.optInt("moons") + (j.optBoolean("ring") ? " + ring" : ""), Math.min(1f, j.optInt("moons") / 4f), VI1, VI2});
        b.add(new Object[]{"🏭 Pollution", j.optInt("poll") + "%", j.optInt("poll") / 100f, RD1, RD2});
        ArrayList<String> ch = new ArrayList<>();
        ch.add("☄️ " + j.optInt("impacts") + " impacts");
        if (j.optString("star").length() > 0) ch.add("☀ " + j.optString("star"));
        ch.add("⏳ " + myr(j.optInt("myr")));
        if (j.optString("season").length() > 0) ch.add(j.optString("season"));
        layout(c, w, h, y, j, b, cols >= 4 ? 2 : 1, ch);
    }

    static boolean busyFlag;
    static boolean busy(Canvas c) { return busyFlag; }

    static String compact(double v) {
        if (v >= 1e9) return String.format(Locale.US, "%.1fB", v / 1e9);
        if (v >= 1e6) return String.format(Locale.US, "%.1fM", v / 1e6);
        if (v >= 1e3) return String.format(Locale.US, "%.0fK", v / 1e3);
        return String.valueOf(Math.round(v));
    }

    /** Bars grow to fill the card: an optional full-width temperature bar, then a grid, then a row of chips. */
    static void layout(Canvas c, int w, int h, float y, JSONObject temp, ArrayList<Object[]> bars, int ncol, ArrayList<String> chips) {
        float gap = 4, minBh = 20, maxBh = 36;
        float chipH = (h - y > 96) ? 17 : 0;
        float avail = h - y - 8 - (chipH > 0 ? chipH + 5 : 0);
        int tRows = temp != null ? 1 : 0;
        int gridMax = (bars.size() + ncol - 1) / ncol;
        int total = (int) ((avail + gap) / (minBh + gap));                // how many rows fit at the smallest size
        total = Math.max(1, Math.min(total, tRows + gridMax));
        int gRows = Math.max(0, total - tRows);
        if (temp == null && gRows < 1) gRows = 1;
        int rows = tRows + gRows;
        float bh = Math.max(minBh - 4, Math.min(maxBh, (avail - gap * (rows - 1)) / rows));
        float yy = y;
        if (temp != null) { tempBar(c, 12, yy, w - 24, bh, temp, "🌡 Surface temp", tempBoth(temp)); yy += bh + gap; }
        float bw = (w - 24 - gap * (ncol - 1)) / ncol;
        int n = Math.min(bars.size(), gRows * ncol);
        for (int i = 0; i < n; i++) {
            Object[] b = bars.get(i);
            int col = ncol == 1 ? 0 : i % ncol, row = ncol == 1 ? i : i / ncol;
            bar(c, 12 + col * (bw + gap), yy + row * (bh + gap), bw, bh, (Float) b[2], (String) b[0], (String) b[1], (Integer) b[3], (Integer) b[4]);
        }
        if (chipH > 0) {
            float cx = 12, cy = h - 8 - chipH;
            Paint tp = P(0xFFAEBBDC, 10, false), bg = new Paint(Paint.ANTI_ALIAS_FLAG); bg.setColor(0x29789ADC);
            for (String s : chips) {
                String t = fit(s, tp, w - 24 - 14);
                float cw = tp.measureText(t) + 14;
                if (cx + cw > w - 12) break;
                c.drawRoundRect(new RectF(cx, cy, cx + cw, cy + chipH), chipH / 2, chipH / 2, bg);
                c.drawText(t, cx + 7, cy + chipH / 2 + 3.5f, tp);
                cx += cw + 5;
            }
        }
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
