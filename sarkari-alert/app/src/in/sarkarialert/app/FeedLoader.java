package in.sarkarialert.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class FeedLoader {

    public interface Callback {
        void onDone(int added, int sources, boolean anySuccess);
    }

    public static void refresh(final Context ctx, final Handler ui, final Callback cb) {
        final String[] sources = Prefs.sources(ctx);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Db db = new Db(ctx);
                int added = 0, ok = 0;
                for (String surl : sources) {
                    try {
                        String body = fetch(surl);
                        List<Alert> alerts = FeedParser.parse(body);
                        for (Alert a : alerts) {
                            String iso = FeedParser.isoDate(a.lastDate);
                            if (iso.length() > 0) a.lastDate = iso;
                            if (db.upsert(a)) added++;
                        }
                        ok++;
                    } catch (Exception e) {
                        Log.e("FeedLoader", "source failed: " + surl + " : " + e);
                    }
                }
                if (added > 0) Prefs.setLastRefresh(ctx, System.currentTimeMillis());
                final int fAdded = added, fOk = ok;
                final boolean success = fOk > 0;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onDone(fAdded, sources.length, success);
                    }
                });
            }
        }).start();
    }

    public static String fetch(String surl) throws Exception {
        URL u = new URL(surl);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(12000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 SarkariAlert/1.0");
        conn.setRequestProperty("Accept-Encoding", "gzip");
        conn.setRequestProperty("Accept", "application/rss+xml, application/json, application/xml, text/xml, */*");
        InputStream in = conn.getInputStream();
        try {
            String enc = conn.getContentEncoding();
            if (enc != null && enc.toLowerCase().contains("gzip")) in = new GZIPInputStream(in);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 3 * 1024 * 1024) break;
                bo.write(buf, 0, n);
            }
            return bo.toString("UTF-8");
        } finally {
            try { in.close(); } catch (Exception e) { }
            conn.disconnect();
        }
    }
}
