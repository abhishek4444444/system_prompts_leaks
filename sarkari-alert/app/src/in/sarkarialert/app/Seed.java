package in.sarkarialert.app;

import android.content.Context;

public class Seed {
    public static int load(Context c) {
        try {
            java.io.InputStream in = c.getAssets().open("seed.json");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            String json = bo.toString("UTF-8");
            java.util.List<Alert> alerts = FeedParser.parseJson(json);
            Db db = new Db(c);
            int added = 0;
            for (Alert a : alerts) {
                a.source = "seed";
                if (db.upsert(a)) added++;
            }
            Prefs.setSeeded(c);
            return added;
        } catch (Exception e) {
            return 0;
        }
    }
}
