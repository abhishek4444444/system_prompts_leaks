package in.sarkarialert.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
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
            "defence", "police", "teaching", "state", "psu", "scheme", "scholarship",
            "result", "admit"};

    private static final int MODE_JOBS = 0, MODE_SAVED = 1, MODE_WATCH = 2;

    private Db db;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private List<Alert> items = new ArrayList<Alert>();
    private List<WatchedSite> sites = new ArrayList<WatchedSite>();
    private AlertAdapter adapter;
    private SiteAdapter siteAdapter;

    private String query = "";
    private String category = "all";
    private int mode = MODE_JOBS;
    private boolean refreshing = false;

    private ListView list;
    private RefreshRing btnRing, pullRing;
    private TextView newPill;
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateStatus(null);
            ui.postDelayed(ticker, 30000);
        }
    };
    private TextView empty, status, subtitle;
    private LinearLayout chips;
    private View chipsScroll, watchHeader;
    private TextView tabJobs, tabSaved, tabWatch, tabSettings;
    private View liveDot;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        db = new Db(this);

        list = (ListView) findViewById(R.id.list);
        empty = (TextView) findViewById(R.id.empty);
        status = (TextView) findViewById(R.id.status);
        subtitle = (TextView) findViewById(R.id.subtitle);
        chips = (LinearLayout) findViewById(R.id.chips);
        chipsScroll = findViewById(R.id.chips_scroll);
        tabJobs = (TextView) findViewById(R.id.tab_jobs);
        tabSaved = (TextView) findViewById(R.id.tab_saved);
        tabWatch = (TextView) findViewById(R.id.tab_watch);
        tabSettings = (TextView) findViewById(R.id.tab_settings);
        liveDot = findViewById(R.id.live_dot);

        SiteMonitor.ensureDefaults(this);
        requestNotificationPermission();

        btnRing = (RefreshRing) findViewById(R.id.btn_ring);
        pullRing = (RefreshRing) findViewById(R.id.pull_ring);
        newPill = (TextView) findViewById(R.id.new_pill);
        btnRing.setColor(0xFFFFFFFF);
        btnRing.setVisibility(View.GONE);   // clean idle: only the gradient disc shows
        pullRing.setVisibility(View.GONE);
        setupPullToRefresh();
        newPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setLastSeen(MainActivity.this, System.currentTimeMillis());
                newPill.setVisibility(View.GONE);
                if (mode == MODE_WATCH) switchMode(MODE_JOBS);
                else if (mode == MODE_SAVED) switchMode(MODE_JOBS);
                else reloadAlerts();
                list.post(new Runnable() {
                    @Override
                    public void run() {
                        list.smoothScrollToPosition(0);
                    }
                });
            }
        });

        // pulsing live dot
        AlphaAnimation pulse = new AlphaAnimation(1f, 0.25f);
        pulse.setDuration(900);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        liveDot.startAnimation(pulse);

        watchHeader = getLayoutInflater().inflate(R.layout.watch_header, list, false);

        adapter = new AlertAdapter();
        siteAdapter = new SiteAdapter(this, sites);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                int header = list.getHeaderViewsCount();
                if (mode == MODE_WATCH) {
                    if (pos < header) return;
                    checkSingleSite(sites.get(pos - header));
                    return;
                }
                Alert a = items.get(pos - header);
                Intent i = new Intent(MainActivity.this, DetailActivity.class);
                i.putExtra("id", a.id);
                startActivity(i);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
                if (mode != MODE_WATCH) return false;
                final int header = list.getHeaderViewsCount();
                if (pos < header) return false;
                final WatchedSite s = sites.get(pos - header);
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(R.string.remove_confirm)
                        .setPositiveButton(R.string.remove, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                new Db(MainActivity.this).deleteSite(s.id);
                                loadWatch();
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                return true;
            }
        });

        watchHeader.findViewById(R.id.watch_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddDialog();
            }
        });

        findViewById(R.id.btn_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doRefresh();
            }
        });
        findViewById(R.id.btn_translate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, TranslateActivity.class));
            }
        });
        findViewById(R.id.btn_ai).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, LocalAiActivity.class));
            }
        });

        tabJobs.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchMode(MODE_JOBS); }
        });
        tabSaved.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchMode(MODE_SAVED); }
        });
        tabWatch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchMode(MODE_WATCH); }
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
                if (mode != MODE_WATCH) reloadAlerts();
            }
        });

        buildChips();
        switchMode(MODE_JOBS);
        NetWatcher.ensureRegistered(this);
        JobSchedulerUtil.schedule(this);

        if (System.currentTimeMillis() - Prefs.lastRefresh(this) > 6 * 3600000L) {
            doRefresh();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mode == MODE_WATCH) loadWatch();
        else reloadAlerts();
        showNewPillIfAny();
        ui.removeCallbacks(ticker);
        ui.postDelayed(ticker, 30000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(ticker);
    }

    /** Material-style pull: the content drags with your finger, an arrow-tipped
     *  ring grows in the gap that opens, pops + ticks at the trigger, then the
     *  release springs into the indeterminate spin. */
    private void setupPullToRefresh() {
        final float slop = dp(10);
        final float trigger = dp(56);  // ring travel needed to arm
        final float maxDrag = dp(120); // rubber-band ceiling
        final float resist = dp(80);   // rubber-band stiffness
        list.setOnTouchListener(new View.OnTouchListener() {
            float startY;
            boolean pulling, wasArmed;
            float drag;

            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = ev.getY();
                        pulling = false;
                        wasArmed = false;
                        break;
                    case MotionEvent.ACTION_MOVE: {
                        boolean atTop = list.getFirstVisiblePosition() == 0
                                && (list.getChildAt(0) == null
                                    || list.getChildAt(0).getTop() >= 0);
                        float dy = ev.getY() - startY;
                        if (!pulling && atTop && dy > slop && !refreshing) {
                            pulling = true;
                            pullRing.animate().cancel();
                            list.animate().cancel();
                            pullRing.bringToFront(); // always above the cards
                            pullRing.setAlpha(0f);
                            pullRing.setVisibility(View.VISIBLE);
                        }
                        if (pulling) {
                            float over = Math.max(0f, dy - slop);
                            drag = maxDrag * (1f - 1f / (over / resist + 1f)); // rubber band
                            float travel = Math.min(drag, trigger);
                            pullRing.setAlpha(Math.max(0f, Math.min(1f,
                                    (drag - dp(12)) / dp(20))));
                            pullRing.setTranslationY(-dp(38) + travel);
                            pullRing.setPull(drag / trigger);
                            list.setTranslationY(drag * 0.5f); // content follows the finger
                            boolean armedNow = drag >= trigger;
                            if (armedNow != wasArmed) {
                                wasArmed = armedNow;
                                if (armedNow) tick();
                            }
                            return true;
                        }
                        break;
                    }
                    case MotionEvent.ACTION_UP:
                        if (pulling) {
                            pulling = false;
                            if (drag >= trigger && !refreshing) beginPullRefresh();
                            else springBack();
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        if (pulling) {
                            pulling = false;
                            springBack();
                            return true;
                        }
                        break;
                }
                return false;
            }
        });
    }

    /** Released past the trigger: park the ring below the header, offset the
     *  content, and spin while the fetch runs. */
    private void beginPullRefresh() {
        pullRing.startSpin();
        pullRing.animate().translationY(dp(4)).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        list.animate().translationY(dp(50)).setDuration(260)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        doRefresh();
    }

    /** Released too early: everything eases back to rest. */
    private void springBack() {
        list.animate().translationY(0f).setDuration(260)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
        pullRing.setPull(0f);
        pullRing.animate().translationY(-dp(38)).alpha(0f).setDuration(200)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        pullRing.setVisibility(View.GONE);
                        pullRing.setAlpha(1f);
                    }
                }).start();
    }

    /** Short confirmation buzz when the pull arms (VIBRATE permission held). */
    private void tick() {
        try {
            android.os.Vibrator vib =
                    (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vib == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(android.os.VibrationEffect.createOneShot(18,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vib.vibrate(18);
            }
        } catch (Exception ignored) {
        }
    }

    private void showNewPillIfAny() {
        int n = new Db(this).countNewerThan(Prefs.lastSeen(this));
        if (n > 0 && mode != MODE_WATCH) {
            newPill.setText(getString(R.string.new_pill_text, n));
            newPill.setVisibility(View.VISIBLE);
        } else {
            newPill.setVisibility(View.GONE);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 101);
            }
        }
    }

    private void switchMode(int m) {
        mode = m;
        styleTabs();
        chipsScroll.setVisibility(m == MODE_WATCH ? View.GONE : View.VISIBLE);
        if (m == MODE_WATCH) {
            if (list.getHeaderViewsCount() == 0) list.addHeaderView(watchHeader);
            loadWatch();
        } else {
            if (list.getHeaderViewsCount() > 0) list.removeHeaderView(watchHeader);
            // rebind the alerts adapter: ListView kept the site adapter after Watch
            list.setAdapter(adapter);
            reloadAlerts();
        }
    }

    private void styleTabs() {
        TextView[] tabs = {tabJobs, tabSaved, tabWatch, tabSettings};
        for (int i = 0; i < tabs.length; i++) {
            boolean sel = i == mode;
            tabs[i].setBackgroundResource(sel ? R.drawable.bg_segment_active
                    : android.R.color.transparent);
            tabs[i].setTextColor(Color.parseColor(sel ? "#12263E" : "#9FB3C4"));
        }
    }

    private void buildChips() {
        chips.removeAllViews();
        for (final String code : CAT_CODES) {
            TextView chip = new TextView(this);
            chip.setText(Alert.categoryName(code, this));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            int h = (int) dp(9);
            chip.setPadding(h, (int) dp(5), h, (int) dp(5));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    category = code;
                    styleChips();
                    reloadAlerts();
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

    private void reloadAlerts() {
        items = db.list(query, category, mode == MODE_SAVED);
        adapter.notifyDataSetChanged();
        empty.setText(mode == MODE_SAVED ? R.string.empty_saved : R.string.empty_jobs);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(items.isEmpty() && list.getHeaderViewsCount() == 0
                ? View.GONE : View.VISIBLE);
        updateStatus(null);
    }

    private void loadWatch() {
        sites = new Db(this).listSites();
        if (siteAdapter == null) {
            siteAdapter = new SiteAdapter(this, sites);
        } else {
            siteAdapter.setSites(sites);
        }
        list.setAdapter(siteAdapter);
        empty.setText(R.string.watch_empty);
        empty.setVisibility(sites.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(sites.isEmpty() && list.getHeaderViewsCount() == 0
                ? View.GONE : View.VISIBLE);
        updateStatus(null);
    }

    private void checkSingleSite(final WatchedSite s) {
        Toast.makeText(this, R.string.refreshing, Toast.LENGTH_SHORT).show();
        SiteMonitor.checkOne(this, s, ui, new SiteMonitor.SingleCallback() {
            @Override
            public void onDone(int added, String error) {
                loadWatch();
                if (error != null) {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.site_error, error), Toast.LENGTH_SHORT).show();
                } else if (added > 0) {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.refresh_done, added, 1), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this,
                            R.string.refresh_none, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showAddDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(20);
        box.setPadding(pad, (int) dp(10), pad, 0);
        final EditText urlIn = new EditText(this);
        urlIn.setHint(R.string.add_site_hint);
        urlIn.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        urlIn.setTextSize(13);
        box.addView(urlIn);
        final EditText labelIn = new EditText(this);
        labelIn.setHint(R.string.add_site_label_hint);
        labelIn.setTextSize(13);
        box.addView(labelIn);

        new AlertDialog.Builder(this)
                .setTitle(R.string.add_site_title)
                .setView(box)
                .setPositiveButton(R.string.add_site_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        addSite(urlIn.getText().toString().trim(),
                                labelIn.getText().toString().trim());
                    }
                })
                .setNegativeButton(R.string.add_site_cancel, null)
                .show();
    }

    private void addSite(String url, String label) {
        if (url.length() == 0 || !(url.startsWith("http://") || url.startsWith("https://"))) {
            Toast.makeText(this, R.string.add_site_bad_url, Toast.LENGTH_SHORT).show();
            return;
        }
        String low = url.toLowerCase();
        String mode2 = (low.contains("/feed") || low.endsWith(".xml")
                || low.contains("rss") || low.contains("format=feed")) ? "feed" : "page";
        WatchedSite w = new WatchedSite();
        w.url = url;
        w.mode = mode2;
        w.label = label.length() > 0 ? label : SiteMonitor.hostOf(url);
        new Db(this).upsertSite(w);
        Toast.makeText(this, R.string.add_site_done, Toast.LENGTH_SHORT).show();
        loadWatch();
        // baseline capture right away
        final WatchedSite added = w;
        SiteMonitor.checkOne(this, added, ui, new SiteMonitor.SingleCallback() {
            @Override
            public void onDone(int n, String error) {
                loadWatch();
            }
        });
    }

    private void updateStatus(String extra) {
        String when = getString(R.string.never);
        long t = Prefs.lastRefresh(this);
        if (t > 0) {
            String d = SiteAdapter.timeAgo(this, t);
            when = d;
        }
        int siteCount = new Db(this).sitesCount();
        subtitle.setText(getString(R.string.monitor_sub_on));
        status.setText(getString(R.string.last_updated, when)
                + "  ·  " + siteCount + " sources  ·  " + db.count() + " alerts"
                + (extra == null ? "" : "  ·  " + extra));
    }

    private void doRefresh() {
        if (refreshing) return;
        refreshing = true;
        status.setText(R.string.refreshing);
        btnRing.setSpinning(true);
        NetWatcher.uiListener = new Runnable() {
            @Override
            public void run() {
                onRefreshDone();
            }
        };
        NetWatcher.refreshOnce(this);
        // safety net: if no callback arrived (all sources unreachable), settle anyway
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (refreshing) onRefreshDone();
            }
        }, 12000);
    }

    private void onRefreshDone() {
        if (!refreshing) return;
        refreshing = false;
        btnRing.setSpinning(false);
        pullRing.finishSpin(); // completes circle + fades; no-op if not spinning
        list.animate().translationY(0f).setDuration(240)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        if (mode == MODE_WATCH) loadWatch();
        else reloadAlerts();
        showNewPillIfAny();
        updateStatus(null);
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

            int color = categoryColor(a.category);
            View strip = v.findViewById(R.id.a_strip);
            strip.setBackgroundColor(color);
            TextView cat = (TextView) v.findViewById(R.id.a_cat);
            cat.setBackgroundColor(color);
            cat.setText(Alert.categoryName(a.category, MainActivity.this));

            TextView posts = (TextView) v.findViewById(R.id.a_posts);
            posts.setVisibility(a.posts.length() > 0 ? View.VISIBLE : View.GONE);
            posts.setText(a.posts);

            TextView newB = (TextView) v.findViewById(R.id.a_new);
            newB.setVisibility(System.currentTimeMillis() - new Db(MainActivity.this)
                    .getCreated(a.id) < 86400000L ? View.VISIBLE : View.GONE);

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

    private int categoryColor(String code) {
        int rid = getResources().getIdentifier("cat_" + code, "color", getPackageName());
        if (rid == 0) rid = R.color.cat_other;
        return getResources().getColor(rid);
    }
}
