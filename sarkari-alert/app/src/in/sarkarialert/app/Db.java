package in.sarkarialert.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class Db extends SQLiteOpenHelper {
    private static final String DB = "sarkari.db";
    private static final int VER = 1;

    public Db(Context c) {
        super(c, DB, null, VER);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE alerts (_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "hash TEXT UNIQUE, title TEXT, org TEXT, category TEXT, posts TEXT," +
                "qualification TEXT, fee TEXT, apply_start TEXT, last_date TEXT," +
                "exam_date TEXT, details TEXT, url TEXT, source TEXT, saved INTEGER DEFAULT 0," +
                "created_at INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
    }

    private static ContentValues cv(Alert a, long now) {
        ContentValues v = new ContentValues();
        v.put("hash", a.hash);
        v.put("title", a.title);
        v.put("org", a.org);
        v.put("category", a.category);
        v.put("posts", a.posts);
        v.put("qualification", a.qualification);
        v.put("fee", a.fee);
        v.put("apply_start", a.applyStart);
        v.put("last_date", a.lastDate);
        v.put("exam_date", a.examDate);
        v.put("details", a.details);
        v.put("url", a.url);
        v.put("source", a.source);
        v.put("created_at", now);
        return v;
    }

    /** @return true if a new row was inserted */
    public boolean upsert(Alert a) {
        if (a.hash == null || a.hash.length() == 0) a.hash = hashOf(a.title, a.org);
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT _id FROM alerts WHERE hash=?", new String[]{a.hash});
        boolean exists = c.moveToFirst();
        c.close();
        if (exists) return false;
        db.insertWithOnConflict("alerts", null, cv(a, System.currentTimeMillis()),
                SQLiteDatabase.CONFLICT_IGNORE);
        return true;
    }

    public Alert get(long id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM alerts WHERE _id=?",
                new String[]{String.valueOf(id)});
        Alert a = c.moveToFirst() ? fromCursor(c) : null;
        c.close();
        return a;
    }

    public List<Alert> list(String query, String category, boolean savedOnly) {
        StringBuilder sql = new StringBuilder("SELECT * FROM alerts WHERE 1=1");
        List<String> args = new ArrayList<String>();
        if (query != null && query.length() > 0) {
            sql.append(" AND (title LIKE ? OR org LIKE ? OR details LIKE ?)");
            String like = "%" + query + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (category != null && !category.equals("all")) {
            sql.append(" AND category=?");
            args.add(category);
        }
        if (savedOnly) {
            sql.append(" AND saved=1");
        }
        sql.append(" ORDER BY (last_date IS NULL), last_date ASC, _id DESC LIMIT 400");
        Cursor c = getReadableDatabase().rawQuery(sql.toString(),
                args.toArray(new String[args.size()]));
        List<Alert> out = new ArrayList<Alert>();
        while (c.moveToNext()) out.add(fromCursor(c));
        c.close();
        return out;
    }

    public int count() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM alerts", null);
        int n = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return n;
    }

    public void setSaved(long id, boolean saved) {
        ContentValues v = new ContentValues();
        v.put("saved", saved ? 1 : 0);
        getWritableDatabase().update("alerts", v, "_id=?", new String[]{String.valueOf(id)});
    }

    public List<Alert> savedAlerts() {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM alerts WHERE saved=1", null);
        List<Alert> out = new ArrayList<Alert>();
        while (c.moveToNext()) out.add(fromCursor(c));
        c.close();
        return out;
    }

    /** delete non-saved rows older than daysOld; returns removed count */
    public int cleanup(int daysOld) {
        long cutoff = System.currentTimeMillis() - daysOld * 86400000L;
        return getWritableDatabase().delete("alerts", "saved=0 AND created_at<?",
                new String[]{String.valueOf(cutoff)});
    }

    public static String hashOf(String title, String org) {
        String base = (title == null ? "" : title) + "|" + (org == null ? "" : org);
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] d = md.digest(base.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(base.hashCode());
        }
    }

    private static Alert fromCursor(Cursor c) {
        Alert a = new Alert();
        a.id = c.getLong(c.getColumnIndexOrThrow("_id"));
        a.hash = s(c, "hash");
        a.title = s(c, "title");
        a.org = s(c, "org");
        a.category = s(c, "category");
        a.posts = s(c, "posts");
        a.qualification = s(c, "qualification");
        a.fee = s(c, "fee");
        a.applyStart = s(c, "apply_start");
        a.lastDate = s(c, "last_date");
        a.examDate = s(c, "exam_date");
        a.details = s(c, "details");
        a.url = s(c, "url");
        a.source = s(c, "source");
        a.saved = c.getInt(c.getColumnIndexOrThrow("saved")) == 1;
        return a;
    }

    private static String s(Cursor c, String col) {
        String v = c.getString(c.getColumnIndexOrThrow(col));
        return v == null ? "" : v;
    }
}
