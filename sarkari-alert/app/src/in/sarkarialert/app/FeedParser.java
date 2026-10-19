package in.sarkarialert.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.text.Html;
import android.util.Log;
import android.util.Xml;

public class FeedParser {

    public static List<Alert> parse(String body) {
        if (body == null) return new ArrayList<Alert>();
        String t = body.trim();
        if (t.startsWith("{") || t.startsWith("[")) {
            try {
                return parseJson(t);
            } catch (Exception e) {
                return new ArrayList<Alert>();
            }
        }
        return parseXml(t);
    }

    public static List<Alert> parseJson(String body) throws Exception {
        List<Alert> out = new ArrayList<Alert>();
        JSONArray arr;
        String t = body.trim();
        if (t.startsWith("[")) {
            arr = new JSONArray(t);
        } else {
            JSONObject o = new JSONObject(t);
            arr = o.optJSONArray("alerts");
            if (arr == null) arr = o.optJSONArray("items");
            if (arr == null) arr = o.optJSONArray("jobs");
            if (arr == null) arr = o.optJSONArray("data");
            if (arr == null) arr = new JSONArray();
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Alert a = new Alert();
            a.title = txt(o, new String[]{"title", "name", "post_name"});
            if (a.title.length() == 0) continue;
            a.org = txt(o, new String[]{"org", "organisation", "organization", "board", "department"});
            String catRaw = txt(o, new String[]{"category", "cat", "sector"});
            a.category = Alert.categoryCode(catRaw.length() > 0 ? catRaw : guessCategory(a.title));
            a.posts = txt(o, new String[]{"posts", "vacancies", "total_posts", "posts_count"});
            a.qualification = txt(o, new String[]{"qualification", "eligibility", "education"});
            a.fee = txt(o, new String[]{"fee", "application_fee", "fees"});
            a.applyStart = txt(o, new String[]{"apply_start", "start_date", "from_date"});
            a.lastDate = txt(o, new String[]{"last_date", "lastdate", "last_date_to_apply", "apply_last_date", "end_date", "due_date"});
            a.examDate = txt(o, new String[]{"exam_date", "exam", "examDate"});
            a.details = txt(o, new String[]{"details", "description", "summary", "info"});
            a.url = txt(o, new String[]{"url", "link", "official_url", "apply_url"});
            if (a.url.length() > 0 && !a.url.startsWith("http")) a.url = "";
            a.hash = Db.hashOf(a.title, a.org);
            out.add(a);
        }
        return out;
    }

    private static String txt(JSONObject o, String[] keys) {
        for (String k : keys) {
            String v = o.optString(k, "");
            if (v != null && v.length() > 0) {
                return clean(v);
            }
        }
        return "";
    }

    private static String clean(String s) {
        if (s == null) return "";
        String out = s.replaceAll("<[^>]+>", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&nbsp;", " ").replace("&#8211;", "-")
                .replace("&ndash;", "-").replace("&rsquo;", "'").replace("&ndash;", "-");
        out = out.replaceAll("\\s+", " ").trim();
        return out;
    }

    private static final Pattern LAST_DATE_PATTERNS[] = new Pattern[]{
            Pattern.compile("(?i)(?:last\\s*date|apply\\s*before|closing\\s*date|अंतिम\\s*तिथि|आखिरी\\s*तारीख)\\s*[:\\-–]?\\s*([0-9]{1,2}[\\-/\\.][0-9]{1,2}[\\-/\\.][0-9]{2,4})"),
            Pattern.compile("(?i)([0-9]{1,2}\\s+(?:january|february|march|april|may|june|july|august|september|october|november|december)\\s+[0-9]{4})")
    };

    /** convert dd/mm/yyyy or dd month yyyy into ISO yyyy-mm-dd (best effort) */
    public static String isoDate(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        Matcher m = Pattern.compile("([0-9]{1,2})[\\-/\\.][ ]?([0-9]{1,2})[\\-/\\.][ ]?([0-9]{2,4})").matcher(s);
        if (m.find()) {
            try {
                int d = Integer.parseInt(m.group(1));
                int mo = Integer.parseInt(m.group(2));
                int y = Integer.parseInt(m.group(3));
                if (y < 100) y += 2000;
                if (mo >= 1 && mo <= 12 && d >= 1 && d <= 31)
                    return String.format("%04d-%02d-%02d", y, mo, d);
            } catch (Exception e) { }
        }
        String[] months = {"january", "february", "march", "april", "may", "june", "july",
                "august", "september", "october", "november", "december"};
        m = Pattern.compile("([0-9]{1,2})\\s+([A-Za-z]+)\\s+([0-9]{4})").matcher(s);
        if (m.find()) {
            String mon = m.group(2).toLowerCase();
            for (int i = 0; i < months.length; i++) {
                if (mon.startsWith(months[i].substring(0, 3))) {
                    try {
                        int d = Integer.parseInt(m.group(1));
                        int y = Integer.parseInt(m.group(3));
                        if (d >= 1 && d <= 31) return String.format("%04d-%02d-%02d", y, i + 1, d);
                    } catch (Exception e) { }
                }
            }
        }
        m = Pattern.compile("([0-9]{4})-([0-9]{2})-([0-9]{2})").matcher(s);
        if (m.find()) return m.group(1) + "-" + m.group(2) + "-" + m.group(3);
        return "";
    }

    public static String guessCategory(String title) {
        if (title == null) return "job";
        String s = title.toLowerCase();
        // type-first inference: schemes, scholarships, results, admit cards
        if (s.contains("scholarship") || s.contains("fellowship") || s.contains("vidyasa")) return "scholarship";
        if (s.contains("scheme") || s.contains("yojana") || s.contains("yojna") || s.contains("abhiyan")
                || s.contains("mission") || s.contains("kalyan") || s.contains("bhatta") || s.contains("pension")) return "scheme";
        if (s.contains("admit card") || s.contains("hall ticket") || s.contains("call letter") || s.contains("exam date")) return "admit";
        if (s.contains("result") || s.contains("merit list") || s.contains("score card") || s.contains("cut off")
                || s.contains("answer key")) return "result";
        if (s.contains("railway") || s.contains("rrb") || s.contains("ntpc") || s.contains("group d")) return "railway";
        if (s.contains("ssc") || s.contains("chsl") || s.contains("chsl") || s.contains("gd constable")) return "ssc";
        if (s.contains("upsc") || s.contains("civil service") || s.contains("nda ") || s.contains(" cds ")) return "upsc";
        if (s.contains("ibps") || s.contains("sbi ") || s.contains("bank") || s.contains("rrb po") || s.contains("clerk")) return "banking";
        if (s.contains("army") || s.contains("navy") || s.contains("air force") || s.contains("agniveer") || s.contains("defence") || s.contains("bsf") || s.contains("crpf")) return "defence";
        if (s.contains("police") || s.contains("si ") || s.contains("constable")) return "police";
        if (s.contains("teacher") || s.contains("teaching") || s.contains("net ") || s.contains("ctet") || s.contains("shikshak") || s.contains("super tet")) return "teaching";
        if (s.contains("upsssc") || s.contains("upsssc") || s.contains("uppsc") || s.contains("mpesb") || s.contains("vyapam") || s.contains("dsssb") || s.contains("uksssc") || s.contains("jssc") || s.contains("bpsc") || s.contains("gssc")) return "state";
        if (s.contains("iocl") || s.contains("ntpc ") || s.contains("ongc") || s.contains("bhel") || s.contains("hal ") || s.contains("railtel")) return "psu";
        return "job";
    }

    private static List<Alert> parseXml(String body) {
        List<Alert> out = new ArrayList<Alert>();
        try {
            XmlPullParser p = Xml.newPullParser();
            java.io.StringReader r = new java.io.StringReader(body);
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            p.setInput(r);
            int type = p.getEventType();
            boolean inItem = false;
            boolean isAtom = false;
            String curText = null;
            Alert cur = null;
            String curLink = "";
            while (type != XmlPullParser.END_DOCUMENT) {
                String tag = p.getName();
                if (type == XmlPullParser.START_TAG) {
                    if ("feed".equals(tag)) isAtom = true;
                    if ((!isAtom && "item".equals(tag)) || (isAtom && "entry".equals(tag))) {
                        inItem = true;
                        cur = new Alert();
                        curLink = "";
                    } else if (inItem && "link".equals(tag)) {
                        String href = p.getAttributeValue(null, "href");
                        if (href != null && href.startsWith("http")) curLink = href.trim();
                    } else if (inItem && ("title".equals(tag) || "description".equals(tag)
                            || "summary".equals(tag) || "content".equals(tag)
                            || "pubDate".equals(tag) || "published".equals(tag) || "updated".equals(tag))) {
                        curText = tag;
                    }
                } else if (type == XmlPullParser.TEXT) {
                    if (inItem && curText != null && p.getText() != null) {
                        String add = p.getText();
                        if ("title".equals(curText)) {
                            if (cur.title.length() > 0) cur.title += add;
                            else cur.title = add;
                        } else {
                            cur.details += add;
                        }
                    }
                } else if (type == XmlPullParser.END_TAG) {
                    if ((!isAtom && "item".equals(tag)) || (isAtom && "entry".equals(tag))) {
                        if (cur != null && cur.title.trim().length() > 0) {
                            cur.title = clean(cur.title.trim());
                            String link = curLink;
                            if (link.length() == 0 && cur.details.contains("http")) {
                                Matcher mm = Pattern.compile("https?://[^\\s\"<>]+").matcher(cur.details);
                                if (mm.find()) link = mm.group();
                            }
                            cur.url = link;
                            cur.details = clean(cur.details);
                            if (cur.details.length() > 700) cur.details = cur.details.substring(0, 700) + "…";
                            cur.lastDate = "";
                            for (Pattern pt : LAST_DATE_PATTERNS) {
                                Matcher m = pt.matcher(cur.title + " " + cur.details);
                                if (m.find()) {
                                    String iso = isoDate(m.group(1));
                                    if (iso.length() > 0) { cur.lastDate = iso; break; }
                                }
                            }
                            cur.category = Alert.categoryCode(guessCategory(cur.title));
                            cur.source = "feed";
                            cur.hash = Db.hashOf(cur.title, cur.org);
                            out.add(cur);
                        }
                        inItem = false;
                        cur = null;
                    }
                    curText = null;
                }
                type = p.next();
            }
        } catch (Exception e) {
            Log.e("FeedParser", "xml parse: " + e);
        }
        return out;
    }
}
