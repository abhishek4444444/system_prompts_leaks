package in.sarkarialert.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private LinearLayout feedsBox;
    private EditText feedUrl;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        final Switch daily = (Switch) findViewById(R.id.daily_switch);
        daily.setChecked(Prefs.dailyEnabled(this));
        daily.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean on = daily.isChecked();
                Prefs.setDaily(SettingsActivity.this, on);
                if (on) AlarmScheduler.scheduleDaily(SettingsActivity.this);
                else AlarmScheduler.cancelDaily(SettingsActivity.this);
            }
        });

        feedsBox = (LinearLayout) findViewById(R.id.feeds_container);
        feedUrl = (EditText) findViewById(R.id.feed_url);
        if (Prefs.sources(this).length == 0) {
            Prefs.setSources(this, Prefs.defaultSources());
        }
        findViewById(R.id.feed_add).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String u = feedUrl.getText().toString().trim();
                if (u.length() == 0 || !(u.startsWith("http://") || u.startsWith("https://"))) {
                    Toast.makeText(SettingsActivity.this, "सही URL लिखें (https://…)", Toast.LENGTH_SHORT).show();
                    return;
                }
                Prefs.addSource(SettingsActivity.this, u);
                feedUrl.setText("");
                renderFeeds();
            }
        });

        findViewById(R.id.reset_seed).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int n = Seed.load(SettingsActivity.this);
                Toast.makeText(SettingsActivity.this,
                        getString(R.string.reset_done) + " (" + n + ")", Toast.LENGTH_SHORT).show();
            }
        });

        renderFeeds();
    }

    private void renderFeeds() {
        feedsBox.removeAllViews();
        LayoutInflater li = getLayoutInflater();
        for (final String url : Prefs.sources(this)) {
            View row = li.inflate(R.layout.item_feed, feedsBox, false);
            ((TextView) row.findViewById(R.id.feed_text)).setText(url);
            row.findViewById(R.id.feed_del).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Prefs.removeSource(SettingsActivity.this, url);
                    renderFeeds();
                }
            });
            feedsBox.addView(row);
        }
    }
}
