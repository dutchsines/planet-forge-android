package com.planetforge.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        /** Wallpaper smoothness in frames per second (0 = as fast as the screen refreshes). */
        @JavascriptInterface
        public void wallFps(int fps) { getSharedPreferences("pf", MODE_PRIVATE).edit().putInt("wallFps", fps).apply(); }

        /** How much news to send: 0 off, 1 big moments only, 2 everything (rate limited). */
        @JavascriptInterface
        public void notifyLevel(int level) { getSharedPreferences("pf", MODE_PRIVATE).edit().putInt("nLevel", level).apply(); }

        /** The live wallpaper: the page records pictures of the planet turning and hands them over one by one. */
        @JavascriptInterface
        public void wallFrame(int i, int n, String dataUrl) { Wall.frame(getApplicationContext(), i, n, dataUrl); }

        @JavascriptInterface
        public void wallDone(int n) { Wall.done(getApplicationContext(), n); setWallpaper(); }

        /** How often the background job sends a photo of the world, in hours (0 = never). */
        @JavascriptInterface
        public void photoConfig(int hours) { getSharedPreferences("pf", MODE_PRIVATE).edit().putInt("photoH", hours).apply(); }

        /** Asks Android to let the app run in the background without being put to sleep (battery optimisation exemption). */
        @JavascriptInterface
        public void requestBackground() { runOnUiThread(new Runnable() { @Override public void run() { askBackground(true); } }); }

        /** In-game updater: the page hands over a newer index.html; it is kept in app storage and used while it is newer than the bundled page. */
        @JavascriptInterface
        public boolean saveUpdate(String html, double build) {
            try {
                if (html == null || html.length() < 200000 || html.length() > 40000000) return false;
                long b = (long) build; if (b <= bundledBuild() || buildIn(html) != b) return false;
                File dir = new File(getFilesDir(), "update"); dir.mkdirs();
                File tmp = new File(dir, "index.html.tmp"), out = new File(dir, "index.html");
                FileOutputStream f = new FileOutputStream(tmp); f.write(withShim(html).getBytes("UTF-8")); f.close();
                if (out.exists()) out.delete();
                return tmp.renameTo(out);
            } catch (Exception e) { return false; }
        }

        @JavascriptInterface
        public void applyUpdate() {
            runOnUiThread(new Runnable() { @Override public void run() { loadGame(); } });
        }

        @JavascriptInterface
        public void quit() {
            runOnUiThread(new Runnable() {
                @Override public void run() { finishAndRemoveTask(); }
            });
        }
    }

    /** The page the background job should open: a downloaded update when it is newer than the bundled one. */
    static String pageUrl(Context c) {
        try {
            File f = new File(new File(c.getFilesDir(), "update"), "index.html");
            if (f.exists()) {
                InputStream in = new java.io.FileInputStream(f); ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                in.close();
                InputStream ia = c.getAssets().open("index.html"); ByteArrayOutputStream oa = new ByteArrayOutputStream();
                while ((n = ia.read(buf)) > 0) oa.write(buf, 0, n);
                ia.close();
                if (buildIn(o.toString("UTF-8")) > buildIn(oa.toString("UTF-8"))) return "file://" + f.getAbsolutePath();
            }
        } catch (Exception e) { }
        return "file:///android_asset/index.html";
    }

    /** Offers (once, or on request) the battery-optimisation exemption so the world keeps being made while the phone is idle. */
    private void askBackground(final boolean force) {
        try {
            if (Build.VERSION.SDK_INT < 23) return;
            final String pkg = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(pkg)) { if (force) Toast.makeText(this, "Already allowed to run in the background", Toast.LENGTH_SHORT).show(); return; }
            final android.content.SharedPreferences sp = getSharedPreferences("pf", MODE_PRIVATE);
            if (!force && sp.getBoolean("bgAsked", false)) return;
            sp.edit().putBoolean("bgAsked", true).apply();
            new AlertDialog.Builder(this).setTitle("Keep your world growing?")
                .setMessage("Planet Forge can keep making your world in the background and send you news and photos of it. Android may put it to sleep to save battery. Allow it to keep running?")
                .setPositiveButton("Allow", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) {
                    try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + pkg))); }
                    catch (Exception e) { try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception e2) { } } } })
                .setNegativeButton("Not now", null).show();
        } catch (Exception e) { }
    }

    private static final String SHIM = "<script>window.pfApp={isApp:true,android:true,wallpaper:function(on){try{setWallpaper(!!on)}catch(e){}},recording:function(){},quit:function(){try{AndroidBridge.quit()}catch(e){window.close()}},saveUpdate:function(t,b){return AndroidBridge.saveUpdate(t,b)},applyUpdate:function(){AndroidBridge.applyUpdate()}};</script>";

    private static String withShim(String html) { return html.contains("window.pfApp=") ? html : html.replace("<head>", "<head>" + SHIM); }

    private static long buildIn(String html) {
        Matcher m = Pattern.compile("PF_BUILD\\s*=\\s*(\\d+)").matcher(html);
        return m.find() ? Long.parseLong(m.group(1)) : 0;
    }

    private long bundledBuild() {
        try {
            InputStream in = getAssets().open("index.html"); ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            in.close(); return buildIn(o.toString("UTF-8"));
        } catch (Exception e) { return 0; }
    }

    /** Opens the bundled page, or a downloaded update if that is newer (a fresh APK with a newer page wins again). */
    private void loadGame() {
        try {
            File f = new File(new File(getFilesDir(), "update"), "index.html");
            if (f.exists()) {
                InputStream in = new java.io.FileInputStream(f); ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                in.close();
                if (buildIn(o.toString("UTF-8")) > bundledBuild()) { web.loadUrl("file://" + f.getAbsolutePath()); return; }
            }
        } catch (Exception e) { }
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            if (Build.VERSION.SDK_INT >= 23) lp.preferredRefreshRate = 60f;     // the game draws at 30 fps; a 120 Hz screen only makes the compositor work harder
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
        loadGame();
        web.postDelayed(new Runnable() { @Override public void run() { askBackground(false); } }, 8000);
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
