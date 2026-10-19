package in.sarkarialert.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Google AI Studio (Gemini) chat client. If a model is retired the client
 * transparently falls back to the next one, so the extension keeps working.
 */
final class Gemini {

    interface Cb { void done(String text, String err); }

    private static final String[] MODELS = {
            "gemini-2.0-flash", "gemini-2.5-flash", "gemini-1.5-flash"
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
                                        + MODELS[m] + ":generateContent?key=" + key,
                                body.toString()));
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
                        lastErr = friendly(he);
                        if (he.code == 404 || he.code == 400) continue; // model gone — try next
                        break;
                    } catch (Exception e) {
                        lastErr = e.getMessage();
                        break;
                    }
                }
                final String err = lastErr == null ? "Something went wrong" : lastErr;
                uiDeliver(ui, null, err, cb);
            }
        }).start();
    }

    private static void uiDeliver(Handler ui, final String text, final String err, final Cb cb) {
        ui.post(new Runnable() {
            @Override public void run() { cb.done(text, err); }
        });
    }

    private static String friendly(WebApi.HttpError he) {
        String msg = he.getMessage();
        try {
            JSONObject e = new JSONObject(he.body).getJSONObject("error");
            msg = e.optString("message", msg);
        } catch (Exception ignored) { }
        if (msg == null) msg = "";
        if (msg.length() > 220) msg = msg.substring(0, 220) + "…";
        if (he.code == 400 && msg.toLowerCase().contains("api key")) {
            return "API key looks invalid. Open Settings → AI key and paste a fresh key from aistudio.google.com.";
        }
        if (he.code == 403) {
            return "Key rejected (403). Make sure the Gemini API is enabled for this key in Google AI Studio.";
        }
        if (he.code == 429) {
            return "Free quota limit reached for now. Wait a minute and try again.";
        }
        if (he.code == 404 || he.code == 400) {
            return "Model unavailable — try again.";
        }
        return "AI error (" + he.code + "): " + msg;
    }
}
