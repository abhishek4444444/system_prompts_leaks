package in.sarkarialert.app;

public class Alert {
    public long id;
    public String hash = "";
    public String title = "";
    public String org = "";
    public String category = "other";
    public String posts = "";
    public String qualification = "";
    public String fee = "";
    public String applyStart = "";
    public String lastDate = "";
    public String examDate = "";
    public String details = "";
    public String url = "";
    public String source = "";
    public boolean saved;

    public static String categoryCode(String raw) {
        if (raw == null) return "other";
        String s = raw.trim().toLowerCase();
        if (s.length() == 0) return "other";
        if (s.equals("railway") || s.equals("rrb")) return "railway";
        if (s.equals("ssc")) return "ssc";
        if (s.equals("upsc")) return "upsc";
        if (s.equals("banking") || s.equals("bank")) return "banking";
        if (s.equals("defence") || s.equals("army") || s.equals("navy") || s.equals("airforce")) return "defence";
        if (s.equals("police")) return "police";
        if (s.equals("teaching") || s.equals("shikshan")) return "teaching";
        if (s.equals("state") || s.equals("rajya")) return "state";
        if (s.equals("psu")) return "psu";
        if (s.equals("news") || s.equals("update")) return "news";
        if (s.equals("job") || s.equals("jobs") || s.equals("naukri")) return "job";
        return "other";
    }

    public static String categoryName(String code, android.content.Context c) {
        int rid = c.getResources().getIdentifier("cat_" + code, "string", c.getPackageName());
        if (rid != 0) return c.getString(rid);
        return code;
    }
}
