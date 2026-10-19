package in.sarkarialert.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Translator built on Google's public "gtx" web endpoint — no API key needed.
 * Modes: "hi" = English to Hindi, "en" = to English, "rm" = Hindi in Roman
 * letters (Hinglish) using Google's romanization data.
 */
final class Translate {

    interface Cb { void done(String result, String detected, String err); }

    static void run(final String text, final String mode, final Cb cb) {
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    List<String> pieces = chunk(text);
                    StringBuilder out = new StringBuilder();
                    String detected = "";
                    for (int c = 0; c < pieces.size(); c++) {
                        String tl = mode.equals("en") ? "en" : "hi";
                        String extra = mode.equals("rm") ? "&dt=t&dt=rm" : "&dt=t";
                        String url = "https://translate.googleapis.com/translate_a/single?client=gtx"
                                + "&sl=auto&tl=" + tl + extra
                                + "&q=" + URLEncoder.encode(pieces.get(c), "UTF-8");
                        JSONArray root = new JSONArray(WebApi.get(url));
                        JSONArray segs = root.getJSONArray(0);
                        StringBuilder part = new StringBuilder();
                        if (mode.equals("rm")) {
                            // response layout (verified live):
                            //   [trans, orig, null, null, wordCount]  <- dt=t sentence
                            //   [null, null, "roman dost"]            <- dt=rm translit entry
                            // collect only the translit entries; fall back to the
                            // translated text if Google returned none
                            StringBuilder roman = new StringBuilder();
                            StringBuilder plain = new StringBuilder();
                            for (int i = 0; i < segs.length(); i++) {
                                JSONArray seg = segs.getJSONArray(i);
                                if (seg.isNull(0)) {
                                    for (int k = 2; k < seg.length(); k++) {
                                        if (!seg.isNull(k)) {
                                            Object v = seg.get(k);
                                            if (v instanceof String) {
                                                roman.append((String) v).append(' ');
                                                break;
                                            }
                                        }
                                    }
                                } else if (!seg.isNull(0)) {
                                    Object v = seg.get(0);
                                    if (v instanceof String) plain.append((String) v);
                                }
                            }
                            if (roman.length() > 0) {
                                part.append(roman.toString().replaceAll("\\s+", " ").trim());
                            } else {
                                part.append(plain);
                            }
                        } else {
                            for (int i = 0; i < segs.length(); i++) {
                                JSONArray seg = segs.getJSONArray(i);
                                if (!seg.isNull(0)) part.append(seg.getString(0));
                            }
                        }
                        if (root.length() > 2 && !root.isNull(2)) detected = root.getString(2);
                        out.append(part);
                        if (c < pieces.size() - 1) out.append("\n");
                    }
                    final String r = out.toString().trim();
                    final String d = detected;
                    ui.post(new Runnable() { @Override public void run() { cb.done(r, d, null); } });
                } catch (final Exception e) {
                    ui.post(new Runnable() { @Override public void run() { cb.done(null, null, e.getMessage()); } });
                }
            }
        }).start();
    }

    /** split into pieces of ~1300 chars, preferring line and sentence breaks */
    private static List<String> chunk(String t) {
        List<String> out = new ArrayList<String>();
        if (t.length() <= 1300) {
            out.add(t);
            return out;
        }
        int start = 0;
        while (start < t.length()) {
            int end = Math.min(start + 1300, t.length());
            if (end < t.length()) {
                int brk = Math.max(t.lastIndexOf('\n', end),
                        Math.max(t.lastIndexOf('\u0964', end), t.lastIndexOf('.', end)));
                if (brk > start + 200) end = brk + 1;
            }
            out.add(t.substring(start, end));
            start = end;
        }
        return out;
    }
}
