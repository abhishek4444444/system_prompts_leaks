package in.sarkarialert.app;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * SARKARI BRAIN — the app's own local AI. 100% on-device: a curated
 * knowledge base of Indian government schemes/exams + live answers built
 * from the phone's own alert database. No internet, no key, no delay.
 */
final class OfflineBrain {

    private OfflineBrain() { }

    static final String NAME = "Sarkari Brain";

    // ---------- quick chips for the GUI ----------

    static String[] quickChips() {
        return new String[]{"⏳ Deadlines", "🆕 Latest jobs", "📜 Schemes info",
                "🚂 Railway jobs", "❓ App help"};
    }

    // ---------- tiny knowledge base ----------

    private static final Object[][] KB = {
        {new String[]{"pm kisan", "pm-kisan", "kisan nidhi"},
         "📜 PM-KISAN (PM Kisan Samman Nidhi)\n\n• ₹6,000/year in 3 installments of ₹2,000 straight to farmers' bank accounts\n• Apply: pmkisan.gov.in → New Farmer Registration\n• Needs: Aadhaar, bank account linked, land records\n• Status check: 'Know Your Status' on the same site\n\nYe general jaankari hai — official portal pe latest notification zaroor padho."},
        {new String[]{"ayushman", "pmjay", "ayushman bharat"},
         "📜 Ayushman Bharat (PM-JAY)\n\n• ₹5 lakh/year free treatment per family in empanelled hospitals\n• Check eligibility: pmjay.gov.in → 'Am I Eligible?' (mobile + Aadhaar)\n• Golden card banao nearest CSC/empanelled hospital se\n• Cashless treatment at the hospital desk\n\nScheme state ke hisaab se thodi different hoti hai — official site pe confirm karo."},
        {new String[]{"scholarship", "nsp", "vidyasa"},
         "📜 National Scholarship Portal (NSP)\n\n• Pre-matric, post-matric, merit-cum-means — sab ek portal pe: scholarships.gov.in\n• Needs: Aadhaar, income certificate, caste certificate (if applicable), bank passbook, institute ID\n• OTR (One Time Registration) pehle banao\n• Renewal har saal same login se hota hai\n\nState scholarships alag portals pe bhi hote hain — apne state education board bhi check karo."},
        {new String[]{"ssc gd", "gd constable"},
         "📜 SSC GD Constable\n\n• Eligibility: 10th pass, 18–23 years\n• Exam: CBT (GK, Maths, Reasoning, Hindi/English) + PET/PST + Medical\n• Seats: BSF, CISF, CRPF, SSB, ITBP, AR, SSF mein\n• Apply online: ssc.gov.in — ek saal mein usually ek baar notification\n\nLatest dates ke liye app ke Jobs tab me SSC category filter karo."},
        {new String[]{"ssc cgl", "cgl"},
         "📜 SSC CGL (Combined Graduate Level)\n\n• Eligibility: graduation (any stream, most posts)\n• Age: mostly 18–32 (post-wise)\n• Rounds: Tier-1 CBT → Tier-2 CBT → skill test (some posts)\n• Posts: ASO, Income Tax Inspector, Auditor, Accountant, etc.\n• Apply: ssc.gov.in\n\nApp me SSC filter laga ke sab SSC alerts dekho."},
        {new String[]{"upsc", "civil service", "ias ", " ias", "ips"},
         "📜 UPSC Civil Services (IAS/IPS/IFS)\n\n• Eligibility: graduation, age 21–32 (attempts: 6 general)\n• Rounds: Prelims (June) → Mains (Sept) → Interview\n• Apply: upsc.gov.in / upsconline.nic.in\n• Prelims: GS + CSAT; Mains: 9 papers + optional subject\n\nUPSC ka sab kuch app ke UPSC chip me milega."},
        {new String[]{"ntpc", "group d", "rrb", "railway"},
         "🚂 Railway (RRB) Exams\n\n• RRB NTPC: graduate level — stations, accounts, traffic posts\n• RRB Group D (CEN): 10th/ITI — track maintainer, assistant posts\n• CBT → document verification (+ PET for Group D)\n• Apply: rrbapply.gov.in (ek hi portal, koi bhi RRB zone)\n\nRailway chip on home = saari railway alerts ek jagah."},
        {new String[]{"ibps", "bank po", "bank clerk", "sbi po", "banking"},
         "📜 Banking Exams (IBPS/SBI/RBI)\n\n• IBPS PO/Clerk, SBI PO/Clerk, RBI Grade B\n• Eligibility: graduation, age 20–30 (relaxations apply)\n• Rounds: Prelims → Mains → Interview (PO/Gr-B)\n• Apply: ibps.in, sbi.co.in/careers, rbi.org.in\n\nBanking chip se saare bank alerts dekho."},
        {new String[]{"agniveer", "agnipath"},
         "📜 Agnipath / Agniveer\n\n• 4-year service in Army/Navy/Air Force\n• Age: 17.5–21\n• Phase: online CBT → PET/PMT → medical\n• 25% batch ko regular service mein retention milta hai\n• Apply: joinindianarmy.nic.in, agneepatharmy sites\n\nApp me Defence filter lagao for alerts."},
        {new String[]{"atal pension", "apy "},
         "📜 Atal Pension Yojana (APY)\n\n• Guaranteed pension ₹1,000–₹5,000/month after 60\n• Age: 18–40, bank account zaroori\n• Contribution monthly/quarterly — jitni jaldi shuru, utna kam kharch\n• Apply: apna bank branch ya net-banking se\n\nPension related aur bhi schemes: app me Schemes chip dekho."},
        {new String[]{"ujjwala", "lpg connection"},
         "📜 PM Ujjwala Yojana\n\n• Free LPG gas connection to eligible women\n• Apply: pmuy.gov.in ya nearest gas agency\n• Needs: Aadhaar, ration card, bank account, self-declaration\n• Also gives first refill + stove support in many cases"},
        {new String[]{"pmay", "awas yojana", "housing"},
         "📜 PM Awas Yojana (PMAY-G urban & rural)\n\n• Financial help for pucca house (urban: credit-linked subsidy; rural: ₹1.2L+)\n• Eligibility check: pmaymis.gov.in / pmayg.nic.in\n• Aadhaar-linked application, income criteria apply"},
        {new String[]{"result"},
         "📄 Results kab aayenge?\n\n• Result dates exam body ke hisaab se fix hoti hain (SSC/UPSC/Railway alag timelines)\n• App ke ⏳ Deadlines brain-chip se pata karo kya-kya declared hai\n• Best trick: Watch tab me us board ka page add karo — result aate hi INSTANT notification milega\n• Admit card bhi isi tarah track hota hai"},
        {new String[]{"admit card", "hall ticket", "call letter"},
         "🎫 Admit Card\n\n• Admit cards exam se ~10–15 din pehle board ki website pe aate hain\n• App me Admit Card chip = sab admit-card alerts ek jagah\n• Watch tab me apne board ka page add karo to notification turant milega\n• Login/App-ID ready rakho, photo signature print-ready"},
    };

    // ---------- main entry ----------

    static String answer(String q, Alert a, Context c) {
        String s = norm(q);

        // --- small talk first ---
        if (isGreeting(s)) {
            return "Namaste! 🙏 Main " + NAME + " hoon — tera apna local AI, is phone ke andar hi rehta hoon.\n\n"
                    + "• Koi scheme/exam pooch (\"PM-Kisan kya hai?\", \"SSC GD eligibility?\")\n"
                    + "• Apne alerts ka data (\"deadlines\", \"latest jobs\", \"railway\")\n"
                    + "• App help (\"site add kaise karu?\")\n\n"
                    + "Na internet, na API key, na waiting — sab kuch instant. 🔥";
        }
        if (containsAny(s, "who are you", "tum kaun", "tumhara naam", "your name", "kaun ho", "tera naam")) {
            return "Main hoon " + NAME + " 🧠 — " + new AppInfo(c).who() + "\n\n"
                    + "Poora knowledge base + tere app ka data mere dimaag me hai — "
                    + "bina internet ke turant jawab.";
        }
        if (containsAny(s, "thank", "shukriya", "dhanyavaad", "great job")) {
            return " anytime! 😄 Aur kuch poochna hai to bata.";
        }
        if (containsAny(s, "what can you do", "kya kar sakte", "how to use", "help me", "features")) {
            return appHelp();
        }
        if ((s.contains("aaj") && (s.contains("date") || s.contains("din"))) || s.contains("today date")
                || s.contains("aaj ki tarikh")) {
            return "📅 Aaj hai " + new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH)
                    .format(new Date());
        }

        // --- alert-context answers ---
        if (a != null) {
            String r = alertAnswer(s, a);
            if (r != null) return r;
        }

        // --- app / navigation help ---
        if (containsAny(s, "notification", "notifications nahi", "alert nahi")) return notifHelp();
        if (containsAny(s, "add site", "website add", "site kaise", "watch kya", "koi site")) return siteHelp();
        if (containsAny(s, "battery", "battery drain")) return batteryHelp();
        if (containsAny(s, "translate", "hindi me kaise", "page hindi")) return translateHelp();
        if (containsAny(s, "server down", "site nahi khul", "down ho", "captcha")) return siteguardHelp();
        if (containsAny(s, "api key", "ai key", "cloud ai", "gemini key", "groq")) return keyHelp();

        // --- stats ---
        if (containsAny(s, "kitne alert", "kitni job", "kitni site", "total job", "status bata", "kitna data")) {
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
        if (s.contains("latest") || s.contains("new job") || s.contains("nayi job")
                || s.contains("jobs dikhao") || s.contains("kya aaya")) {
            return latest(c);
        }

        // --- generic DB search ---
        String term = pickTerm(s);
        if (term != null) {
            List<Alert> hits = new Db(c).list(term, "all", false);
            if (!hits.isEmpty()) return searchList(term, hits);
        }

        // --- graceful scope ---
        return "Is sawal ka jawab mere local dimaag me abhi nahi hai 🙈\n\n"
                + "Ye try kar:\n"
                + "• \"PM-Kisan kya hai?\" ya \"SSC GD eligibility?\"\n"
                + "• \"deadlines\" ya \"latest jobs\" — tere apne alerts se\n"
                + "• Koi alert kholo aur uske baare me poochho\n\n"
                + "Open sawalon ke liye ☁ Cloud chip daba kar Gemini/Groq use kar.";
    }

    // ---------- helpers used by the GUI ----------

    static String greeting() {
        return "Namaste! 🙏 Main " + NAME + " hoon — 100% local, 0% internet.\n\n"
                + "Schemes/exams ke facts, tere alerts ki deadlines, railway/banking lists, "
                + "app ki help — sab instant. Neeche chips daba kar shuru kar, ya seedha pooch!";
    }

    // ---------- alert-specific ----------

    private static String alertAnswer(String s, Alert a) {
        if (s.contains("last date") || s.contains("kab hai") || s.contains("kab tak")) {
            if (a.lastDate.length() < 10)
                return "⏳ Is alert me koi last date captured nahi hai — official link khol ke confirm kar.";
            int d = AlarmScheduler.daysUntil(a.lastDate);
            String p = AlarmScheduler.prettyDate(a.lastDate);
            if (d < 0) return "⏳ Last date " + p + " thi — iska window band ho chuka hai.";
            if (d == 0) return "⏳ AAJ hi last date hai (" + p + ")! Jaldi apply kar.";
            return "⏳ Last date: " + p + "\n⏰ " + d + " din baaki hain" + (d <= 3 ? " — jaldi kar!" : ".");
        }
        if (s.contains("fee") || s.contains("fees") || s.contains("shulk")) {
            return a.fee.length() > 0
                    ? "💰 Application fee:\n" + a.fee
                    : "💰 Fee is alert ke data me nahi hai — official notice ke fee section me dekh.";
        }
        if (s.contains("eligib") || s.contains("qualif") || s.contains("yogyata")) {
            return a.qualification.length() > 0
                    ? "🎓 Eligibility:\n" + a.qualification
                    : "🎓 Qualification capture nahi hua — official notification me detail table dekh.";
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
        StringBuilder sb = new StringBuilder("📝 Kaise apply karein:\n");
        sb.append("1. Detail screen → \"Open in app\" (SiteGuard hai, server down ho tab bhi chalega).\n");
        if (a.fee.length() > 0) sb.append("2. Fee ready rakho: ").append(a.fee).append("\n");
        else sb.append("2. Fee official notice me confirm kar.\n");
        if (a.qualification.length() > 0) sb.append("3. Documents: ").append(a.qualification).append(" ke proof.\n");
        else sb.append("3. Qualification certificates scan karke rakho.\n");
        if (a.lastDate.length() >= 10) {
            int d = AlarmScheduler.daysUntil(a.lastDate);
            if (d >= 0) sb.append("4. Submit before ")
                         .append(AlarmScheduler.prettyDate(a.lastDate))
                         .append(d <= 3 ? " — sirf " + d + " din bache hain!" : ".").append("\n");
        }
        sb.append("\nTip: browser me अ button se official page Hindi me padh sakta hai.");
        return sb.toString();
    }

    private static String summary(Alert a) {
        StringBuilder sb = new StringBuilder("📌 Quick summary\n\n");
        sb.append("• Kya: ").append(a.title).append("\n");
        if (a.org.length() > 0) sb.append("• Kaun: ").append(a.org).append("\n");
        if (a.posts.length() > 0) sb.append("• Posts: ").append(a.posts).append("\n");
        if (a.qualification.length() > 0) sb.append("• Eligibility: ").append(a.qualification).append("\n");
        if (a.fee.length() > 0) sb.append("• Fee: ").append(a.fee).append("\n");
        if (a.lastDate.length() >= 10) {
            int d = AlarmScheduler.daysUntil(a.lastDate);
            if (d >= 0) sb.append("• Last date: ").append(AlarmScheduler.prettyDate(a.lastDate))
                          .append(" (").append(d).append(" din baaki)\n");
        }
        sb.append("\nLocal answer — instant & offline.");
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
        sb.append("\nपूरी जानकारी के लिए लिंक खोलो — अ button से हिंदी में पढ़ो।");
        return sb.toString();
    }

    // ---------- app help ----------

    private static String appHelp() {
        return "Main ye sab kar sakta hoon 🧠\n\n"
                + "• 📜 Scheme/exam knowledge — \"PM-Kisan?\", \"SSC GD?\", \"UPSC?\"\n"
                + "• ⏳ Tere alerts ki deadlines — \"deadlines\"\n"
                + "• 🆕 Latest jobs / category lists — \"railway\", \"banking\"\n"
                + "• 🔍 Search apne data me — \"police bharti\"\n"
                + "• 📊 Stats — \"kitne alerts hain?\"\n"
                + "• Koi alert khol ke uske baare me pooch (fee/eligibility/apply)\n\n"
                + "Open sawal? ☁ Cloud chip se Gemini/Groq bhi use kar sakta hai.";
    }

    private static String notifHelp() {
        return "🔔 Notifications nahi aa rahi? Fix:\n\n"
                + "1. Settings → \"Open notification settings\" → allow all\n"
                + "2. Settings → \"Battery & app settings\" → battery optimization OFF\n"
                + "3. App internet ON hote hi turant check karti hai (battery-safe design)\n"
                + "4. Watch tab me sites add kar — naya content aate hi instant alert";
    }

    private static String siteHelp() {
        return "➕ Koi bhi sarkari site add karne ke liye:\n\n"
                + "1. Watch tab kholo\n2. \"Add website or feed\" dabao\n"
                + "3. URL paste karo (https://…)\n4. Bas! Naya content aate hi notification milega\n\n"
                + "Remove karna ho: site pe LONG-PRESS karo.";
    }

    private static String batteryHelp() {
        return "🔋 Battery ka scene zero hai, tension mat le:\n\n"
                + "• Koi background service nahi chalti — offline = 0 battery\n"
                + "• Internet ON hote hi system app ko 1 quick fetch ke liye jagata hai\n"
                + "• Backup check sirf unmetered WiFi par, din me\n"
                + "• Agar battery-saver pareshan kare: Settings → Battery & app settings";
    }

    private static String translateHelp() {
        return "🔤 Hindi me padhne ke 2 tareeke:\n\n"
                + "1. Browser (Open in app) me अ button — poora page Hindi\n"
                + "2. Home header ka अ — English text paste karke Hindi/Hinglish\n"
                + "3. \"Answer in Hindi\" bhi maang sakta hai jab alert khula ho (cloud mode)";
    }

    private static String siteguardHelp() {
        return "⚡ SiteGuard — server down ka solution:\n\n"
                + "• Result day pe server crash ho to app khud mirror se page laati hai\n"
                + "• Upar orange banner dikhe to samajh mirror mode chal raha hai\n"
                + "• \"Try original\" se wapas asli site\n"
                + "• Captcha/login cookies bhi preserve hoti hain, so login flows work karte hain";
    }

    private static String keyHelp() {
        return "🔑 Cloud AI (optional):\n\n"
                + "Settings → AI key me ye kisi bhi provider ki FREE key paste kar:\n"
                + "• Groq: console.groq.com (gsk_… — sabse fast, free)\n"
                + "• Gemini: aistudio.google.com (AIza…/AQ.…)\n"
                + "• DeepSeek: platform.deepseek.com (sk-…)\n"
                + "• Multiple keys comma se — limit aaye to auto-switch!\n"
                + "Provider format se auto-detect hota hai.";
    }

    // ---------- data answers ----------

    private static String stats(Context c) {
        Db db = new Db(c);
        List<WatchedSite> sites = db.listSites();
        String[] codes = {"railway", "ssc", "upsc", "banking", "defence", "police",
                "teaching", "state", "psu", "scheme", "scholarship", "result", "admit"};
        StringBuilder sb = new StringBuilder("📊 Tera data:\n\n");
        sb.append("• Alerts: ").append(db.count()).append("\n");
        sb.append("• Watched sites: ").append(sites.size()).append("\n");
        StringBuilder cats = new StringBuilder();
        for (String code : codes) {
            int n = db.list("", code, false).size();
            if (n > 0) cats.append(cats.length() > 0 ? ", " : "").append(code).append(": ").append(n);
        }
        if (cats.length() > 0) sb.append("• Breakdown — ").append(cats).append("\n");
        sb.append("\nAur deep jaanna hai to \"latest jobs\" ya \"deadlines\" maang!");
        return sb.toString();
    }

    private static String deadlines(Context c) {
        List<Alert> all = new Db(c).list("", "all", false);
        StringBuilder sb = new StringBuilder("⏳ Agle 30 din ki deadlines:\n\n");
        int n = 0;
        for (Alert x : all) {
            if (x.lastDate.length() < 10) continue;
            int d = AlarmScheduler.daysUntil(x.lastDate);
            if (d < 0 || d > 30 || n >= 8) continue;
            sb.append("• ").append(x.title).append("\n  ")
              .append(AlarmScheduler.prettyDate(x.lastDate)).append(" — ")
              .append(d == 0 ? "AAJ last date!" : d + " din baaki").append("\n");
            n++;
        }
        if (n == 0) return "⏳ Agle 30 din me koi deadline nahi hai tere alerts me. \"Refresh\" ya Watch tab me aur sites add kar.";
        sb.append("\nKisi bhi par apply karna ho to Jobs tab me kholo.");
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
                sb.append("  (").append(AlarmScheduler.prettyDate(x.lastDate)).append(" tak)");
            sb.append("\n");
            n++;
        }
        if (n == 0) return "🆕 Abhi koi alert nahi hai — header ka refresh button daba ya Watch tab me sites add kar.";
        sb.append("\nDetail ke liye Jobs tab me kholo — ya khol ke mujhse pooch!");
        return sb.toString();
    }

    private static String categoryList(Context c, String code) {
        List<Alert> hits = new Db(c).list("", code, false);
        String name = Alert.categoryName(code, c);
        if (hits.isEmpty())
            return "Abhi " + name + " category me kuch nahi hai. Refresh kar ya Watch tab me relevant site add kar — aate hi batata rahunga.";
        StringBuilder sb = new StringBuilder(name + " — ").append(hits.size()).append(" alerts mile:\n\n");
        int n = 0;
        for (Alert x : hits) {
            if (n >= 7) { sb.append("…aur bhi hain — ").append(name).append(" chip laga ke poora dekh."); break; }
            sb.append("• ").append(x.title);
            if (x.lastDate.length() >= 10 && AlarmScheduler.daysUntil(x.lastDate) >= 0)
                sb.append("  (").append(AlarmScheduler.prettyDate(x.lastDate)).append(" tak)");
            sb.append("\n");
            n++;
        }
        return sb.toString();
    }

    private static String searchList(String term, List<Alert> hits) {
        StringBuilder sb = new StringBuilder("🔍 \"").append(term).append("\" ke ").append(hits.size())
                .append(" results tere data me:\n\n");
        int n = 0;
        for (Alert x : hits) {
            if (n >= 6) { sb.append("…aur bhi — thoda specific likh."); break; }
            sb.append("• ").append(x.title);
            if (x.lastDate.length() >= 10 && AlarmScheduler.daysUntil(x.lastDate) >= 0)
                sb.append("  (").append(AlarmScheduler.prettyDate(x.lastDate)).append(" tak)");
            sb.append("\n");
            n++;
        }
        sb.append("\nKisi ko kholo aur uske baare me aur pooch!");
        return sb.toString();
    }

    // ---------- matching ----------

    private static String kbLookup(String s) {
        int bestScore = 0;
        String best = null;
        for (Object[] entry : KB) {
            String[] keys = (String[]) entry[0];
            int score = 0;
            for (String key : keys) {
                if (s.contains(key)) score += key.length();
            }
            if (score > bestScore) { bestScore = score; best = (String) entry[1]; }
        }
        return best;
    }

    private static String categoryIntent(String s) {
        if (!(s.contains("job") || s.contains("jobs") || s.contains("alert") || s.contains("list")
                || s.contains("dikhao") || s.contains("kitni") || s.contains("bharti"))) return null;
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
                || s.equals("namaskar") || s.contains("kaise ho") || s.contains("kya haal")
                || s.startsWith("hi ") || s.startsWith("hello ") || s.startsWith("hey ");
    }

    private static boolean containsAny(String s, String... keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
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

    /** tiny identity helper so the brain can describe itself with app context */
    private static class AppInfo {
        private final Context c;
        AppInfo(Context c) { this.c = c; }
        String who() {
            try {
                return c.getString(c.getResources().getIdentifier(
                        "app_name", "string", c.getPackageName())) + " ka built-in local AI";
            } catch (Exception e) {
                return "Sarkari Alert ka built-in local AI";
            }
        }
    }
}
