package com.planetforge.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import java.io.File;
import java.util.Random;

/**
 * Live wallpaper. It does NOT run the game (a second WebGL page in the same process starved the app and drew black).
 * It plays back pictures of your planet turning (recorded by the game: More menu -> Set as live wallpaper, and refreshed by the background job),
 * cross-fading between them. Until a set exists it draws a simple planet and stars, so it is never black.
 */
public class PlanetWallpaperService extends WallpaperService {
    private static final int STEP_MS = 2500;      // time per picture; 36 pictures = one turn every 90 seconds
    private static final int FRAME_MS = 66;       // ~15 draws per second

    @Override public Engine onCreateEngine() { return new PlanetEngine(); }

    private class PlanetEngine extends Engine {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix m = new Matrix();
        private boolean visible = false;
        private int w = 0, h = 0, n = 0, loadedAt = -1, curIdx = -1;
        private Bitmap cur, nxt;
        private long t0 = SystemClock.uptimeMillis(), lastCheck = 0;
        private float[] sx, sy, sr;

        private final Runnable drawer = new Runnable() {
            @Override public void run() {
                if (!visible) return;
                SurfaceHolder holder = getSurfaceHolder();
                Canvas c = null;
                try {
                    c = holder.lockCanvas();
                    if (c != null) { if (w == 0) { w = c.getWidth(); h = c.getHeight(); } draw(c); }
                } catch (Exception ignored) {
                } finally {
                    if (c != null) { try { holder.unlockCanvasAndPost(c); } catch (Exception ignored) { } }
                }
                handler.postDelayed(this, FRAME_MS);
            }
        };

        private void refresh() {                                   // is there a (new) set of pictures?
            long now = SystemClock.uptimeMillis(); if (now - lastCheck < 30000 && n > 0) return; lastCheck = now;
            int count = getSharedPreferences("pf", MODE_PRIVATE).getInt("wallN", 0), at = (int) (getSharedPreferences("pf", MODE_PRIVATE).getLong("wallAt", 0) / 1000L);
            if (count != n || at != loadedAt) { n = count; loadedAt = at; curIdx = -1; if (cur != null) cur.recycle(); if (nxt != null) nxt.recycle(); cur = nxt = null; }
        }

        private Bitmap load(int i) {
            try { File f = new File(Wall.dir(getApplicationContext()), Wall.name("f", i)); return f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null; } catch (Throwable t) { return null; }
        }

        private void cover(Canvas c, Bitmap b, int alpha) {
            if (b == null) return;
            float s = Math.max((float) w / b.getWidth(), (float) h / b.getHeight());
            m.reset(); m.postScale(s, s); m.postTranslate((w - b.getWidth() * s) / 2f, (h - b.getHeight() * s) / 2f);
            paint.setAlpha(alpha); c.drawBitmap(b, m, paint);
        }

        private void draw(Canvas c) {
            refresh();
            if (n < 2) { drawFallback(c); return; }
            float pos = ((SystemClock.uptimeMillis() - t0) / (float) STEP_MS) % n;
            int i0 = (int) pos, i1 = (i0 + 1) % n; float a = pos - i0;
            if (i0 != curIdx) {                                    // moved on: the next picture becomes the current one
                Bitmap keep = (curIdx >= 0 && i0 == (curIdx + 1) % n) ? nxt : null;
                if (keep == null) { if (cur != null) cur.recycle(); cur = load(i0); } else { if (cur != null) cur.recycle(); cur = keep; }
                curIdx = i0; nxt = load(i1);
            }
            if (cur == null) { drawFallback(c); return; }
            c.drawColor(Color.BLACK);
            cover(c, cur, 255);
            if (nxt != null) cover(c, nxt, (int) (a * 255));
        }

        private void drawFallback(Canvas c) {                      // stars and a lit planet: nothing is ever black
            if (sx == null) { Random r = new Random(7); sx = new float[90]; sy = new float[90]; sr = new float[90]; for (int i = 0; i < 90; i++) { sx[i] = r.nextFloat(); sy[i] = r.nextFloat(); sr[i] = 0.6f + r.nextFloat() * 1.6f; } }
            c.drawColor(Color.rgb(3, 5, 12));
            fill.setShader(null); fill.setColor(Color.WHITE);
            float tw = (SystemClock.uptimeMillis() % 4000) / 4000f;
            for (int i = 0; i < 90; i++) { fill.setAlpha((int) (110 + 90 * Math.sin((tw + sx[i]) * 6.283f))); c.drawCircle(sx[i] * w, sy[i] * h, sr[i], fill); }
            float cx = w * 0.5f, cy = h * 0.46f, r = Math.min(w, h) * 0.30f;
            fill.setAlpha(255);
            fill.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.35f, r * 1.5f, new int[]{Color.rgb(120, 190, 235), Color.rgb(30, 80, 150), Color.rgb(4, 10, 26)}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, fill);
            fill.setShader(new RadialGradient(cx, cy, r * 1.18f, new int[]{Color.argb(0, 80, 160, 255), Color.argb(0, 80, 160, 255), Color.argb(90, 90, 170, 255), Color.argb(0, 90, 170, 255)}, new float[]{0f, 0.84f, 0.93f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r * 1.18f, fill);
            fill.setShader(null);
        }

        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) { super.onSurfaceChanged(holder, format, width, height); w = width; h = height; }

        @Override public void onVisibilityChanged(boolean v) {
            visible = v; handler.removeCallbacks(drawer);
            if (v) { lastCheck = 0; handler.post(drawer); }
        }

        @Override public void onDestroy() {
            visible = false; handler.removeCallbacks(drawer);
            if (cur != null) cur.recycle(); if (nxt != null) nxt.recycle(); cur = nxt = null;
            super.onDestroy();
        }
    }
}
