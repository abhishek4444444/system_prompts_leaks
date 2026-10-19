package in.sarkarialert.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Google AI Studio (Gemini) chat client.
 *
 * Models are DISCOVERED dynamically from the API's model list (so retires
 * like gemini-2.0-flash → gemini-3.8-flash never break the app), with a
 * static fallback chain. Auth uses the x-goog-api-key header required by
 * the new "AQ." authentication keys.
 */
final class Gemini {

    interface Cb { void done(String text, String err); }

    private static final String[] FALLBACK_MODELS = {
            "gemini-3.8-flash", "gemini-flash-latest", "gemini-2.5-flash"
    };
    private static String resolved;
    private static long resolvedAt;

    /** best cheap+fast chat model available for this key right now */
    private static synchronized String pickModel(String key) {
        if (resolved != null && System.currentTimeMillis() - resolvedAt < 6 * 3600000L) {
            return resolved;
        }
        try {
            JSONObject root = new JSONObject(WebApi.get(
                    "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200", key));
            JSONArray models = root.getJSONArray("models");
            String best = null;
            double bestVer = -1;
            for (int i = 0; i < models.length(); i++) {
                JSONObject m = models.getJSONObject(i);
                String name = m.optString("name", "");
                String low = name.toLowerCase();
                if (!low.contains("gemini") || !low.contains("flash")) continue;
                if (low.contains("embedding") || low.contains("tts") || low.contains("image")
                        || low.contains("audio") || low.contains("live") || low.contains("native")
                        || low.contains("aqa") || low.contains("gemma") || low.contains("thinking")
                        || low.contains("exp")) continue;
                JSONArray methods = m.optJSONArray("supportedGenerationMethods");
                boolean canGen = false;
                if (methods != null) {
                    for (int j = 0; j < methods.length(); j++) {
                        if ("generateContent".equals(methods.optString(j))) { canGen = true; break; }
                    }
                }
                if (!canGen) continue;
                double ver = verOf(name);
                if (low.contains("lite")) ver -= 0.01; // prefer full flash on a tie
                if (ver > bestVer) { bestVer = ver; best = name; }
            }
            if (best != null) {
                resolved = best.startsWith("models/") ? best.substring(7) : best;
                resolvedAt = System.currentTimeMillis();
            }
        } catch (Exception ignored) {
            // discovery failed — the static chain below still gets a chance
        }
        return resolved;
    }

    /** "models/gemini-3.8-flash" -> 3.8, "models/gemini-flash-latest" -> 0 */
    private static double verOf(String name) {
        try {
            String s = name.substring(name.indexOf("gemini-") + 7);
            StringBuilder num = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (Character.isDigit(ch) || (ch == '.' && num.length() > 0)) num.append(ch);
                else if (num.length() > 0) break;
            }
            if (num.length() > 0) return Double.parseDouble(num.toString());
        } catch (Exception ignored) { }
        return 0;
    }

    /** history = array of {role, text}, role is "user" or "model" */
    static void ask(final String key, final String system, final List<String[]> history, final Cb cb) {
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override public void run() {
                // candidate list: freshly discovered model first, then static fallbacks
                List<String> models = new ArrayList<String>();
                String discovered = pickModel(key);
                if (discovered != null) models.add(discovered);
                for (String f : FALLBACK_MODELS) {
                    if (!models.contains(f)) models.add(f);
                }

                String lastErr = null;
                boolean keyProblem = false;
                for (int attempt = 0; attempt < models.size(); attempt++) {
                    String model = models.get(attempt);
                    try {
                        JSONObject body = new JSONObject();
                        if (system != null && system.length() > 0) {
                            body.put("systemInstruction", new JSONObject().put("parts",
                                    new JSONArray().put(new JSONObject().put("text", system))));
                        }
                        JSONArray contents = new JSONArray();
                        for (String[] msg : history) {
                            contents.put(new JSONObject()
                                    .put("role", msg[0])
                                    .put("parts", new JSONArray().put(new JSONObject().put("text", msg[1]))));
                        }
                        body.put("contents", contents);
                        body.put("generationConfig", new JSONObject()
                                .put("maxOutputTokens", 8192)
                                .put("temperature", 0.8));
                        JSONObject root = new JSONObject(WebApi.post(
                                "https://generativelanguage.googleapis.com/v1beta/models/"
                                        + model + ":generateContent",
                                body.toString(), key));
                        JSONArray cands = root.getJSONArray("candidates");
                        StringBuilder sb = new StringBuilder();
                        JSONArray parts = cands.getJSONObject(0).getJSONObject("content").getJSONArray("parts");
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject p = parts.getJSONObject(i);
                            if (p.has("text")) sb.append(p.getString("text"));
                        }
                        String text = sb.toString();
                        if (text.trim().length() == 0) throw new Exception("Empty reply from model");
                        synchronized (Gemini.class) {
                            resolved = model;           // remember the one that worked
                            resolvedAt = System.currentTimeMillis();
                        }
                        uiDeliver(ui, text, null, cb);
                        return;
                    } catch (WebApi.HttpError he) {
                        String raw = he.rawMessage();
                        lastErr = "Model " + model + ": " + friendly(he, raw);
                        if (he.code == 404) {
                            if (model.equals(discovered)) resolved = null; // stale cache — re-discover
                            continue;                                       // retired — try next
                        }
                        if (he.code == 400 && raw != null && raw.toLowerCase().contains("api key")) {
                            keyProblem = true;
                            break;
                        }
                        if (he.code == 400) continue; // model rejected params — try next
                        break; // 403 / 429 / 5xx — another model will not help
                    } catch (Exception e) {
                        lastErr = e.getMessage();
                        break;
                    }
                }
                final String err = lastErr == null ? "Something went wrong" : lastErr;
                final boolean kp = keyProblem;
                ui.post(new Runnable() {
                    @Override public void run() {
                        cb.done(null, kp ? err + "\n\nOpen Settings → AI key and re-paste it." : err);
                    }
                });
            }
        }).start();
    }

    private static void uiDeliver(Handler ui, final String text, final String err, final Cb cb) {
        ui.post(new Runnable() {
            @Override public void run() { cb.done(text, err); }
        });
    }

    private static String friendly(WebApi.HttpError he, String raw) {
        String msg = raw == null ? he.getMessage() : raw;
        if (msg == null) msg = "";
        if (msg.length() > 240) msg = msg.substring(0, 240) + "…";
        if (he.code == 403) {
            return "Key rejected (403). Generate a fresh key in Google AI Studio and paste it in Settings.\n\n" + msg;
        }
        if (he.code == 429) {
            return "Free quota limit reached for now. Wait a minute and try again.\n\n" + msg;
        }
        return "error " + he.code + ". " + msg;
    }
}
