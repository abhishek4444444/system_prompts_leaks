package in.sarkarialert.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebSettings;

/** Full-screen viewer for the HTML infographics the AI assistant builds. */
public class InfographicActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_infographic);
        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        WebView web = (WebView) findViewById(R.id.info_web);
        WebSettings s = web.getSettings();
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        String html = getIntent().getStringExtra("html");
        if (html == null || html.length() == 0) {
            finish();
            return;
        }
        web.setBackgroundColor(0xFF0B1E33);
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }
}
