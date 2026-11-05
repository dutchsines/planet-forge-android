package com.planetforge.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Every ~15 minutes: load the game page without any graphics (?bg=1), let it wind the saved planet forward by the time that passed,
 * and post playful notifications about what happened (eras, minds, wars, disasters, super minds).
 * Every few hours (your choice) it then opens the page once with graphics, takes a picture of the world, and posts it with a few facts.
 */
public class BgJobService extends JobService {
    static final int JOB_ID = 8, QUICK_ID = 9;               // QUICK_ID: run once, right now (the widget refresh button)
    private boolean quick;
    private WebView web;
    private boolean finished;
    private JSONObject facts;
    private boolean wallRun;
    private final Handler h = new Handler(Looper.getMainLooper());

    static void schedule(Context c, boolean on) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        js.cancel(7);                                                  // the first version of the job (it ran even while the phone was in use)
        if (!on) { js.cancel(JOB_ID); return; }
        for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) return;     // already scheduled: don't reset its clock
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, BgJobService.class))
                .setPeriodic(30 * 60 * 1000L).setRequiresBatteryNotLow(true).setPersisted(true).build());
    }

    /** Runs the catch-up once, straight away (even with the screen on). It never takes a photo. */
    static void refreshNow(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        js.schedule(new JobInfo.Builder(QUICK_ID, new ComponentName(c, BgJobService.class)).setMinimumLatency(0).setOverrideDeadline(1000).build());
    }

    private class Bridge {
        @JavascriptInterface public void bgDone(final String json) { h.post(new Runnable() { @Override public void run() { report(json); } }); }
        @JavascriptInterface public void photoReady(final String dataUrl, final String json) { h.post(new Runnable() { @Override public void run() { photo(dataUrl, json); } }); }
        @JavascriptInterface public void wallFrame(int i, int n, String dataUrl) { Wall.frame(getApplicationContext(), i, n, dataUrl); }
        @JavascriptInterface public void wallDone(int n) { Wall.done(getApplicationContext(), n); }
        @JavascriptInterface public void photoEnd() { h.post(new Runnable() { @Override public void run() { finish(false); } }); }
        @JavascriptInterface public void bgConfig(boolean on) { }
        @JavascriptInterface public void photoConfig(int hours) { }
        @JavascriptInterface public void widgetStats(final String json) { Widgets.save(getApplicationContext(), json); }
        @JavascriptInterface public void quit() { }
    }

    private JobParameters params;

    @Override public boolean onStartJob(JobParameters p) {
        params = p; finished = false; facts = null; quick = p.getJobId() == QUICK_ID;
        if (MainActivity.visible) return false;                       // the app is open and simulating itself
        if (!quick) try { android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE); if (pm != null && pm.isInteractive()) return false; } catch (Exception e) { }     // never while the phone is being used: it would make it lag
        try {
            web = new WebView(getApplicationContext());
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true);
            web.addJavascriptInterface(new Bridge(), "AndroidBridge");
            web.setWebViewClient(new WebViewClient());
            web.loadUrl(MainActivity.pageUrl(getApplicationContext()) + "?bg=1");
            h.postDelayed(new Runnable() { @Override public void run() { finish(false); } }, 90000);     // safety: never hang around
            return true;
        } catch (Exception e) { return false; }
    }

    private void report(String json) {
        boolean big = false;
        try {
            JSONArray a; Object o = new org.json.JSONTokener(json).nextValue();
            if (o instanceof JSONObject) { a = ((JSONObject) o).optJSONArray("notes"); facts = ((JSONObject) o).optJSONObject("facts"); } else a = (JSONArray) o;
            if (a != null) { for (int i = 0; i < a.length(); i++) big |= a.getJSONObject(i).optBoolean("big", false); Notifier.news(getApplicationContext(), a); }
        } catch (Exception e) { }
        SharedPreferences sp = getSharedPreferences("pf", MODE_PRIVATE);
        int hours = sp.getInt("photoH", 6); long last = sp.getLong("lastPhoto", 0), now = System.currentTimeMillis();
        boolean due = !quick && hours > 0 && facts != null && (now - last >= hours * 3600000L || (big && now - last >= 30 * 60000L));
        if (due) startPhoto(); else finish(false);
    }

    /** Opens the page once with graphics, off screen, so it can take a picture of the world. */
    private void startPhoto() {
        try {
            h.removeCallbacksAndMessages(null);
            final int w = 720, hh = 1560;
            wallRun = getSharedPreferences("pf", MODE_PRIVATE).getBoolean("wallOn", false);
            web.stopLoading();
            web.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hh, View.MeasureSpec.EXACTLY));
            web.layout(0, 0, w, hh);
            web.onResume(); web.resumeTimers();
            web.loadUrl(MainActivity.pageUrl(getApplicationContext()) + "?photo=1&notitle=1&mobile=1" + (wallRun ? "&wall=1" : ""));
            h.postDelayed(new Runnable() { @Override public void run() { photoFallback(); } }, 80000);       // graphics did not start in time: send the facts without a picture
        } catch (Exception e) { photoFallback(); }
    }

    private void photoFallback() {
        try { if (facts != null) Notifier.post(getApplicationContext(), facts.optString("title"), facts.optString("text")); } catch (Exception e) { }
        getSharedPreferences("pf", MODE_PRIVATE).edit().putLong("lastPhoto", System.currentTimeMillis()).apply();
        finish(false);
    }

    private void photo(String dataUrl, String json) {
        if (finished) return;
        try {
            JSONObject f = new JSONObject(json);
            byte[] jpg = Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), Base64.DEFAULT);
            File out = new File(getCacheDir(), "world_photo.jpg");
            FileOutputStream os = new FileOutputStream(out); os.write(jpg); os.close();
            Notifier.photo(getApplicationContext(), f.optString("title"), f.optString("text"), out);
            getSharedPreferences("pf", MODE_PRIVATE).edit().putLong("lastPhoto", System.currentTimeMillis()).apply();
            if (wallRun) { h.removeCallbacksAndMessages(null); h.postDelayed(new Runnable() { @Override public void run() { finish(false); } }, 170000); return; }      // keep going: the page now records the turning planet for the wallpaper (photoEnd finishes the job)
            finish(false);
        } catch (Exception e) { photoFallback(); }
    }

    private void finish(boolean again) {
        if (finished) return; finished = true;
        h.removeCallbacksAndMessages(null);
        if (web != null) { try { web.destroy(); } catch (Exception e) { } web = null; }
        try { jobFinished(params, again); } catch (Exception e) { }
    }

    @Override public boolean onStopJob(JobParameters p) { finished = true; h.removeCallbacksAndMessages(null); if (web != null) { try { web.destroy(); } catch (Exception e) { } web = null; } return false; }
}
