package in.sarkarialert.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

/**
 * In-app browser: opens any site inside the app. Includes a one-tap page
 * translator (Google's translate.goog proxy, the same engine Chrome uses)
 * and an "Ask AI" button that hands the page over to the AI assistant.
 */
public class BrowserActivity extends Activity {

    private WebView web;
    private TextView title, urlView;
    private ProgressBar bar;
    private boolean translating;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_browser);
        web = (WebView) findViewById(R.id.b_web);
        title = (TextView) findViewById(R.id.b_title);
        urlView = (TextView) findViewById(R.id.b_url);
        bar = (ProgressBar) findViewById(R.id.b_bar);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    translating = isTranslatedUrl(url);
                    return false; // keep browsing inside the app
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String u, Bitmap f) {
                super.onPageStarted(v, u, f);
                urlView.setText(host(u));
                bar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                super.onPageFinished(v, u);
                bar.setVisibility(View.GONE);
                title.setText(v.getTitle());
            }

            @Override
            public void onReceivedError(WebView v, int code, String desc, String failingUrl) {
                if (isTranslatedUrl(web.getUrl())) {
                    translating = false;
                    Toast.makeText(BrowserActivity.this,
                            R.string.browser_translate_fail, Toast.LENGTH_LONG).show();
                    return;
                }
                errorPage();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                bar.setProgress(p);
                if (p >= 100) bar.setVisibility(View.GONE);
            }
        });

        findViewById(R.id.b_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goBack(); }
        });
        findViewById(R.id.b_translate).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleTranslate(); }
        });
        findViewById(R.id.b_external).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String u = web.getUrl();
                if (u == null) return;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(originalUrl(u))));
                } catch (Exception e) {
                    Toast.makeText(BrowserActivity.this,
                            R.string.error_no_url, Toast.LENGTH_SHORT).show();
                }
            }
        });
        findViewById(R.id.b_ai).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { askAi(); }
        });

        String url = getIntent().getStringExtra("url");
        if (url == null || !url.startsWith("http")) {
            finish();
            return;
        }
        web.loadUrl(url);
    }

    private void goBack() {
        if (web.canGoBack()) web.goBack();
        else finish();
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    // ---- page translation via Google's translate.goog proxy (same as Chrome) ----

    private void toggleTranslate() {
        String u = web.getUrl();
        if (u == null) return;
        if (isTranslatedUrl(u)) {
            web.loadUrl(originalUrl(u));
        } else {
            web.loadUrl(translatedUrl(u));
            Toast.makeText(this, R.string.browser_translating, Toast.LENGTH_SHORT).show();
        }
    }

    static boolean isTranslatedUrl(String u) {
        try {
            String h = Uri.parse(u).getHost();
            return h != null && h.endsWith(".translate.goog");
        } catch (Exception e) {
            return false;
        }
    }

    static String translatedUrl(String u) {
        Uri x = Uri.parse(u);
        String host = (x.getHost() == null ? "" : x.getHost())
                .replace("-", "--").replace(".", "-");
        Uri.Builder b = new Uri.Builder()
                .scheme("https")
                .encodedAuthority(host + ".translate.goog")
                .encodedPath(x.getPath() == null || x.getPath().length() == 0 ? "/" : x.getPath());
        if (x.getEncodedQuery() != null) b.encodedQuery(x.getEncodedQuery());
        return b.appendQueryParameter("_x_tr_sl", "auto")
                .appendQueryParameter("_x_tr_tl", "hi")
                .appendQueryParameter("_x_tr_hl", "en")
                .build().toString();
    }

    static String originalUrl(String u) {
        if (!isTranslatedUrl(u)) return u;
        Uri x = Uri.parse(u);
        String h = x.getHost();
        h = h.substring(0, h.length() - ".translate.goog".length());
        // undo the hyphen escaping: "--" was a literal "-", "-" was a dot
        h = h.replace("--", "\u0001").replace("-", ".").replace("\u0001", "-");
        StringBuilder q = new StringBuilder();
        for (String name : x.getQueryParameterNames()) {
            if (name.startsWith("_x_tr_")) continue;
            if (q.length() > 0) q.append("&");
            q.append(Uri.encode(name)).append("=")
             .append(Uri.encode(x.getQueryParameter(name)));
        }
        Uri.Builder b = new Uri.Builder().scheme("https").encodedAuthority(h)
                .encodedPath(x.getPath() == null ? "/" : x.getPath());
        if (q.length() > 0) b.encodedQuery(q.toString());
        return b.build().toString();
    }

    // ---- hand the page to the AI assistant ----

    private void askAi() {
        web.evaluateJavascript(
                "(function(){try{return document.body.innerText.substring(0,700)}catch(e){return ''}})()",
                new ValueCallback<String>() {
                    @Override public void onReceiveValue(String v) {
                        String snip = unquote(v);
                        Intent i = new Intent(BrowserActivity.this, AiChatActivity.class);
                        i.putExtra("ctx_url", originalUrl(web.getUrl() == null ? "" : web.getUrl()));
                        i.putExtra("ctx_title", web.getTitle());
                        i.putExtra("ctx_snippet", snip);
                        i.putExtra("prefill", getString(R.string.ai_prefill_page));
                        startActivity(i);
                    }
                });
    }

    private static String unquote(String js) {
        try {
            if (js == null) return "";
            String t = js.trim();
            if (t.startsWith("\"")) {
                return new JSONArray("[" + t + "]").getString(0);
            }
            return t;
        } catch (Exception e) {
            return js == null ? "" : js;
        }
    }

    private void errorPage() {
        String html = "<html><body style='background:#F2F4F8;font-family:sans-serif;"
                + "display:flex;align-items:center;justify-content:center;height:95%;margin:0'>"
                + "<div style='text-align:center;padding:24px'>"
                + "<div style='font-size:42px'>&#9888;</div>"
                + "<h2 style='color:#1B2430;margin:8px 0'>Page didn't load</h2>"
                + "<p style='color:#63707F;font-size:14px'>Check your internet, then try again.<br>"
                + "Some sites also block translation &mdash; tap the &#2306; button again to go back.</p>"
                + "<button onclick='location.reload()' style='margin-top:14px;background:#F4772E;"
                + "color:#fff;border:0;border-radius:22px;padding:10px 26px;font-size:14px'>Try again</button>"
                + "</div></body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    private static String host(String u) {
        try {
            return Uri.parse(u).getHost() == null ? u : Uri.parse(u).getHost();
        } catch (Exception e) {
            return u;
        }
    }
}
