package in.sarkarialert.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

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
        java.util.ArrayList<String> list = new java.util.ArrayList<String>();
        for (String s : cur(c)) if (!s.equals(url)) list.add(s);
        setSources(c, list.toArray(new String[list.size()]));
    }

    private static String[] cur(Context c) { return sources(c); }

    public static boolean firstRun(Context c) {
        return !sp(c).getBoolean("welcomed", false);
    }

    public static void setFirstRunDone(Context c) {
        sp(c).edit().putBoolean("welcomed", true).apply();
    }

    public static long lastSeen(Context c) {
        return sp(c).getLong("last_seen", 0L);
    }

    public static void setLastSeen(Context c, long t) {
        sp(c).edit().putLong("last_seen", t).apply();
    }

    public static boolean sitesMigrated(Context c) {
        return sp(c).getBoolean("sites_migrated", false);
    }

    public static void setSitesMigrated(Context c) {
        sp(c).edit().putBoolean("sites_migrated", true).apply();
    }

    public static String apiKey(Context c) {
        return sp(c).getString("gemini_key", "");
    }

    public static void setApiKey(Context c, String k) {
        sp(c).edit().putString("gemini_key", k).apply();
    }

    public static boolean nationalSeeded(Context c) {
        return sp(c).getBoolean("national_seeded", false);
    }

    public static void setNationalSeeded(Context c) {
        sp(c).edit().putBoolean("national_seeded", true).apply();
    }

    public static boolean recatV2(Context c) {
        return sp(c).getBoolean("recat_v2", false);
    }

    public static void setRecatV2(Context c) {
        sp(c).edit().putBoolean("recat_v2", true).apply();
    }

    public static boolean desktopMode(Context c) {
        return sp(c).getBoolean("desktop_mode", false);
    }

    public static void setDesktopMode(Context c, boolean v) {
        sp(c).edit().putBoolean("desktop_mode", v).apply();
    }

    // ---------- Skills (user-installed AI personas / instructions) ----------

    public static JSONArray skills(Context c) {
        try {
            return new JSONArray(sp(c).getString("skills_v1", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static void setSkills(Context c, JSONArray a) {
        sp(c).edit().putString("skills_v1", a.toString()).apply();
    }

    /** formatted block injected into the cloud AI system prompt */
    public static String activeSkillsForPrompt(Context c) {
        JSONArray arr = skills(c);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null || !o.optBoolean("enabled", true)) continue;
            String content = o.optString("content", "").trim();
            if (content.length() == 0) continue;
            if (n == 0) {
                sb.append("ACTIVE SKILLS installed by the user — follow them precisely ");
                sb.append("for tone, style, format and behavior in every answer:\n\n");
            }
            sb.append("[SKILL: ").append(o.optString("name", "skill")).append("]\n");
            sb.append(content.length() > 4000 ? content.substring(0, 4000) + "…" : content);
            sb.append("\n\n");
            n++;
        }
        return sb.toString();
    }

    public static String[] defaultSources() {
        return new String[]{
                "https://sarkariresult.com.cm/feed/",
                "https://freejobalerts.app/feed.xml"
        };
    }
}
