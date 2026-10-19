package in.sarkarialert.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Google AI Studio (Gemini) chat client. Uses the x-goog-api-key header
 * (required by the new "AQ." authentication keys) and a fallback chain of
 * model aliases so the extension keeps working when models retire.
 */
final class Gemini {

    interface Cb { void done(String text, String err); }

    private static final String[] MODELS = {
            "gemini-flash-latest", "gemini-2.5-flash",
            "gemini-flash-lite-latest", "gemini-2.0-flash"
    };
    private static int modelIdx = 0;

    static String modelName() {
        return MODELS[modelIdx];
    }

    /** history = array of {role, text}, role is "user" or "model" */
    static void ask(final String key, final String system, final List<String[]> history, final Cb cb) {
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override public void run() {
                String lastErr = null;
                boolean keyProblem = false;
                for (int attempt = 0; attempt < MODELS.length; attempt++) {
                    int m = (modelIdx + attempt) % MODELS.length;
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
                                        + MODELS[m] + ":generateContent",
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
                        modelIdx = m; // remember the model that worked
                        uiDeliver(ui, text, null, cb);
                        return;
                    } catch (WebApi.HttpError he) {
                        String raw = he.rawMessage();
                        lastErr = friendly(he, raw);
                        if (he.code == 404) continue;          // model gone — try the next one
                        if (he.code == 400) {
                            if (raw != null && raw.toLowerCase().contains("api key")) {
                                keyProblem = true;             // bad key — stop immediately
                                break;
                            }
                            continue;                          // maybe model rejects params — try next
                        }
                        break; // 403 / 429 / 5xx — retrying another model will not help
                    } catch (Exception e) {
                        lastErr = e.getMessage();
                        break;
                    }
                }
                final String err = lastErr == null ? "Something went wrong" : lastErr;
                final boolean kp = keyProblem;
                ui.post(new Runnable() {
                    @Override public void run() { cb.done(null, kp ? err + "\n\nOpen Settings → AI key and re-paste it." : err); }
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
        if (he.code == 404 || he.code == 400) {
            return "Model unavailable (" + he.code + "). " + msg;
        }
        return "AI error (" + he.code + "): " + msg;
    }
}
