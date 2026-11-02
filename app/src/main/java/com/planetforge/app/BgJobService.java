package com.planetforge.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Every ~15 minutes: load the game page without any graphics (?bg=1), let it wind the saved planet forward by the time that passed,
 * and post a notification for each new era and for the arrival of intelligent life. Nothing runs in between, so it costs almost nothing.
 */
public class BgJobService extends JobService {
    static final int JOB_ID = 7;
    private WebView web;
    private boolean finished;
    private final Handler h = new Handler(Looper.getMainLooper());

    static void schedule(Context c, boolean on) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (!on) { js.cancel(JOB_ID); return; }
        for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) return;     // already scheduled: don't reset its clock
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, BgJobService.class))
                .setPeriodic(15 * 60 * 1000L).setPersisted(true).build());
    }

    private class Bridge {
        @JavascriptInterface public void bgDone(final String json) {
            h.post(new Runnable() { @Override public void run() { report(json); } });
        }
        @JavascriptInterface public void bgConfig(boolean on) { }
        @JavascriptInterface public void quit() { }
    }

    private JobParameters params;

    @Override public boolean onStartJob(JobParameters p) {
        params = p; finished = false;
        if (MainActivity.visible) return false;                       // the app is open and simulating itself
        try {
            web = new WebView(getApplicationContext());
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true);
            web.addJavascriptInterface(new Bridge(), "AndroidBridge");
            web.setWebViewClient(new WebViewClient());
            web.loadUrl("file:///android_asset/index.html?bg=1");
            h.postDelayed(new Runnable() { @Override public void run() { finish(false); } }, 90000);     // safety: never hang around
            return true;
        } catch (Exception e) { return false; }
    }

    private void report(String json) {
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Notifier.post(getApplicationContext(), o.optString("title"), o.optString("text"));
            }
        } catch (Exception e) { }
        finish(false);
    }

    private void finish(boolean again) {
        if (finished) return; finished = true;
        h.removeCallbacksAndMessages(null);
        if (web != null) { try { web.destroy(); } catch (Exception e) { } web = null; }
        try { jobFinished(params, again); } catch (Exception e) { }
    }

    @Override public boolean onStopJob(JobParameters p) { finished = true; h.removeCallbacksAndMessages(null); if (web != null) { try { web.destroy(); } catch (Exception e) { } web = null; } return false; }
}
