package in.sarkarialert.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;

public class Prefs {
    private static final String NAME = "sarkari_prefs";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static boolean dailyEnabled(Context c) {
        return sp(c).getBoolean("daily", true);
    }

    public static void setDaily(Context c, boolean v) {
        sp(c).edit().putBoolean("daily", v).apply();
    }

    public static boolean seeded(Context c) {
        return sp(c).getBoolean("seeded", false);
    }

    public static void setSeeded(Context c) {
        sp(c).edit().putBoolean("seeded", true).apply();
    }

    public static long lastRefresh(Context c) {
        return sp(c).getLong("last_refresh", 0L);
    }

    public static void setLastRefresh(Context c, long t) {
        sp(c).edit().putLong("last_refresh", t).apply();
    }

    public static String[] sources(Context c) {
        String raw = sp(c).getString("sources", null);
        try {
            JSONArray a = new JSONArray(raw == null ? "[]" : raw);
            String[] out = new String[a.length()];
            for (int i = 0; i < a.length(); i++) out[i] = a.getString(i);
            return out;
        } catch (Exception e) {
            return new String[0];
        }
    }

    public static void setSources(Context c, String[] arr) {
        JSONArray a = new JSONArray();
        for (String s : arr) a.put(s);
        sp(c).edit().putString("sources", a.toString()).apply();
    }

    public static void addSource(Context c, String url) {
        String[] cur = sources(c);
        for (String s : cur) if (s.equals(url)) return;
        String[] out = new String[cur.length + 1];
        System.arraycopy(cur, 0, out, 0, cur.length);
        out[cur.length] = url;
        setSources(c, out);
    }

    public static void removeSource(Context c, String url) {
        String[] cur = sources(c);
        java.util.ArrayList<String> list = new java.util.ArrayList<String>();
        for (String s : cur) if (!s.equals(url)) list.add(s);
        setSources(c, list.toArray(new String[list.size()]));
    }

    public static boolean sitesMigrated(Context c) {
        return sp(c).getBoolean("sites_migrated", false);
    }

    public static void setSitesMigrated(Context c) {
        sp(c).edit().putBoolean("sites_migrated", true).apply();
    }

    public static String[] defaultSources() {
        return new String[]{
                "https://sarkariresult.com.cm/feed/",
                "https://freejobalerts.app/feed.xml"
        };
    }
}
