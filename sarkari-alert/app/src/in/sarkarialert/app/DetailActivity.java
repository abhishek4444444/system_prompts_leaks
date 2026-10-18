package in.sarkarialert.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class DetailActivity extends Activity {

    private Alert alert;
    private Button saveBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_detail);
        long id = getIntent().getLongExtra("id", -1);
        alert = new Db(this).get(id);
        if (alert == null) {
            finish();
            return;
        }
        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        ((TextView) findViewById(R.id.d_title)).setText(alert.title);
        TextView org = (TextView) findViewById(R.id.d_org);
        org.setText(alert.org.length() > 0 ? alert.org : getString(R.string.org_label));

        ((TextView) findViewById(R.id.d_cat)).setText(Alert.categoryName(alert.category, this));
        TextView posts = (TextView) findViewById(R.id.d_posts);
        posts.setVisibility(alert.posts.length() > 0 ? View.VISIBLE : View.GONE);
        posts.setText(getString(R.string.posts_label, alert.posts));

        TextView lastBadge = (TextView) findViewById(R.id.d_last_badge);
        int d = AlarmScheduler.daysUntil(alert.lastDate);
        if (alert.lastDate.length() >= 10 && d >= 0) {
            lastBadge.setVisibility(View.VISIBLE);
            lastBadge.setText(d == 0 ? getString(R.string.days_left_1) : getString(R.string.days_left, d));
            lastBadge.setBackgroundResource(d <= 3 ? R.drawable.bg_badge_red : R.drawable.bg_badge_green);
            lastBadge.setTextColor(getResources().getColor(d <= 3 ? R.color.red : R.color.green));
        } else {
            lastBadge.setVisibility(View.GONE);
        }

        setRow(R.id.d_start, getString(R.string.apply_start_label),
                AlarmScheduler.prettyDate(alert.applyStart));
        setRow(R.id.d_last, getString(R.string.last_date_full_label),
                alert.lastDate.length() >= 10 ? AlarmScheduler.prettyDate(alert.lastDate) : "—");
        setRow(R.id.d_exam, getString(R.string.exam_date_label),
                alarmOrDash(alert.examDate));
        setRow(R.id.d_qual, getString(R.string.qualification_label),
                alert.qualification.length() > 0 ? alert.qualification : "—");
        setRow(R.id.d_fee, getString(R.string.fee_label),
                alert.fee.length() > 0 ? alert.fee : "—");

        TextView details = (TextView) findViewById(R.id.d_details);
        details.setText(alert.details.length() > 0 ? alert.details : "—");

        findViewById(R.id.d_open).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (alert.url == null || !alert.url.startsWith("http")) {
                    Toast.makeText(DetailActivity.this, R.string.error_no_url, Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(alert.url)));
                } catch (Exception e) {
                    Toast.makeText(DetailActivity.this, R.string.error_no_url, Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.d_share).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                StringBuilder sb = new StringBuilder();
                sb.append("📢 ").append(alert.title).append("\n");
                if (alert.org.length() > 0) sb.append("🏛 ").append(alert.org).append("\n");
                if (alert.lastDate.length() >= 10) {
                    sb.append("⏳ अंतिम तिथि: ").append(AlarmScheduler.prettyDate(alert.lastDate)).append("\n");
                }
                if (alert.url.length() > 0) sb.append("🔗 ").append(alert.url).append("\n");
                sb.append("\n(Sarkari Alert app se share kiya)");
                Intent s = new Intent(Intent.ACTION_SEND);
                s.setType("text/plain");
                s.putExtra(Intent.EXTRA_TEXT, sb.toString());
                startActivity(Intent.createChooser(s, getString(R.string.btn_share)));
            }
        });

        saveBtn = (Button) findViewById(R.id.d_save);
        styleSave();
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean nowSaved = !alert.saved;
                new Db(DetailActivity.this).setSaved(alert.id, nowSaved);
                alert.saved = nowSaved;
                if (nowSaved) {
                    AlarmScheduler.scheduleFor(DetailActivity.this, alert);
                } else {
                    AlarmScheduler.cancelFor(DetailActivity.this, alert.id);
                }
                styleSave();
            }
        });
    }

    private String alarmOrDash(String v) {
        return (v != null && v.length() > 0) ? AlarmScheduler.prettyDate(v) : "—";
    }

    private void setRow(int viewId, String label, String value) {
        TextView tv = (TextView) findViewById(viewId);
        tv.setText(label + ":  " + value);
    }

    private void styleSave() {
        if (alert.saved) {
            saveBtn.setText(R.string.btn_saved);
            saveBtn.setBackgroundResource(R.drawable.bg_button_dark);
        } else {
            saveBtn.setText(R.string.btn_save);
            saveBtn.setBackgroundResource(R.drawable.bg_button_primary);
        }
    }
}
