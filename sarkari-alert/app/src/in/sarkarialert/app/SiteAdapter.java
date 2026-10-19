package in.sarkarialert.app;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class SiteAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<WatchedSite> items;

    public SiteAdapter(Context c, List<WatchedSite> items) {
        this.ctx = c;
        this.items = items;
    }

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
            v = android.view.LayoutInflater.from(ctx).inflate(R.layout.item_site, parent, false);
        }
        WatchedSite s = items.get(position);
        View dot = v.findViewById(R.id.s_dot);
        if (s.lastError != null && s.lastError.length() > 0) {
            dot.setBackgroundResource(R.drawable.dot_status_err);
        } else if (s.lastChecked > 0) {
            dot.setBackgroundResource(R.drawable.dot_status_ok);
        } else {
            dot.setBackgroundResource(R.drawable.dot_status_idle);
        }
        ((TextView) v.findViewById(R.id.s_label)).setText(s.label);
        ((TextView) v.findViewById(R.id.s_url)).setText(s.url);
        ((TextView) v.findViewById(R.id.s_mode)).setText(
                "feed".equals(s.mode) ? "FEED" : "PAGE");

        TextView isNew = (TextView) v.findViewById(R.id.s_new);
        if (s.lastNew > 0) {
            isNew.setVisibility(View.VISIBLE);
            isNew.setText(ctx.getString(R.string.site_new_count, s.lastNew));
        } else {
            isNew.setVisibility(View.GONE);
        }

        TextView status = (TextView) v.findViewById(R.id.s_status);
        if (s.lastError != null && s.lastError.length() > 0) {
            status.setText(ctx.getString(R.string.site_error, s.lastError));
            status.setTextColor(ctx.getResources().getColor(R.color.red));
        } else if (s.lastChecked > 0) {
            if (s.lastHash == null || s.lastHash.length() == 0) {
                status.setText(ctx.getString(R.string.site_baseline, countSnapshot(s)));
            } else {
                status.setText(ctx.getString(R.string.site_checked, timeAgo(ctx, s.lastChecked)));
            }
            status.setTextColor(ctx.getResources().getColor(R.color.text_sub));
        } else {
            status.setText("Pending first check");
            status.setTextColor(ctx.getResources().getColor(R.color.text_sub));
        }
        return v;
    }

    private static int countSnapshot(WatchedSite s) {
        try {
            return new org.json.JSONArray(s.snapshot).length();
        } catch (Exception e) {
            return 0;
        }
    }

    static String timeAgo(Context c, long then) {
        long d = System.currentTimeMillis() - then;
        if (d < 90000L) return c.getString(R.string.time_now);
        if (d < 3600000L) return c.getString(R.string.time_min, (int) (d / 60000L));
        if (d < 86400000L) return c.getString(R.string.time_hr, (int) (d / 3600000L));
        return c.getString(R.string.time_day, (int) (d / 86400000L));
    }
}
