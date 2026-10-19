package in.sarkarialert.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

/**
 * In-app browser with:
 *  - one-tap page translation (translate.goog, Chrome's own engine)
 *  - "Ask AI" handoff with the page as context
 *  - SiteGuard: if the site is down / blocked (5xx, 403, 429, network error)
 *    the page is auto-reloaded through a readable mirror, then through the
 *    Wayback Machine — so result-day crashes never stop the user
 *  - captcha-proof: first-party + third-party cookies, DOM storage, and
 *    multi-window support (reCAPTCHA and target=_blank links work)
 *  - desktop-site toggle in the overflow menu
 *  - share that always sends the ORIGINAL site URL
 */
public class BrowserActivity extends Activity {

    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private WebView web;
    private TextView title, urlView;
    private ProgressBar bar;
    private View rescueBar;
    private boolean desktop;
    private boolean inMirror;        // currently viewing the SiteGuard mirror
    private String rescueTriedFor;   // URL already auto-rescued once (loop guard)

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_browser);
        web = (WebView) findViewById(R.id.b_web);
        title = (TextView) findViewById(R.id.b_title);
        urlView = (TextView) findViewById(R.id.b_url);
        bar = (ProgressBar) findViewById(R.id.b_bar);
        rescueBar = findViewById(R.id.b_rescue);
        desktop = Prefs.desktopMode(this);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        // captcha + login flows need cookies (incl. third-party)
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);
        applyUa();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    if (inMirror && !url.contains("r.jina.ai")) {
                        leaveMirror(); // user navigated out of the mirror
                    }
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
                urlView.setText(host(displayUrl(u)));
                bar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                super.onPageFinished(v, u);
                bar.setVisibility(View.GONE);
                title.setText(v.getTitle());
                CookieManager.getInstance().flush();
            }

            /** network-level failure: DNS, timeout, connection refused… */
            @Override
            public void onReceivedError(WebView v, WebResourceRequest req,
                                        WebResourceError err) {
                if (req.isForMainFrame()) {
                    runRescueLadder(v.getUrl());
                }
            }

            /** server answered but unhappy: 403/429 bot-blocks, 5xx crashes */
            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest req,
                                            WebResourceResponse resp) {
                if (req.isForMainFrame()) {
                    int code = resp.getStatusCode();
                    if (code >= 500 || code == 403 || code == 429) {
                        runRescueLadder(v.getUrl());
                    }
                }
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
        findViewById(R.id.b_ai).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { askAi(); }
        });
        findViewById(R.id.b_more).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(v); }
        });
        findViewById(R.id.b_rescue_retry).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String target = originalUrl(web.getUrl() == null ? "" : web.getUrl());
                leaveMirror();
                rescueBar.setVisibility(View.GONE);
                web.loadUrl(target);
            }
        });

        String url = getIntent().getStringExtra("url");
        if (url == null || !url.startsWith("http")) {
            finish();
            return;
        }
        web.loadUrl(url);
    }

    // ---- SiteGuard: direct -> mirror -> archive ----

    private void runRescueLadder(String failedUrl) {
        if (failedUrl == null || failedUrl.startsWith("data:")) return;
        if (inMirror && failedUrl.contains("web.archive.org")) {
            errorPage(); // archive failed too — give up gracefully
            return;
        }
        if (inMirror && failedUrl.contains("r.jina.ai")) {
            // mirror failed — step up to the Wayback Machine
            web.loadUrl(SiteGuard.archive(originalUrl(failedUrl)));
            return;
        }
        if (failedUrl.equals(rescueTriedFor)) {
            errorPage(); // already rescued this URL once; no loops
            return;
        }
        rescueTriedFor = failedUrl;
        inMirror = true;
        rescueBar.setVisibility(View.VISIBLE);
        Toast.makeText(this, R.string.browser_rescue_toast, Toast.LENGTH_LONG).show();
        web.loadUrl(SiteGuard.mirror(failedUrl));
    }

    private void leaveMirror() {
        inMirror = false;
        rescueBar.setVisibility(View.GONE);
    }

    private void goBack() {
        if (web.canGoBack()) web.goBack();
        else finish();
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    // ---- overflow menu: desktop site / share / open external ----

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(getString(
                desktop ? R.string.browser_menu_desktop_on : R.string.browser_menu_desktop_off)
                .toString());
        menu.getMenu().add(getString(R.string.browser_menu_share));
        menu.getMenu().add(getString(R.string.browser_menu_external));
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(android.view.MenuItem item) {
                String t = String.valueOf(item.getTitle());
                if (t.startsWith(getString(R.string.browser_menu_desktop_prefix))) {
                    desktop = !desktop;
                    Prefs.setDesktopMode(BrowserActivity.this, desktop);
                    Toast.makeText(BrowserActivity.this, desktop
                            ? R.string.browser_menu_desktop_on : R.string.browser_menu_desktop_off,
                            Toast.LENGTH_SHORT).show();
                    applyUa();
                    reloadOriginal();
                } else if (t.equals(getString(R.string.browser_menu_share))) {
                    sharePage();
                } else {
                    openExternal();
                }
                return true;
            }
        });
        menu.show();
    }

    private void applyUa() {
        web.getSettings().setUserAgentString(desktop ? DESKTOP_UA : "");
    }

    private void reloadOriginal() {
        String u = web.getUrl();
        if (u == null) return;
        if (u.contains("r.jina.ai") || u.contains("web.archive.org")) {
            leaveMirror();
            u = originalUrl(u);
        }
        web.loadUrl(u);
    }

    // ---- page translation via Google's translate.goog proxy (same as Chrome) ----

    private void toggleTranslate() {
        String u = web.getUrl();
        if (u == null) return;
        if (u.contains("r.jina.ai") || u.contains("web.archive.org")) {
            // translating a mirror double-proxies; go back to the real page first
            u = originalUrl(u);
            leaveMirror();
            rescueBar.setVisibility(View.GONE);
        }
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
        if (u == null) return "";
        if (u.contains("r.jina.ai/")) {
            int i = u.indexOf("r.jina.ai/");
            return u.substring(i + "r.jina.ai/".length());
        }
        if (u.contains("web.archive.org/web/")) {
            try {
                Uri x = Uri.parse(u);
                String p = x.getPath() == null ? "" : x.getPath();
                // /web/<timestamp|id>/<original url>
                int first = p.indexOf('/', 1);
                if (first > 0) {
                    String rest = p.substring(first + 1);
                    if (rest.startsWith("http")) {
                        String q = x.getEncodedQuery();
                        return q == null ? rest : rest + "?" + q;
                    }
                }
            } catch (Exception ignored) { }
            return u;
        }
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

    /** the URL to show/share: always the site's own address, never a mirror */
    private String displayUrl(String u) {
        return originalUrl(u);
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

    private void sharePage() {
        String u = originalUrl(web.getUrl() == null ? "" : web.getUrl());
        Intent s = new Intent(Intent.ACTION_SEND);
        s.setType("text/plain");
        String t = web.getTitle();
        s.putExtra(Intent.EXTRA_SUBJECT, t == null ? u : t);
        s.putExtra(Intent.EXTRA_TEXT, (t == null || t.length() == 0 ? "" : t + "\n") + u
                + "\n\n(shared from Sarkari Alert app)");
        startActivity(Intent.createChooser(s, getString(R.string.btn_share)));
    }

    private void openExternal() {
        String u = originalUrl(web.getUrl() == null ? "" : web.getUrl());
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_no_url, Toast.LENGTH_SHORT).show();
        }
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
                + "<p style='color:#63707F;font-size:14px'>Check your internet and try again.<br>"
                + "You can also open this page in your main browser from the menu.</p>"
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
            String h = Uri.parse(u).getHost();
            return h == null ? u : h;
        } catch (Exception e) {
            return u;
        }
    }
}
