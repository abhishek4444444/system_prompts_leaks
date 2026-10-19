package in.sarkarialert.app;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import org.json.JSONArray;

import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Universal watch engine: checks feeds AND arbitrary web pages.
 * - feed mode: parse items, insert new alerts
 * - page mode: fingerprint the page, extract anchors; links that were not in
 *   the stored snapshot become instant alerts ("scheme just appeared").
 */
public class SiteMonitor {

    public interface Callback {
        void onDone(int added, int total, int ok, List<Alert> fresh);
    }

    public interface SingleCallback {
        void onDone(int added, String error);
    }

    private static final int MAX_LINKS = 200;
    private static final int MAX_BODY = 3 * 1024 * 1024;
    private static final Pattern A_TAG = Pattern.compile(
            "<a[^>]+href\\s*=\\s*[\"']?(https?://[^\"'\\s>]+)[\"']?[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SCRIPT = Pattern.compile(
            "(?is)<(script|style)[^>]*>.*?</(script|style)>");

    public static void ensureDefaults(Context c) {
        Db db = new Db(c);
        if (!Prefs.sitesMigrated(c)) {
            for (String url : Prefs.sources(c)) {
                db.upsertSite(site(url, "feed", hostOf(url)));
            }
            Prefs.setSitesMigrated(c);
        }
        if (db.sitesCount() == 0) {
            db.upsertSite(site("https://sarkariresult.com.cm/feed/", "feed", "Sarkari Result (feed)"));
            db.upsertSite(site("https://freejobalerts.app/feed.xml", "feed", "Free Job Alerts (feed)"));
            db.upsertSite(site("https://sarkariresult.com/latestjob/", "page", "Sarkari Result — Latest Jobs"));
            db.upsertSite(site("https://www.freejobalert.com/sarkarijob/", "page", "FreeJobAlert — Latest"));
        }
        // national scheme / scholarship / govt news sources — seeded ONCE, so
        // anything the user removes stays removed (this was the remove bug)
        if (!Prefs.nationalSeeded(c)) {
            db.upsertSite(site("https://www.myscheme.gov.in/", "page", "MyScheme — Govt Schemes"));
            db.upsertSite(site("https://www.india.gov.in/whats-new", "page", "India.gov.in — What's New"));
            db.upsertSite(site("https://www.pmindia.gov.in/en/news-updates/", "page", "PM India — News & Schemes"));
            db.upsertSite(site("https://scholarships.gov.in/", "page", "National Scholarship Portal"));
            Prefs.setNationalSeeded(c);
        }
    }

    private static WatchedSite site(String url, String mode, String label) {
        WatchedSite w = new WatchedSite();
        w.url = url;
        w.mode = mode;
        w.label = label != null ? label : hostOf(url);
        return w;
    }

    public static String hostOf(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    public static void refreshAll(final Context ctx, final Handler ui, final Callback cb) {
        ensureDefaults(ctx);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Db db = new Db(ctx);
                List<WatchedSite> sites = db.listSites();
                int added = 0, ok = 0;
                final List<Alert> fresh = new ArrayList<Alert>();
                for (WatchedSite s : sites) {
                    if (!s.enabled) continue;
                    try {
                        int n = checkSite(ctx, db, s, fresh);
                        added += n;
                        ok++;
                    } catch (Exception e) {
                        Log.e("SiteMonitor", "site failed: " + s.url + " : " + e);
                        db.updateSiteState(s.id, s.lastHash, s.snapshot,
                                System.currentTimeMillis(),
                                trimErr(String.valueOf(e)), s.lastNew);
                    }
                }
                final int fa = added, fo = ok, ft = sites.size();
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onDone(fa, ft, fo, fresh);
                    }
                });
            }
        }).start();
    }

    /** check one site immediately (from the Watch screen) */
    public static void checkOne(final Context ctx, final WatchedSite w, final Handler ui,
                                final SingleCallback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Db db = new Db(ctx);
                final List<Alert> tmp = new ArrayList<Alert>();
                int n = 0;
                String err = null;
                try {
                    n = checkSite(ctx, db, w, tmp);
                } catch (Exception e) {
                    err = trimErr(String.valueOf(e));
                    db.updateSiteState(w.id, w.lastHash, w.snapshot,
                            System.currentTimeMillis(), err, w.lastNew);
                }
                final int fn = n;
                final String ferr = err;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onDone(fn, ferr);
                    }
                });
            }
        }).start();
    }

    /** @return number of NEW alerts from this site */
    static int checkSite(Context ctx, Db db, WatchedSite s, List<Alert> fresh) throws Exception {
        String body = fetch(s.url);
        int newCount = 0;
        if ("feed".equals(s.mode)) {
            List<Alert> alerts = FeedParser.parse(body);
            for (Alert a : alerts) {
                a.source = s.label;
                if (a.org == null || a.org.length() == 0) a.org = s.label;
                String iso = FeedParser.isoDate(a.lastDate);
                if (iso.length() > 0) a.lastDate = iso;
                if (db.upsert(a)) {
                    newCount++;
                    fresh.add(a);
                }
            }
            db.updateSiteState(s.id, s.lastHash, s.snapshot,
                    System.currentTimeMillis(), null, newCount);
        } else {
            String pageHash = sha256(normalize(body));
            List<String[]> links = extractLinks(body);
            HashSet<String> known = readSnapshot(s.snapshot);
            boolean baseline = known.isEmpty();
            for (String[] l : links) {
                if (baseline) break; // first run: capture snapshot silently
                if (!known.contains(l[0])) {
                    Alert a = new Alert();
                    a.title = l[1];
                    a.url = l[0];
                    a.org = s.label;
                    a.category = Alert.categoryCode(FeedParser.guessCategory(a.title));
                    a.hash = Db.hashOf(a.title, a.url);
                    a.source = s.label;
                    String iso = FeedParser.isoDate(a.title);
                    if (iso.length() > 0) a.lastDate = iso;
                    if (db.upsert(a)) {
                        newCount++;
                        fresh.add(a);
                    }
                }
            }
            JSONArray arr = new JSONArray();
            int cap = Math.min(links.size(), MAX_LINKS);
            for (int i = 0; i < cap; i++) arr.put(links.get(i)[0]);
            db.updateSiteState(s.id, pageHash, arr.toString(),
                    System.currentTimeMillis(), null, newCount);
        }
        return newCount;
    }

    static String fetch(String surl) throws Exception {
        URL u = new URL(surl);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36");
        conn.setRequestProperty("Accept-Encoding", "gzip");
        conn.setRequestProperty("Accept",
                "text/html, application/rss+xml, application/json, application/xml, */*");
        conn.setRequestProperty("Cache-Control", "no-cache");
        InputStream in = conn.getInputStream();
        try {
            String enc = conn.getContentEncoding();
            if (enc != null && enc.toLowerCase().contains("gzip")) in = new GZIPInputStream(in);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BODY) break;
                bo.write(buf, 0, n);
            }
            return bo.toString("UTF-8");
        } finally {
            try { in.close(); } catch (Exception ignored) { }
            conn.disconnect();
        }
    }

    /** extract [url, anchor-text] pairs, filtered for meaningful content links */
    static List<String[]> extractLinks(String html) {
        List<String[]> out = new ArrayList<String[]>();
        HashSet<String> seen = new HashSet<String>();
        Matcher m = A_TAG.matcher(html);
        while (m.find() && out.size() < MAX_LINKS * 2) {
            String url = m.group(1).trim();
            String text = cleanText(m.group(2));
            if (text.length() < 6 || text.length() > 160) continue;
            String low = url.toLowerCase();
            if (low.endsWith(".jpg") || low.endsWith(".png") || low.endsWith(".css")
                    || low.endsWith(".js") || low.endsWith(".ico") || low.endsWith(".svg")) continue;
            String tlow = text.toLowerCase();
            if (tlow.contains("login") || tlow.contains("privacy") || tlow.contains("about us")
                    || tlow.contains("contact") || tlow.contains("click here")
                    || tlow.contains("read more") || tlow.contains("home")) continue;
            if (!seen.add(url)) continue;
            out.add(new String[]{url, text});
            if (out.size() >= MAX_LINKS) break;
        }
        return out;
    }

    static String normalize(String html) {
        String s = SCRIPT.matcher(html).replaceAll(" ");
        s = s.replaceAll("<[^>]+>", " ").replaceAll("&[a-z#0-9]+;", " ")
                .replaceAll("\\s+", " ").trim();
        return s;
    }

    private static String cleanText(String raw) {
        String s = raw.replaceAll("<[^>]+>", " ").replace("&amp;", "&")
                .replace("&nbsp;", " ").replace("&#39;", "'").replace("&quot;", "\"")
                .replace("&ndash;", "-").replace("&rsquo;", "'").replace("&ndash;", "-");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static HashSet<String> readSnapshot(String json) {
        HashSet<String> out = new HashSet<String>();
        try {
            JSONArray arr = new JSONArray(json == null ? "[]" : json);
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    static String sha256(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(data.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(data.hashCode());
        }
    }

    private static String trimErr(String e) {
        if (e == null) return null;
        return e.length() > 90 ? e.substring(0, 90) : e;
    }
}
