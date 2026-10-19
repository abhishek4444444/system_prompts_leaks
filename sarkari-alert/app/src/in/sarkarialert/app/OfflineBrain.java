package in.sarkarialert.app;

import android.content.Context;

import java.util.List;

/**
 * Local AI — instant, on-device, zero-delay answers built from the alert's
 * own data and built-in knowledge. Works with NO internet and NO API key.
 * Used automatically when no cloud key is configured, or via the
 * "Local instant" chip.
 */
final class OfflineBrain {

    private OfflineBrain() { }

    /** can this question be answered fully offline? */
    static boolean canHandle(String q) {
        String s = norm(q);
        return s.length() > 0 && (
                s.contains("last date") || s.contains("kab hai") || s.contains("kab tak")
             || s.contains("fee") || s.contains("fees")
             || s.contains("eligib") || s.contains("qualif") || s.contains("yogyata")
             || s.contains("apply") || s.contains("kaise") || s.contains("how to")
             || s.contains("summary") || s.contains("short me") || s.contains("summarize")
             || s.contains("what is this") || s.contains("kya hai")
             || s.contains("hindi") || s.contains("explain") || s.contains("samjha")
             || s.contains("notification") || s.contains("help") || s.contains("madad"));
    }

    static String answer(String q, Alert a, Context c) {
        String s = norm(q);
        // whole-database local intelligence when no specific alert is open
        if (a == null) {
            String db = dbAnswer(s, c);
            if (db != null) return db;
        }
        if (a != null) {
            if (s.contains("last date") || s.contains("kab hai") || s.contains("kab tak")) {
                return lastDateAnswer(a);
            }
            if (s.contains("fee") || s.contains("fees")) {
                return a.fee.length() > 0
                        ? "💰 Application fee:\n" + a.fee
                        : "💰 The notification doesn't mention a fee in the app's data. "
                          + "Open the official link and check the fee section before applying.";
            }
            if (s.contains("eligib") || s.contains("qualif") || s.contains("yogyata")) {
                return a.qualification.length() > 0
                        ? "🎓 Eligibility / Qualification:\n" + a.qualification
                        : "🎓 Qualification is not captured for this alert. "
                          + "Open the official notification for the detailed eligibility table.";
            }
            if (s.contains("apply") || s.contains("kaise") || s.contains("how to")) {
                return applyAnswer(a);
            }
            if (s.contains("summary") || s.contains("short me") || s.contains("summarize")
                    || s.contains("what is this") || s.contains("kya hai")
                    || s.contains("explain") || s.contains("samjha")) {
                return summary(a);
            }
            if (s.contains("hindi")) {
                return hindiDigest(a);
            }
        }
        if (s.contains("notification") || s.contains("help") || s.contains("madad")) {
            return "Notifications not arriving? Quick fixes:\n\n"
                    + "1. Settings → \"Open notification settings\" → allow all\n"
                    + "2. Settings → \"Battery & app settings\" → disable battery optimization for Sarkari Alert\n"
                    + "3. The app checks the moment your internet turns on — no background drain\n"
                    + "4. Watch more sites in the Watch tab for wider coverage\n\n"
                    + "This is your LOCAL assistant — instant, offline, and free. "
                    + "For deep questions add a free AI key (Gemini / Groq / DeepSeek) in Settings.";
        }
        if (a == null) {
            return "I am the LOCAL assistant — I answer instantly, offline and free "
                    + "from the details of an open alert.\n\n"
                    + "Open any alert (or tap a notification) and ask me:\n"
                    + "• \"last date kab hai?\"\n• \"fee kya hai?\"\n• \"eligibility?\"\n"
                    + "• \"how to apply?\"\n• \"summary\"\n• \"hindi me batao\"\n\n"
                    + "For open general questions, add a free cloud AI key in Settings "
                    + "(Gemini, Groq or DeepSeek — auto-detected).";
        }
        return summary(a);
    }

    private static String lastDateAnswer(Alert a) {
        if (a.lastDate.length() < 10) {
            return "⏳ No last date is captured for this alert. "
                    + "Open the official link to confirm the schedule.";
        }
        int d = AlarmScheduler.daysUntil(a.lastDate);
        String pretty = AlarmScheduler.prettyDate(a.lastDate);
        if (d < 0) return "⏳ Last date was " + pretty + " — applications are closed for this one.";
        if (d == 0) return "⏳ TODAY is the last date (" + pretty + ")! Apply as soon as possible.";
        return "⏳ Last date: " + pretty + "\n"
                + "⏰ " + d + " days left" + (d <= 3 ? " — hurry!" : ".");
    }

    private static String applyAnswer(Alert a) {
        StringBuilder sb = new StringBuilder();
        sb.append("📝 How to apply:\n");
        sb.append("1. Open the official link (Detail screen → \"Open in app\" — it has SiteGuard, so it works even when the server is down).\n");
        if (a.fee.length() > 0) sb.append("2. Keep the fee ready: ").append(a.fee).append(".\n");
        else sb.append("2. Check the application fee in the official notice.\n");
        if (a.qualification.length() > 0) sb.append("3. Keep documents proving: ").append(a.qualification).append(".\n");
        else sb.append("3. Keep your qualification documents scanned.\n");
        if (a.lastDate.length() >= 10) {
            int d = AlarmScheduler.daysUntil(a.lastDate);
            if (d >= 0) sb.append("4. Submit before ").append(AlarmScheduler.prettyDate(a.lastDate))
                          .append(d <= 3 ? " — only " + d + " day(s) left!" : ".").append("\n");
        }
        sb.append("\nTip: use the अ button in the in-app browser to read the official page in Hindi.");
        return sb.toString();
    }

    private static String summary(Alert a) {
        StringBuilder sb = new StringBuilder();
        sb.append("📌 Quick summary\n\n");
        sb.append("• What: ").append(a.title).append("\n");
        if (a.org.length() > 0) sb.append("• Who: ").append(a.org).append("\n");
        if (a.posts.length() > 0) sb.append("• Posts: ").append(a.posts).append("\n");
        if (a.qualification.length() > 0) sb.append("• Eligibility: ").append(a.qualification).append("\n");
        if (a.fee.length() > 0) sb.append("• Fee: ").append(a.fee).append("\n");
        if (a.lastDate.length() >= 10) {
            int d = AlarmScheduler.daysUntil(a.lastDate);
            if (d >= 0) sb.append("• Last date: ").append(AlarmScheduler.prettyDate(a.lastDate))
                          .append(" (").append(d).append(" days left)\n");
        }
        if (a.details.length() > 0) {
            String det = a.details.length() > 220 ? a.details.substring(0, 220) + "…" : a.details;
            sb.append("• Details: ").append(det).append("\n");
        }
        sb.append("\nAnswered LOCALLY — instant and offline. Add a free AI key in Settings for open questions.");
        return sb.toString();
    }

    private static String hindiDigest(Alert a) {
        StringBuilder sb = new StringBuilder();
        sb.append("📌 संक्षिप्त जानकारी (हिंदी में)\n\n");
        sb.append("• क्या है: ").append(a.title).append("\n");
        if (a.org.length() > 0) sb.append("• कौन: ").append(a.org).append("\n");
        if (a.posts.length() > 0) sb.append("• पद: ").append(a.posts).append("\n");
        if (a.qualification.length() > 0) sb.append("• योग्यता: ").append(a.qualification).append("\n");
        if (a.fee.length() > 0) sb.append("• शुल्क: ").append(a.fee).append("\n");
        if (a.lastDate.length() >= 10) {
            int d = AlarmScheduler.daysUntil(a.lastDate);
            if (d > 0) sb.append("• आवेदन की अंतिम तिथि: ").append(AlarmScheduler.prettyDate(a.lastDate))
                         .append(" (").append(d).append(" दिन बाकी)\n");
            else if (d == 0) sb.append("• आज ही अंतिम तिथि है — जल्दी करें!\n");
            else sb.append("• अंतिम तिथि निकल चुकी है।\n");
        }
        sb.append("\nपूरी जानकारी के लिए लिंक खोलें और अ button से पेज हिंदी में पढ़ें।");
        return sb.toString();
    }

    /** searches / deadline listings straight from the alert database */
    private static String dbAnswer(String s, Context c) {
        if (s.contains("notification") || s.contains("help") || s.contains("madad")) return null;
        Db db = new Db(c);
        if (s.contains("last date") || s.contains("kab hai") || s.contains("deadline")) {
            List<Alert> all = db.list("", "all", false);
            StringBuilder sb = new StringBuilder("\u23F3 Deadlines in your alerts:\n\n");
            int n = 0;
            for (Alert x : all) {
                if (x.lastDate.length() < 10) continue;
                int d = AlarmScheduler.daysUntil(x.lastDate);
                if (d < 0 || d > 30 || n >= 8) continue;
                sb.append("\u2022 ").append(x.title).append(" \u2014 ")
                  .append(AlarmScheduler.prettyDate(x.lastDate))
                  .append(" (").append(d).append(" days left)\n");
                n++;
            }
            if (n == 0) return "\u23F3 No deadlines in the next 30 days in your alerts right now.";
            sb.append("\nAnswered LOCALLY from your own alert data \u2014 instant & offline.");
            return sb.toString();
        }
        String term = pickTerm(s);
        if (term == null) return null;
        List<Alert> hits = db.list(term, "all", false);
        if (hits.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("\uD83D\uDD0E Found ").append(hits.size())
                .append(" in your alerts for \"").append(term).append("\":\n\n");
        int n = 0;
        for (Alert x : hits) {
            if (n >= 6) { sb.append("\u2026and more \u2014 type more of the title to narrow down."); break; }
            sb.append("\u2022 ").append(x.title);
            if (x.lastDate.length() >= 10 && AlarmScheduler.daysUntil(x.lastDate) >= 0)
                sb.append(" (last date ").append(AlarmScheduler.prettyDate(x.lastDate)).append(")");
            sb.append("\n");
            n++;
        }
        sb.append("\nOpen any alert and ask me about it directly.");
        return sb.toString();
    }

    /** pick the most meaningful search word from the question */
    private static String pickTerm(String s) {
        String[] stop = {"kya", "hai", "hain", "jobs", "job", "result", "results", "sarkari",
                "government", "govt", "latest", "about", "show", "list", "find", "search",
                "batao", "alert", "alerts", "the", "and", "for", "what", "when", "which",
                "news", "any", "please", "assistant", "local", "instant", "this", "that"};
        String best = null;
        for (String w : s.split("[^a-z0-9]+")) {
            if (w.length() < 4) continue;
            boolean skip = false;
            for (String st : stop) if (st.equals(w)) { skip = true; break; }
            if (skip) continue;
            if (best == null || w.length() > best.length()) best = w;
        }
        return best;
    }

    private static String norm(String q) {
        return q == null ? "" : q.toLowerCase().trim();
    }
}
