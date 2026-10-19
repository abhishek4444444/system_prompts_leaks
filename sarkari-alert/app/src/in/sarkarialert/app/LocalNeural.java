package in.sarkarialert.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;

import org.json.JSONObject;

/**
 * Local neural AI (beta): runs a small quantized instruction model
 * (SmolLM2-135M) fully ON-DEVICE inside a WebView using transformers.js
 * (WebGPU when available, WASM otherwise). First use downloads the model
 * (~100 MB) straight from HuggingFace; afterwards it is cached and loads
 * instantly. Streaming tokens arrive over the JS bridge.
 */
final class LocalNeural {

    interface Cb {
        void onStatus(String msg);   // one-line status (loading/progress/ready)
        void onToken(String soFar);  // progressive answer
        void onDone(String full);
        void onError(String msg);
    }

    private WebView web;
    private boolean booting, ready, failed;
    private String pendingSystem, pendingQuestion;
    private Cb cb;
    private final Handler ui = new Handler(Looper.getMainLooper());

    boolean isReady() { return ready; }
    boolean hasFailed() { return failed; }

    @SuppressLint("SetJavaScriptEnabled")
    void boot(Context c, android.view.ViewGroup host, Cb callback) {
        cb = callback;
        if (booting || ready) return;
        booting = true;
        web = new WebView(c);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setBackgroundColor(0x00000000);
        host.addView(web, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1));
        web.loadUrl("file:///android_asset/local_ai.html");
    }

    /** ask once boot reported ready (or queue while still loading) */
    void ask(String system, String question) {
        if (ready) {
            run(system, question);
        } else {
            pendingSystem = system;
            pendingQuestion = question;
        }
    }

    private void run(String system, String question) {
        final String sys = system == null ? "" : system;
        final String q = question == null ? "" : question;
        ui.post(new Runnable() {
            @Override public void run() {
                web.evaluateJavascript("window.run(" + jq(sys) + "," + jq(q) + ")", null);
            }
        });
    }

    private static String jq(String s) {
        return JSONObject.quote(s == null ? "" : s);
    }

    private class Bridge {

        @JavascriptInterface
        public void info(String what) {
            if ("wasm".equals(what)) {
                post("WebGPU unavailable — running on WASM (a bit slower).");
            }
        }

        @JavascriptInterface
        public void progress(final double pct) {
            ui.post(new Runnable() {
                @Override public void run() {
                    if (cb != null) cb.onStatus("Downloading model… " + (int) pct + "%");
                }
            });
        }

        @JavascriptInterface
        public void ready() {
            ui.post(new Runnable() {
                @Override public void run() {
                    ready = true;
                    booting = false;
                    if (cb != null) cb.onStatus(null); // clear progress line
                    if (pendingQuestion != null) {
                        LocalNeural.this.run(pendingSystem, pendingQuestion);
                        pendingQuestion = null;
                    }
                }
            });
        }

        @JavascriptInterface
        public void token(final String t) {
            ui.post(new Runnable() {
                @Override public void run() {
                    if (cb != null) cb.onToken(t);
                }
            });
        }

        @JavascriptInterface
        public void done() {
            ui.post(new Runnable() {
                @Override public void run() {
                    if (cb != null) cb.onDone(null);
                }
            });
        }

        @JavascriptInterface
        public void error(final String e) {
            ui.post(new Runnable() {
                @Override public void run() {
                    failed = true;
                    booting = false;
                    if (cb != null) cb.onError(e);
                }
            });
        }

        @JavascriptInterface
        public void booted() { /* module evaluated; pipeline loading started */ }

        private void post(final String msg) {
            ui.post(new Runnable() {
                @Override public void run() {
                    if (cb != null) cb.onStatus(msg);
                }
            });
        }
    }
}
