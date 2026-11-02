package com.planetforge.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Live wallpaper: runs the game page in an off-screen WebView (wallpaper mode, lightest graphics)
 * and copies its picture onto the wallpaper surface about 15 times a second.
 */
public class PlanetWallpaperService extends WallpaperService {
    private static final String URL =
            "file:///android_asset/index.html?wallpaper=1&lite=1&mobile=1&notitle=1&mins=30&fps=15";
    private static final int FRAME_MS = 66;

    @Override
    public Engine onCreateEngine() { return new PlanetEngine(); }

    private class PlanetEngine extends Engine {
        private WebView web;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private boolean visible = false;
        private int w = 0, h = 0;

        private final Runnable drawer = new Runnable() {
            @Override public void run() {
                if (!visible || web == null) return;
                SurfaceHolder holder = getSurfaceHolder();
                Canvas c = null;
                try {
                    c = holder.lockCanvas();
                    if (c != null) { c.drawColor(Color.BLACK); web.draw(c); }
                } catch (Exception ignored) {
                } finally {
                    if (c != null) { try { holder.unlockCanvasAndPost(c); } catch (Exception ignored) {} }
                }
                handler.postDelayed(this, FRAME_MS);
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            web = new WebView(PlanetWallpaperService.this);
            web.setBackgroundColor(Color.BLACK);
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setAllowFileAccess(true);
            s.setMediaPlaybackRequiresUserGesture(true);   // no sound behind the home screen
            web.setWebViewClient(new WebViewClient());
            web.loadUrl(URL);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            w = width; h = height;
            web.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            web.layout(0, 0, w, h);
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visible = v;
            handler.removeCallbacks(drawer);
            if (web == null) return;
            if (v) { web.onResume(); handler.post(drawer); } else { web.onPause(); }
        }

        @Override
        public void onDestroy() {
            visible = false;
            handler.removeCallbacks(drawer);
            if (web != null) { web.destroy(); web = null; }
            super.onDestroy();
        }
    }
}
