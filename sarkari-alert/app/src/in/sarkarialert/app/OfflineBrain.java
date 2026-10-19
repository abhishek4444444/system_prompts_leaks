package in.sarkarialert.app;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * SARKARI BRAIN — the app's own local AI. 100% on-device: a curated
 * knowledge base of Indian government schemes/exams, live answers built
 * from the phone's own alert database, AND a self-learning memory that
 * captures every cloud-AI exchange so the Brain gets smarter over time.
 */
final class OfflineBrain {

    static final String NAME = "Sarkari Brain";

    private OfflineBrain() { }

    // ---------- quick chips for the GUI ----------

    static String[] quickChips() {
        return new String[]{"⏳ Deadlines", "🆕 Latest jobs", "📜 Schemes info",
                "🚂 Railway jobs", "❓ App help", "🎯 Skills"};
    }

    // ---------- tiny knowledge base ----------

    private static final Object[][] KB = {
        {new String[]{"pm kisan", "pm-kisan", "kisan nidhi"},
         "📜 PM-KISAN (PM Kisan Samman Nidhi)\n\n• ₹6,000/year in 3 installments of ₹2,000, straight to the farmer's bank account\n• Apply: pmkisan.gov.in → New Farmer Registration\n• Needs: Aadhaar, bank account linked, land records\n• Status: 'Know Your Status' on the same portal\n\nThis is general info — always read the official notification before applying."},
        {new String[]{"ayushman", "pmjay", "ayushman bharat"},
         "📜 Ayushman Bharat (PM-JAY)\n\n• ₹5 lakh/year free treatment per family at empanelled hospitals\n• Check eligibility: pmjay.gov.in → 'Am I Eligible?' (mobile + Aadhaar)\n• Golden card: nearest CSC or empanelled hospital\n• Treatment is cashless at the hospital desk\n\nDetails vary slightly by state — confirm on the official site."},
        {new String[]{"scholarship", "nsp", "vidyasa"},
         "📜 National Scholarship Portal (NSP)\n\n• Pre-matric, post-matric and merit-cum-means schemes on one portal: scholarships.gov.in\n• Needs: Aadhaar, income certificate, caste certificate (if applicable), bank passbook, institute ID\n• Create the OTR (One Time Registration) first\n• Renewal every year with the same login\n\nState scholarships may sit on separate portals — check your state board too."},
        {new String[]{"ssc gd", "gd constable"},
         "📜 SSC GD Constable\n\n• Eligibility: 10th pass, age 18–23\n• Stages: CBT (GK, Maths, Reasoning, Hindi/English) → PET/PST → Medical\n• Forces: BSF, CISF, CRPF, SSB, ITBP, AR, SSF\n• Apply online: ssc.gov.in — usually one notification per year\n\nUse the SSC filter on the Jobs tab for every SSC alert."},
        {new String[]{"ssc cgl", "cgl"},
         "📜 SSC CGL (Combined Graduate Level)\n\n• Eligibility: graduation (most posts)\n• Age: mostly 18–32 (varies by post)\n• Stages: Tier-1 CBT → Tier-2 CBT → skill test for some posts\n• Posts: ASO, Income Tax Inspector, Auditor, Accountant and more\n• Apply: ssc.gov.in\n\nThe SSC filter chip shows all SSC alerts."},
        {new String[]{"upsc", "civil service", "ias ", " ias", "ips"},
         "📜 UPSC Civil Services (IAS/IPS/IFS)\n\n• Eligibility: graduation, age 21–32 (6 attempts for general category)\n• Stages: Prelims (June) → Mains (Sept) → Interview\n• Apply: upsc.gov.in / upsconline.nic.in\n• Prelims: GS + CSAT; Mains: 9 papers including the optional subject\n\nAll UPSC alerts live under the UPSC chip in the app."},
        {new String[]{"ntpc", "group d", "rrb", "railway"},
         "🚂 Railway (RRB) Exams\n\n• RRB NTPC: graduate level — stations, accounts, traffic posts\n• RRB Group D: 10th/ITI — track maintainer, assistant posts\n• Stages: CBT → document verification (+ PET for Group D)\n• Apply: rrbapply.gov.in (one portal for every RRB zone)\n\nThe Railway chip on the home tab gathers every railway alert."},
        {new String[]{"ibps", "bank po", "bank clerk", "sbi po", "banking"},
         "📜 Banking Exams (IBPS/SBI/RBI)\n\n• IBPS PO/Clerk, SBI PO/Clerk, RBI Grade B\n• Eligibility: graduation, age 20–30 (relaxations apply)\n• Stages: Prelims → Mains → Interview (PO / Grade B)\n• Apply: ibps.in, sbi.co.in/careers, rbi.org.in\n\nThe Banking chip collects all bank alerts."},
        {new String[]{"agniveer", "agnipath"},
         "📜 Agnipath / Agniveer\n\n• 4-year service in the Army/Navy/Air Force\n• Age: 17.5–21\n• Stages: online CBT → PET/PMT → medical\n• Up to 25% of each batch is retained in regular service\n• Apply: joinindianarmy.nic.in and the official Agniveer portals\n\nUse the Defence filter for related alerts."},
        {new String[]{"atal pension", "apy "},
         "📜 Atal Pension Yojana (APY)\n\n• Guaranteed pension of ₹1,000–₹5,000/month after 60\n• Age: 18–40, a bank account is required\n• Contributions are monthly/quarterly — start early, pay less\n• Apply: your bank branch or net-banking\n\nMore pension schemes under the Schemes chip."},
        {new String[]{"ujjwala", "lpg connection"},
         "📜 PM Ujjwala Yojana\n\n• Free LPG gas connection for eligible women\n• Apply: pmuy.gov.in or the nearest gas agency\n• Needs: Aadhaar, ration card, bank account, self-declaration\n• Many cases also include the first refill and a stove"},
        {new String[]{"pmay", "awas yojana", "housing"},
         "📜 PM Awas Yojana (urban & rural)\n\n• Financial help for a pucca house (urban: credit-linked subsidy; rural: ₹1.2 lakh+)\n• Eligibility check: pmaymis.gov.in / pmayg.nic.in\n• Aadhaar-linked application with income criteria"},
        {new String[]{"result"},
         "📄 When do results come out?\n\n• Result timelines are fixed by each exam body (SSC/UPSC/Railway differ)\n• Ask me \"deadlines\" to see what has been declared in your alerts\n• Best trick: add that board's page in the Watch tab — you get an INSTANT notification the moment the result drops\n• Admit cards are tracked the same way"},
        {new String[]{"ration card", "nfsa", "apl", "bpl"},
         "📜 Ration Card (NFSA)\n\n• Types: APL, BPL, AAY (Antyodaya) — eligibility depends on state rules\n• Apply through your state's food & civil supplies portal, or the nearest CSC\n• Needs: Aadhaar, address proof, income details, family photo\n• Portability: you can use a NFSA ration card in any state (One Nation One Ration Card)\n\nSearch \"<your state> NFSA ration card apply\" for the exact state portal."},
        {new String[]{"voter id", "voter card", "epic"},
         "📜 Voter ID (EPIC)\n\n• Eligibility: Indian citizen, 18+ on the qualifying date\n• New registration: Form 6 at voters.eci.gov.in or the Voter Helpline App\n• Needs: age proof, address proof, photo\n• Corrections: Form 8; track status on the same portal\n• Digital copy is available as e-EPIC"},
        {new String[]{"pan card", "pan "},
         "📜 PAN Card\n\n• Instant FREE e-PAN with Aadhaar (if your mobile is Aadhaar-linked): onlineservices.nsdl.com or the Income-tax e-filing portal\n• Physical card: Form 49A via NSDL/UTIITSL (₹ ~110 in India)\n• Needs: Aadhaar e-KYC, photo, signature\n• Correction/reprint flows exist on the same portals"},
        {new String[]{"aadhaar", "uidai", "aadhar"},
         "📜 Aadhaar (UIDAI)\n\n• Enrolment/update: any Aadhaar Seva Kendra (book a slot at appointments.uidai.gov.in)\n• Address update: possible ONLINE at myaadhaar.uidai.gov.in with valid documents\n• e-Aadhaar download: uidai.gov.in (Aadhaar number + OTP)\n• mAadhaar app carries your digital ID\n\nNever share your Aadhaar OTP with anyone."},
        {new String[]{"admit card", "hall ticket", "call letter"},
         "🎫 Admit Card\n\n• Admit cards usually arrive 10–15 days before the exam on the board's website\n• The Admit Card chip in the app gathers all admit-card alerts\n• Add your board's page in the Watch tab to get notified instantly\n• Keep your registration ID, photo and signature print-ready"},
    };

    // ---------- main entry ----------

    static String answer(String q, Alert a, Context c) {
        String s = norm(q);

        // --- user skills: custom, overrides everything else ---
        String sk = skillAnswer(s, c);
        if (sk != null) return sk;

        // --- small talk ---
        if (isGreeting(s)) {
            int mem = Prefs.learned(c).length();
            StringBuilder g = new StringBuilder();
            g.append("Hello! 🙏 I am ").append(NAME).append(" — your own local AI, running entirely inside this phone.\n\n");
            g.append("• Ask about schemes/exams (\"What is PM-Kisan?\", \"SSC GD eligibility?\")\n");
            g.append("• Your alerts' data (\"deadlines\", \"latest jobs\", \"railway\")\n");
            g.append("• App help (\"How do I add a site?\")\n");
            if (mem > 0) g.append("• I remember ").append(mem).append(" answers you taught me through cloud chats 🧠\n");
            g.append("\nNo internet, no API key, no waiting — everything is instant. 🔥");
            return g.toString();
        }
        if (containsAny(s, "who are you", "your name", "what are you")) {
            return "I am " + NAME + " 🧠 — the built-in local AI of Sarkari Alert.\n\n"
                    + "A full knowledge base + this app's own data live in my brain — "
                    + "and I learn new answers every time you use the cloud chat. "
                    + "No internet needed to answer.";
        }
        if (containsAny(s, "thank", "great job", "awesome")) {
            return "Anytime! 😄 Ask me anything else.";
        }
        if (containsAny(s, "what can you do", "how to use you", "features", "help me")) {
            return appHelp();
        }
        if ((s.contains("aaj") && (s.contains("date") || s.contains("din"))) || s.contains("today date")
                || s.contains("aaj ki tarikh")) {
            return "📅 Today is " + new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH)
                    .format(new Date());
        }

        // --- self-learned memory (from cloud chats) ---
        String mem = memoryRecall(s, c);
        if (mem != null) return mem;

        // --- alert-context answers ---
        if (a != null) {
            String r = alertAnswer(s, a);
            if (r != null) return r;
        }

        // --- app / navigation help ---
        if (containsAny(s, "notification", "alert nahi")) return notifHelp();
        if (containsAny(s, "add site", "website add", "site kaise", "watch tab")) return siteHelp();
        if (containsAny(s, "battery")) return batteryHelp();
        if (containsAny(s, "translate", "hindi me kaise", "page hindi")) return translateHelp();
        if (containsAny(s, "server down", "site nahi khul", "captcha")) return siteguardHelp();
        if (containsAny(s, "api key", "ai key", "cloud ai", "gemini key", "groq")) return keyHelp();

        // --- stats ---
        if (containsAny(s, "how many alert", "kitne alert", "kitni job", "total job", "stats", "kitna data")) {
            return stats(c);
        }

        // --- deadlines (whole DB) ---
        if (s.contains("deadline") || ((s.contains("last date") || s.contains("kab tak")) && a == null)) {
            return deadlines(c);
        }

        // --- category listing ---
        String cat = categoryIntent(s);
        if (cat != null) return categoryList(c, cat);

        // --- knowledge base ---
        String kb = kbLookup(s);
        if (kb != null) return kb;

        // --- latest jobs ---
        if (s.contains("latest") || s.contains("new job") || s.contains("jobs dikhao") || s.contains("kya aaya")) {
            return latest(c);
        }

        // --- generic DB search ---
        String term = pickTerm(s);
        if (term != null) {
            List<Alert> hits = new Db(c).list(term, "all", false);
            if (!hits.isEmpty()) return searchList(term, hits);
        }

        // --- graceful scope ---
        return "I don't have that in my local brain yet 🙈\n\n"
                + "Try:\n"
                + "• \"What is PM-Kisan?\" or \"SSC GD eligibility?\"\n"
                + "• \"deadlines\" or \"latest jobs\" — from your own alerts\n"
                + "• Open any alert and ask about it\n\n"
                + "For open questions tap ☁ Cloud — and here's the trick: whatever the cloud AI answers, "
                + "I LEARN and remember forever, so you can ask me offline next time. 🧠";
    }

    // ---------- helpers used by the GUI ----------

    static String greeting() {
        return "Hello! 🙏 I am " + NAME + " — 100% local, 0% internet.\n\n"
                + "Scheme/exam facts, your alerts' deadlines, category lists, app help — all instant. "
                + "And every cloud-chat answer trains my memory for later. "
                + "Tap a chip below or just ask!";
    }

    // ---------- self-learning memory ----------

    /** best-effort recall of an answer the cloud AI taught this brain earlier */
    private static String memoryRecall(String s, Context c) {
        org.json.JSONArray arr = Prefs.learned(c);
        if (arr.length() == 0 || s.length() < 4) return null;
        Set<String> qTokens = tokens(s);
        if (qTokens.isEmpty()) return null;
        String bestQ = null, bestA = null;
        double bestScore = 0;
        for (int i = 0; i < arr.length(); i++) {
            org.json.JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Set<String> mem = tokens(o.optString("q", ""));
            if (mem.isEmpty()) continue;
            int overlap = 0;
            for (String t : qTokens) if (mem.contains(t)) overlap++;
            double score = (double) overlap / qTokens.size();
            if (score > bestScore) { bestScore = score; bestQ = o.optString("q"); bestA = o.optString("a"); }
        }
        if (bestScore >= 0.6 && bestA != null && bestA.trim().length() > 0) {
            return "🧠 From my learned memory (you asked this in a cloud chat before):\n\n" + bestA.trim()
                    + "\n\n— Learned question: \"" + bestQ + "\"";
        }
        return null;
    }

    // ---------- alert-specific ----------

    private static String alertAnswer(String s, Alert a) {
        if (s.contains("last date") || s.contains("kab hai") || s.contains("kab tak")) {
            if (a.lastDate.length() < 10)
                return "⏳ No last date was captured for this alert — open the official link to confirm the schedule.";
            int d = AlarmScheduler.daysUntil(a.lastDate);
            String p = AlarmScheduler.prettyDate(a.lastDate);
            if (d < 0) return "⏳ The last date was " + p + " — the application window has closed.";
            if (d == 0) return "⏳ TODAY is the last date (" + p + ")! Apply as soon as possible.";
            return "⏳ Last date: " + p + "\n⏰ " + d + " days left" + (d <= 3 ? " — hurry!" : ".");
        }
        if (s.contains("fee") || s.contains("fees") || s.contains("shulk")) {
            return a.fee.length() > 0
                    ? "💰 Application fee:\n" + a.fee
                    : "💰 The fee isn't captured in this alert — check the fee section of the official notice.";
        }
        if (s.contains("eligib") || s.contains("qualif") || s.contains("yogyata")) {
            return a.qualification.length() > 0
                    ? "🎓 Eligibility:\n" + a.qualification
                    : "🎓 Qualification wasn't captured — see the detailed table in the official notification.";
        }
        if (s.contains("apply") || s.contains("kaise") || s.contains("how to")) {
            return applySteps(a);
        }
        if (s.contains("summary") || s.contains("short me") || s.contains("summarize")
                || s.contains("what is this") || s.contains("kya hai") || s.contains("samjha")) {
            return summary(a);
        }
        if (s.contains("hindi") && !s.contains("kaise")) {
            return hindiDigest(a);
        }
        return null;
    }

    private static String applySteps(Alert a) {
        StringBuilder sb = new StringBuilder("📝 How to apply:\n");
        sb.append("1. Detail screen → \"Open in app\" (SiteGuard keeps it working even if the server is down).\n");
        if (a.fee.length() > 0) sb.append("2. Keep the fee ready: ").append(a.fee).append("\n");
        else sb.append("2. Confirm the fee in the official notice.\n");
        if (a.qualification.length() > 0) sb.append("3. Documents: proof of ").append(a.qualification).append("\n");
        else sb.append("3. Keep scanned copies of your qualification certificates.\n");
        if (a.lastDate.length() >= 10) {
            int d = AlarmScheduler.daysUntil(a.lastDate);
            if (d >= 0) sb.append("4. Submit before ")
                         .append(AlarmScheduler.prettyDate(a.lastDate))
                         .append(d <= 3 ? " — only " + d + " day(s) left!" : ".").append("\n");
        }
        sb.append("\nTip: the अ button in the in-app browser reads the official page in Hindi.");
        return sb.toString();
    }

    private static String summary(Alert a) {
        StringBuilder sb = new StringBuilder("📌 Quick summary\n\n");
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
        sb.append("\nAnswered locally — instant and offline.");
        return sb.toString();
    }

    private static String hindiDigest(Alert a) {
        StringBuilder sb = new StringBuilder("📌 संक्षिप्त जानकारी\n\n");
        sb.append("• क्या: ").append(a.title).append("\n");
        if (a.org.length() > 0) sb.append("• कौन: ").append(a.org).append("\n");
        if (a.posts.length() > 0) sb.append("• पद: ").append(a.posts).append("\n");
        if (a.qualification.length() > 0) sb.append("• योग्यता: ").append(a.qualification).append("\n");
        if (a.fee.length() > 0) sb.append("• शुल्क: ").append(a.fee).append("\n");
        if (a.lastDate.length() >= 10) {
            int d = AlarmScheduler.daysUntil(a.lastDate);
            if (d > 0) sb.append("• अंतिम तिथि: ").append(AlarmScheduler.prettyDate(a.lastDate))
                         .append(" (").append(d).append(" दिन बाकी)\n");
            else if (d == 0) sb.append("• आज ही अंतिम तिथि है!\n");
        }
        sb.append("\nपूरी जानकारी के लिए लिंक खोलें — अ button से हिंदी में पढ़ें।");
        return sb.toString();
    }

    // ---------- app help ----------

    private static String appHelp() {
        return "Here's what I can do 🧠\n\n"
                + "• 📜 Scheme/exam knowledge — \"What is PM-Kisan?\", \"SSC GD?\", \"UPSC?\"\n"
                + "• ⏳ Deadlines from your alerts — \"deadlines\"\n"
                + "• 🆕 Latest jobs and category lists — \"railway\", \"banking\"\n"
                + "• 🔍 Search your own data — \"police bharti\"\n"
                + "• 📊 Stats — \"how many alerts do I have?\"\n"
                + "• 🎯 Recall the skills you installed\n"
                + "• 🧠 Remember answers you got from cloud chats — ask me offline later\n\n"
                + "Open any alert and I can explain its fee, eligibility and apply steps.";
    }

    private static String notifHelp() {
        return "🔔 Notifications not arriving? Fix it in one minute:\n\n"
                + "1. Settings → \"Open notification settings\" → allow everything\n"
                + "2. Settings → \"Battery & app settings\" → turn OFF battery optimization for this app\n"
                + "3. The app checks the moment your internet turns on — battery-safe by design\n"
                + "4. Add more sites in the Watch tab for wider coverage\n"
                + "5. New alerts now play a sound — check the phone's media volume too";
    }

    private static String siteHelp() {
        return "➕ Add any government site:\n\n"
                + "1. Open the Watch tab\n2. Tap \"Add website or feed\"\n"
                + "3. Paste the URL (https://…)\n4. Done — you get a notification the moment something new appears\n\n"
                + "To remove: LONG-PRESS the site.";
    }

    private static String batteryHelp() {
        return "🔋 Battery usage is zero by design:\n\n"
                + "• No background service runs — offline means 0 battery\n"
                + "• The system wakes the app for one quick fetch when internet turns on\n"
                + "• The backup check runs only on unmetered Wi-Fi\n"
                + "• If a battery-saver interferes: Settings → Battery & app settings";
    }

    private static String translateHelp() {
        return "🔤 Three ways to read in Hindi:\n\n"
                + "1. In the browser (\"Open in app\") tap अ — the whole page translates\n"
                + "2. The अ button on the home header — paste English text, get Hindi/Hinglish\n"
                + "3. With an alert open, ask \"Answer in Hindi\" (cloud mode)";
    }

    private static String siteguardHelp() {
        return "⚡ SiteGuard — the server-down solution:\n\n"
                + "• If a result-day server crashes, the app automatically loads a mirror of the page\n"
                + "• An orange banner means mirror mode is on\n"
                + "• \"Try original\" switches back to the real site\n"
                + "• Captcha and login cookies are preserved, so login flows keep working";
    }

    private static String keyHelp() {
        return "🔑 Cloud AI (optional):\n\n"
                + "Settings → AI key accepts any of these FREE keys:\n"
                + "• Groq: console.groq.com (gsk_… — fastest)\n"
                + "• Gemini: aistudio.google.com (AIza…/AQ.…)\n"
                + "• DeepSeek: platform.deepseek.com (sk-…)\n"
                + "• Multiple keys comma-separated auto-switch on limits\n"
                + "The provider is detected from the key format. And remember: "
                + "every cloud answer trains my local memory. 🧠";
    }

    // ---------- data answers ----------

    private static String stats(Context c) {
        Db db = new Db(c);
        List<WatchedSite> sites = db.listSites();
        String[] codes = {"railway", "ssc", "upsc", "banking", "defence", "police",
                "teaching", "state", "psu", "scheme", "scholarship", "result", "admit"};
        StringBuilder sb = new StringBuilder("📊 Your data:\n\n");
        sb.append("• Alerts: ").append(db.count()).append("\n");
        sb.append("• Watched sites: ").append(sites.size()).append("\n");
        StringBuilder cats = new StringBuilder();
        for (String code : codes) {
            int n = db.list("", code, false).size();
            if (n > 0) cats.append(cats.length() > 0 ? ", " : "").append(code).append(": ").append(n);
        }
        if (cats.length() > 0) sb.append("• Breakdown — ").append(cats).append("\n");
        sb.append("• Learned answers in memory: ").append(Prefs.learned(c).length()).append("\n");
        sb.append("\nAsk \"latest jobs\" or \"deadlines\" to dig deeper.");
        return sb.toString();
    }

    private static String deadlines(Context c) {
        List<Alert> all = new Db(c).list("", "all", false);
        StringBuilder sb = new StringBuilder("⏳ Deadlines in the next 30 days:\n\n");
        int n = 0;
        for (Alert x : all) {
            if (x.lastDate.length() < 10) continue;
            int d = AlarmScheduler.daysUntil(x.lastDate);
            if (d < 0 || d > 30 || n >= 8) continue;
            sb.append("• ").append(x.title).append("\n  ")
              .append(AlarmScheduler.prettyDate(x.lastDate)).append(" — ")
              .append(d == 0 ? "TODAY is the last date!" : d + " days left").append("\n");
            n++;
        }
        if (n == 0) return "⏳ No deadlines in the next 30 days in your alerts. Hit Refresh or add more sites in the Watch tab.";
        sb.append("\nOpen any of them on the Jobs tab to apply.");
        return sb.toString();
    }

    private static String latest(Context c) {
        List<Alert> all = new Db(c).list("", "all", false);
        StringBuilder sb = new StringBuilder("🆕 Latest alerts:\n\n");
        int n = 0;
        for (Alert x : all) {
            if (n >= 7) break;
            sb.append("• ").append(x.title);
            if (x.lastDate.length() >= 10 && AlarmScheduler.daysUntil(x.lastDate) >= 0)
                sb.append("  (till ").append(AlarmScheduler.prettyDate(x.lastDate)).append(")");
            sb.append("\n");
            n++;
        }
        if (n == 0) return "🆕 No alerts yet — tap Refresh in the header or add sites in the Watch tab.";
        sb.append("\nOpen them on the Jobs tab — or open one and ask me about it!");
        return sb.toString();
    }

    private static String categoryList(Context c, String code) {
        List<Alert> hits = new Db(c).list("", code, false);
        String name = Alert.categoryName(code, c);
        if (hits.isEmpty())
            return "Nothing in the " + name + " category right now. Refresh, or add a relevant site in the Watch tab — I'll tell you the moment something lands.";
        StringBuilder sb = new StringBuilder(name + " — ").append(hits.size()).append(" alerts found:\n\n");
        int n = 0;
        for (Alert x : hits) {
            if (n >= 7) { sb.append("…and more — tap the ").append(name).append(" chip on the home tab to see everything."); break; }
            sb.append("• ").append(x.title);
            if (x.lastDate.length() >= 10 && AlarmScheduler.daysUntil(x.lastDate) >= 0)
                sb.append("  (till ").append(AlarmScheduler.prettyDate(x.lastDate)).append(")");
            sb.append("\n");
            n++;
        }
        return sb.toString();
    }

    private static String searchList(String term, List<Alert> hits) {
        StringBuilder sb = new StringBuilder("🔍 ").append(hits.size())
                .append(" results for \"").append(term).append("\" in your data:\n\n");
        int n = 0;
        for (Alert x : hits) {
            if (n >= 6) { sb.append("…and more — type a more specific word."); break; }
            sb.append("• ").append(x.title);
            if (x.lastDate.length() >= 10 && AlarmScheduler.daysUntil(x.lastDate) >= 0)
                sb.append("  (till ").append(AlarmScheduler.prettyDate(x.lastDate)).append(")");
            sb.append("\n");
            n++;
        }
        sb.append("\nOpen any of them and ask me more!");
        return sb.toString();
    }

    // ---------- user skills (local recall) ----------

    private static String skillAnswer(String s, Context c) {
        org.json.JSONArray arr = Prefs.skills(c);
        StringBuilder list = null;
        for (int i = 0; i < arr.length(); i++) {
            org.json.JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String name = o.optString("name", "");
            boolean enabled = o.optBoolean("enabled", true);
            if (s.contains("skill")) {
                if (list == null) list = new StringBuilder("🎯 Your installed skills:\n\n");
                list.append("• ").append(name).append(enabled ? "" : " (off)").append("\n");
            }
            if (!enabled || name.length() == 0) continue;
            String low = name.toLowerCase();
            boolean hit = s.contains(low);
            if (!hit) {
                for (String tok : low.split("[^a-z0-9]+")) {
                    if (tok.length() >= 3 && !"the".equals(tok) && s.contains(tok)) { hit = true; break; }
                }
            }
            if (hit) {
                return "🎯 " + name + " (your skill):\n\n" + o.optString("content", "")
                        + "\n\n— The Cloud AI (☁ chip) fully FOLLOWS this skill in every answer.";
            }
        }
        if (list != null) {
            list.append("\nAdd or edit them via the 🎯 Skills chip. Cloud AI follows them; "
                    + "I recall them offline.");
            return list.toString();
        }
        return null;
    }

    // ---------- matching ----------

    private static String kbLookup(String s) {
        Set<String> q = tokens(s);
        int bestScore = 0;
        String best = null;
        for (Object[] entry : KB) {
            String[] keys = (String[]) entry[0];
            int score = 0;
            for (String key : keys) {
                if (s.contains(key)) score += key.length();
                else {
                    for (String t : tokens(key)) {
                        if (q.contains(t) && t.length() >= 4) score += t.length();
                    }
                }
            }
            if (score > bestScore) { bestScore = score; best = (String) entry[1]; }
        }
        return best;
    }

    private static String categoryIntent(String s) {
        if (!(s.contains("job") || s.contains("jobs") || s.contains("alert") || s.contains("list")
                || s.contains("show") || s.contains("bharti"))) return null;
        String[][] map = {{"railway", "railway"}, {"ssc", "ssc"}, {"upsc", "upsc"},
                {"bank", "banking"}, {"defence", "defence"}, {"army", "defence"},
                {"police", "police"}, {"teach", "teaching"}, {"state", "state"}, {"psu", "psu"},
                {"scheme", "scheme"}, {"scholarship", "scholarship"}, {"result", "result"},
                {"admit", "admit"}};
        for (String[] m : map) if (s.contains(m[0])) return m[1];
        return null;
    }

    private static boolean isGreeting(String s) {
        return s.equals("hi") || s.equals("hello") || s.equals("hey") || s.equals("namaste")
                || s.equals("namaskar") || s.contains("how are you") || s.contains("kaise ho")
                || s.contains("kya haal") || s.startsWith("hi ") || s.startsWith("hello ")
                || s.startsWith("hey ");
    }

    private static boolean containsAny(String s, String... keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }

    private static Set<String> tokens(String s) {
        Set<String> out = new HashSet<String>();
        String[] stop = {"the", "and", "for", "what", "when", "which", "this", "that", "how",
                "you", "your", "are", "can", "does", "did", "with", "about", "kya", "hai",
                "hain", "kaise", "batao", "please", "tell"};
        for (String w : s.toLowerCase().split("[^a-z0-9]+")) {
            if (w.length() < 3) continue;
            boolean skip = false;
            for (String st : stop) if (st.equals(w)) { skip = true; break; }
            if (!skip) out.add(w);
        }
        return out;
    }

    private static String pickTerm(String s) {
        String[] stop = {"kya", "hai", "hain", "jobs", "job", "result", "results", "sarkari",
                "government", "govt", "latest", "about", "show", "list", "find", "search",
                "batao", "alert", "alerts", "the", "and", "for", "what", "when", "which",
                "news", "any", "please", "assistant", "local", "instant", "this", "that",
                "brain", "tell", "muje", "mujhe"};
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
