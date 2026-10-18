package in.sarkarialert.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String[] CAT_CODES = {"all", "railway", "ssc", "upsc", "banking",
            "defence", "police", "teaching", "state", "psu"};

    private Db db;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private List<Alert> items = new ArrayList<Alert>();
    private AlertAdapter adapter;

    private String query = "";
    private String category = "all";
    private boolean savedTab = false;
    private boolean refreshing = false;

    private ListView list;
    private TextView empty, status, tabJobs, tabSaved, tabSettings;
    private LinearLayout chips;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        db = new Db(this);

        list = (ListView) findViewById(R.id.list);
        empty = (TextView) findViewById(R.id.empty);
        status = (TextView) findViewById(R.id.status);
        tabJobs = (TextView) findViewById(R.id.tab_jobs);
        tabSaved = (TextView) findViewById(R.id.tab_saved);
        tabSettings = (TextView) findViewById(R.id.tab_settings);
        chips = (LinearLayout) findViewById(R.id.chips);

        if (!Prefs.seeded(this)) Seed.load(this);

        adapter = new AlertAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                Alert a = items.get(pos);
                Intent i = new Intent(MainActivity.this, DetailActivity.class);
                i.putExtra("id", a.id);
                startActivity(i);
            }
        });

        findViewById(R.id.btn_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doRefresh();
            }
        });

        tabJobs.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchTab(false); }
        });
        tabSaved.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchTab(true); }
        });
        tabSettings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        ((EditText) findViewById(R.id.search)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                reload();
            }
        });

        buildChips();
        reload();
        updateStatus(null);

        if (Prefs.sources(this).length == 0) {
            Prefs.setSources(this, Prefs.defaultSources());
        }
        if (System.currentTimeMillis() - Prefs.lastRefresh(this) > 6 * 3600000L) {
            doRefresh();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void switchTab(boolean saved) {
        savedTab = saved;
        tabJobs.setTextColor(Color.parseColor(saved ? "#9FB3C4" : "#FFFFFF"));
        tabSaved.setTextColor(Color.parseColor(saved ? "#FFFFFF" : "#9FB3C4"));
        tabJobs.setTypeface(null, saved ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        tabSaved.setTypeface(null, saved ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        reload();
    }

    private void buildChips() {
        chips.removeAllViews();
        for (final String code : CAT_CODES) {
            TextView chip = new TextView(this);
            chip.setText(Alert.categoryName(code, this));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            int h = (int) dp(7);
            chip.setPadding(h, (int) dp(5), h, (int) dp(5));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    category = code;
                    styleChips();
                    reload();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = (int) dp(7);
            chip.setLayoutParams(lp);
            chips.addView(chip);
        }
        styleChips();
    }

    private void styleChips() {
        for (int i = 0; i < chips.getChildCount(); i++) {
            TextView chip = (TextView) chips.getChildAt(i);
            String code = CAT_CODES[i];
            boolean sel = code.equals(category);
            chip.setBackgroundResource(sel ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chip.setTextColor(Color.parseColor(sel ? "#FFFFFF" : "#63707F"));
        }
    }

    private void reload() {
        items = db.list(query, category, savedTab);
        adapter.notifyDataSetChanged();
        empty.setText(savedTab ? R.string.empty_saved : R.string.empty_jobs);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        updateStatus(null);
    }

    private void updateStatus(String extra) {
        String when = getString(R.string.never);
        long t = Prefs.lastRefresh(this);
        if (t > 0) {
            String d = java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(new java.util.Date(t));
            when = d;
        }
        status.setText(getString(R.string.last_updated, when)
                + "  ·  " + db.count() + " अलर्ट" + (extra == null ? "" : "  ·  " + extra));
    }

    private void doRefresh() {
        if (refreshing) return;
        refreshing = true;
        status.setText(R.string.refreshing);
        FeedLoader.refresh(this, ui, new FeedLoader.Callback() {
            @Override
            public void onDone(int added, int sources, boolean anySuccess) {
                refreshing = false;
                String msg;
                if (!anySuccess) {
                    msg = getString(R.string.refresh_fail);
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                } else if (added > 0) {
                    msg = getString(R.string.refresh_done, added, sources);
                } else {
                    msg = getString(R.string.refresh_none);
                }
                reload();
                updateStatus(anySuccess ? msg : msg + " (" + getString(R.string.offline_note) + ")");
            }
        });
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private class AlertAdapter extends BaseAdapter {
        @Override
        public int getCount() { return items.size(); }
        @Override
        public Object getItem(int position) { return items.get(position); }
        @Override
        public long getItemId(int position) { return items.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = getLayoutInflater().inflate(R.layout.item_alert, parent, false);
            }
            Alert a = items.get(position);
            ((TextView) v.findViewById(R.id.a_title)).setText(a.title);
            TextView org = (TextView) v.findViewById(R.id.a_org);
            org.setText(a.org.length() > 0 ? a.org : a.source);
            ((TextView) v.findViewById(R.id.a_cat)).setText(
                    a.posts.length() > 0
                            ? Alert.categoryName(a.category, MainActivity.this) + " · " + a.posts
                            : Alert.categoryName(a.category, MainActivity.this));
            TextView last = (TextView) v.findViewById(R.id.a_last);
            last.setVisibility(a.lastDate.length() >= 10 ? View.VISIBLE : View.GONE);
            if (a.lastDate.length() >= 10) {
                int d = AlarmScheduler.daysUntil(a.lastDate);
                String base = getString(R.string.last_date_label, AlarmScheduler.prettyDate(a.lastDate));
                if (d < 0) {
                    last.setText(base + " · " + getString(R.string.days_over));
                    last.setBackgroundResource(R.drawable.bg_badge_red);
                    last.setTextColor(getResources().getColor(R.color.red));
                } else if (d == 0) {
                    last.setText(base + " · " + getString(R.string.days_left_1));
                    last.setBackgroundResource(R.drawable.bg_badge_red);
                    last.setTextColor(getResources().getColor(R.color.red));
                } else {
                    last.setText(base + " · " + getString(R.string.days_left, d));
                    if (d <= 3) {
                        last.setBackgroundResource(R.drawable.bg_badge_red);
                        last.setTextColor(getResources().getColor(R.color.red));
                    } else {
                        last.setBackgroundResource(R.drawable.bg_badge_green);
                        last.setTextColor(getResources().getColor(R.color.green));
                    }
                }
            }
            return v;
        }
    }
}
