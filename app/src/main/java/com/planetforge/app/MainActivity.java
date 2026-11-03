package com.planetforge.app;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/** Planet Forge: the whole game is one web page (assets/index.html) shown full-screen in a WebView. */
public class MainActivity extends Activity {
    private WebView web;
    static volatile boolean visible = false;          // the background job stays out of the way while the app is on screen
    private long lastBack = 0;

    /** Methods the page can call as window.AndroidBridge.* (the page's own pfApp shim uses quit()). */
    private class Bridge {
        @JavascriptInterface
        public void setWallpaper() {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    try {
                        Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
                        i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                new ComponentName(MainActivity.this, PlanetWallpaperService.class));
                        startActivity(i);
                    } catch (Exception e) {
                        try { startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)); }
                        catch (Exception e2) { Toast.makeText(MainActivity.this, "Open Wallpaper settings and pick Planet Forge", Toast.LENGTH_LONG).show(); }
                    }
                }
            });
        }

        /** The page tells us whether the 15-minute background check (new eras, intelligent life) should be running. */
        @JavascriptInterface
        public void bgConfig(boolean on) { BgJobService.schedule(getApplicationContext(), on); }

        @JavascriptInterface
        public void bgDone(String json) { }

        @JavascriptInterface
        public void quit() {
            runOnUiThread(new Runnable() {
                @Override public void run() { finishAndRemoveTask(); }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        Notifier.channel(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        BgJobService.schedule(this, true);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                    // keeps the player's settings and saved worlds between runs
        s.setMediaPlaybackRequiresUserGesture(false);    // ambient sound
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onBackPressed() {
        // Back acts like Esc inside the game: closes panels, otherwise pauses and opens the menu (Resume / New world / Load world / Settings / Quit)
        web.evaluateJavascript("window.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))", null);
    }

    @Override protected void onPause()  {
        visible = false;
        web.evaluateJavascript("window.pfSaveNow&&pfSaveNow()", null);        // autosave right now, so reopening gives back this very planet
        super.onPause();  web.onPause();
    }
    @Override protected void onResume() {
        super.onResume(); web.onResume(); visible = true;
        web.evaluateJavascript("window.pfResume&&pfResume()", null);          // catch the planet up with the time that passed
    }
    @Override protected void onDestroy() { if (web != null) { web.destroy(); } super.onDestroy(); }
}
