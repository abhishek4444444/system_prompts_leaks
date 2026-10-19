package in.sarkarialert.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Universal AI client. ONE key field, ANY provider — the key format is
 * auto-detected:
 *
 *   AIza… / AQ.…  → Google Gemini   (x-goog-api-key header)
 *   gsk_…         → Groq            (OpenAI-compatible, Llama)
 *   sk-… (hex32)  → DeepSeek        (OpenAI-compatible)
 *   sk-or-…       → OpenRouter      (OpenAI-compatible, free models)
 *   sk-…          → OpenAI          (OpenAI-compatible)
 *
 * Answers STREAM token-by-token (SSE) so replies feel instant, with a
 * silent non-streaming fallback. Roles are normalized per API
 * ("model" for Gemini, "assistant" for OpenAI-style) — the old
 * "Role 'ai' is not supported" 400 is fixed at this layer.
 */
final class Llm {

    interface StreamCb {
        /** progressive full text so far, already on the UI thread */
        void onDelta(String soFar);
        /** final result or error, on the UI thread */
        void done(String text, String err);
    }

    private static final Handler UI = new Handler(Looper.getMainLooper());

    private Llm() { }

    // ---- provider detection ----

    static String detect(String key) {
        if (key == null) return "";
        key = key.trim();
        if (key.startsWith("gsk_")) return "groq";
        if (key.startsWith("AIza") || key.startsWith("AQ.")) return "gemini";
        if (key.startsWith("sk-or-")) return "openrouter";
        if (key.startsWith("sk-")) return isHex32(key.substring(3)) ? "deepseek" : "openai";
        return "";
    }

    private static boolean isHex32(String s) {
        if (s == null || s.length() != 32) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!ok) return false;
        }
        return true;
    }

    static String pretty(String provider) {
        if ("groq".equals(provider)) return "Groq (Llama)";
        if ("deepseek".equals(provider)) return "DeepSeek";
        if ("openai".equals(provider)) return "OpenAI";
        if ("openrouter".equals(provider)) return "OpenRouter";
        return "Google Gemini";
    }

    // ---- entry point ----

    static void ask(final String key, final String system,
                    final List<String[]> history, final StreamCb cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                String provider = detect(key);
                if (provider.length() == 0) provider = "gemini"; // last guess
                if ("gemini".equals(provider)) askGemini(key, system, history, cb);
                else askOpenAIStyle(provider, key, system, history, cb);
            }
        }).start();
    }

    // ================= Google Gemini =================

    private static final String[] GEMINI_FALLBACKS = {
            "gemini-3.8-flash", "gemini-flash-latest", "gemini-2.5-flash"
    };
    private static String gModel;
    private static long gModelAt;

    private static synchronized String pickGemini(String key) {
        if (gModel != null && System.currentTimeMillis() - gModelAt < 6 * 3600000L) {
            return gModel;
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
                if (low.contains("lite")) ver -= 0.01;
                if (ver > bestVer) { bestVer = ver; best = name; }
            }
            if (best != null) {
                gModel = best.startsWith("models/") ? best.substring(7) : best;
                gModelAt = System.currentTimeMillis();
            }
        } catch (Exception ignored) { }
        return gModel;
    }

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

    private static void askGemini(final String key, final String system,
                                  final List<String[]> history, final StreamCb cb) {
        List<String> models = new ArrayList<String>();
        String discovered = pickGemini(key);
        if (discovered != null) models.add(discovered);
        for (String f : GEMINI_FALLBACKS) if (!models.contains(f)) models.add(f);

        for (int attempt = 0; attempt < models.size(); attempt++) {
            final String model = models.get(attempt);
            try {
                JSONObject body = new JSONObject();
                if (system != null && system.length() > 0) {
                    body.put("systemInstruction", new JSONObject().put("parts",
                            new JSONArray().put(new JSONObject().put("text", system))));
                }
                JSONArray contents = new JSONArray();
                for (String[] msg : history) {
                    contents.put(new JSONObject()
                            .put("role", "ai".equals(msg[0]) ? "model" : "user") // Gemini roles
                            .put("parts", new JSONArray().put(new JSONObject().put("text", msg[1]))));
                }
                body.put("contents", contents);
                body.put("generationConfig", new JSONObject()
                        .put("maxOutputTokens", 8192).put("temperature", 0.8));
                HttpURLConnection conn = openPost(
                        "https://generativelanguage.googleapis.com/v1beta/models/"
                                + model + ":streamGenerateContent?alt=sse",
                        body.toString(), "x-goog-api-key", key);
                int code = conn.getResponseCode();
                if (code >= 400) {
                    WebApi.HttpError he = new WebApi.HttpError(code, drain(conn.getErrorStream()));
                    conn.disconnect();
                    String raw = he.rawMessage();
                    if (code == 404) {
                        if (model.equals(discovered)) { synchronized (Llm.class) { gModel = null; } }
                        continue; // retired model — next
                    }
                    if (code == 400 && raw != null && raw.toLowerCase().contains("api key")) {
                        deliverErr(cb, "API key looks invalid for Google Gemini. "
                                + "Open Settings → AI key and paste a fresh key.\n\n" + safe(raw));
                        return;
                    }
                    if (code == 400) continue; // maybe this model rejects params — try next
                    deliverErr(cb, friendly(code, "Google Gemini", raw));
                    return;
                }
                String type = conn.getContentType() == null ? "" : conn.getContentType();
                StringBuilder sb = new StringBuilder();
                if (type.contains("application/json")) {
                    // server ignored the stream — whole JSON in one body
                    JSONObject root = new JSONObject(drain(conn.getInputStream()));
                    JSONArray cands = root.getJSONArray("candidates");
                    JSONArray parts = cands.getJSONObject(0).getJSONObject("content").getJSONArray("parts");
                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject p = parts.getJSONObject(i);
                        if (p.has("text")) sb.append(p.getString("text"));
                    }
                    conn.disconnect();
                    deliverDone(cb, sb.toString(), cb, false);
                } else {
                    boolean ok = streamSse(conn, new Part() {
                        @Override public String text(JSONObject root) {
                            try {
                                JSONArray cands = root.getJSONArray("candidates");
                                JSONArray parts = cands.getJSONObject(0)
                                        .getJSONObject("content").getJSONArray("parts");
                                StringBuilder t = new StringBuilder();
                                for (int i = 0; i < parts.length(); i++) {
                                    JSONObject p = parts.getJSONObject(i);
                                    if (p.has("text")) t.append(p.getString("text"));
                                }
                                return t.toString();
                            } catch (Exception e) {
                                return "";
                            }
                        }
                    }, sb, cb);
                    conn.disconnect();
                    if (ok) {
                        synchronized (Llm.class) { gModel = model; gModelAt = System.currentTimeMillis(); }
                        deliverDone(cb, sb.toString(), cb, true);
                    } else if (sb.length() == 0) {
                        deliverErr(cb, "Connection lost before any reply. Try again.");
                    } else {
                        deliverDone(cb, sb.toString(), cb, false); // partial but usable
                    }
                }
                return;
            } catch (Exception e) {
                if (attempt == models.size() - 1) {
                    deliverErr(cb, "Couldn't reach Google Gemini: " + safe(String.valueOf(e.getMessage())));
                    return;
                }
            }
        }
        deliverErr(cb, "No working Gemini model found for this key right now.");
    }

    // ============ OpenAI-compatible: Groq / DeepSeek / OpenAI / OpenRouter ============

    private static String baseOf(String p) {
        if ("groq".equals(p)) return "https://api.groq.com/openai/v1";
        if ("deepseek".equals(p)) return "https://api.deepseek.com/v1";
        if ("openrouter".equals(p)) return "https://openrouter.ai/api/v1";
        return "https://api.openai.com/v1";
    }

    private static String[] modelsOf(String p) {
        if ("groq".equals(p)) {
            return new String[]{"llama-3.3-70b-versatile", "gpt-oss-20b", "llama-3.1-8b-instant"};
        }
        if ("deepseek".equals(p)) return new String[]{"deepseek-chat"};
        if ("openrouter".equals(p)) {
            return new String[]{"meta-llama/llama-3.3-70b-instruct:free",
                    "deepseek/deepseek-chat-v3-0324:free",
                    "google/gemma-2-9b-it:free"};
        }
        return new String[]{"gpt-4o-mini", "gpt-4.1-mini"};
    }

    private static void askOpenAIStyle(final String provider, final String key, final String system,
                                       final List<String[]> history, final StreamCb cb) {
        String[] models = modelsOf(provider);
        for (int attempt = 0; attempt < models.length; attempt++) {
            String model = models[attempt];
            try {
                JSONObject body = new JSONObject();
                body.put("model", model);
                JSONArray messages = new JSONArray();
                if (system != null && system.length() > 0) {
                    messages.put(new JSONObject().put("role", "system").put("content", system));
                }
                for (String[] msg : history) {
                    messages.put(new JSONObject()
                            .put("role", "ai".equals(msg[0]) ? "assistant" : "user") // OpenAI roles
                            .put("content", msg[1]));
                }
                body.put("messages", messages);
                body.put("max_tokens", 4096);
                body.put("temperature", 0.8);
                body.put("stream", true);
                HttpURLConnection conn = openPost(baseOf(provider) + "/chat/completions",
                        body.toString(), "Authorization", "Bearer " + key);
                int code = conn.getResponseCode();
                if (code >= 400) {
                    WebApi.HttpError he = new WebApi.HttpError(code, drain(conn.getErrorStream()));
                    conn.disconnect();
                    String raw = he.rawMessage();
                    if ((code == 404 || code == 400) && attempt < models.length - 1) continue;
                    deliverErr(cb, friendly(code, pretty(provider), raw));
                    return;
                }
                String type = conn.getContentType() == null ? "" : conn.getContentType();
                StringBuilder sb = new StringBuilder();
                if (type.contains("application/json")) {
                    JSONObject root = new JSONObject(drain(conn.getInputStream()));
                    sb.append(root.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").optString("content", ""));
                    conn.disconnect();
                    deliverDone(cb, sb.toString(), cb, false);
                } else {
                    boolean ok = streamSse(conn, new Part() {
                        @Override public String text(JSONObject root) {
                            try {
                                return root.getJSONArray("choices").getJSONObject(0)
                                        .getJSONObject("delta").optString("content", "");
                            } catch (Exception e) {
                                return "";
                            }
                        }
                    }, sb, cb);
                    conn.disconnect();
                    if (ok || sb.length() > 0) deliverDone(cb, sb.toString(), cb, ok);
                    else deliverErr(cb, "Connection lost before any reply. Try again.");
                }
                return;
            } catch (Exception e) {
                if (attempt == models.length - 1) {
                    deliverErr(cb, "Couldn't reach " + pretty(provider) + ": "
                            + safe(String.valueOf(e.getMessage())));
                    return;
                }
            }
        }
    }

    // ================= shared plumbing =================

    private interface Part { String text(JSONObject sseJson); }

    private static HttpURLConnection openPost(String surl, String json,
                                              String authName, String authValue) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(surl).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(120000);
        conn.setRequestMethod("POST");
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "text/event-stream, application/json");
        conn.setRequestProperty(authName, authValue);
        conn.setDoOutput(true);
        OutputStream os = conn.getOutputStream();
        os.write(json.getBytes("UTF-8"));
        os.flush();
        os.close();
        return conn;
    }

    /** reads an SSE body, calls back with extracted text deltas; true = clean end */
    private static boolean streamSse(HttpURLConnection conn, Part part,
                                     StringBuilder out, final StreamCb cb) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"), 8192);
            long lastPost = 0;
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.length() == 0 || data.equals("[DONE]")) continue;
                String t;
                try {
                    t = part.text(new JSONObject(data));
                } catch (Exception e) {
                    continue; // keep-alive or comment chunk
                }
                if (t == null || t.length() == 0) continue;
                out.append(t);
                final String soFar = out.toString();
                long now = System.currentTimeMillis();
                if (now - lastPost >= 120) { // throttle UI updates
                    lastPost = now;
                    UI.post(new Runnable() {
                        @Override public void run() { cb.onDelta(soFar); }
                    });
                }
            }
            return true;
        } catch (Exception e) {
            return false; // mid-stream failure — caller decides with what we have
        } finally {
            try { if (r != null) r.close(); } catch (Exception ignored) { }
        }
    }

    private static String drain(java.io.InputStream in) throws Exception {
        if (in == null) return "";
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } finally {
            try { in.close(); } catch (Exception ignored) { }
        }
    }

    private static void deliverDone(final StreamCb cb, final String text, final StreamCb unused,
                                    final boolean cleanEnd) {
        UI.post(new Runnable() {
            @Override public void run() {
                String t = text == null ? "" : text.trim();
                if (t.length() == 0) cb.done(null, "Empty reply — try again.");
                else cb.done(t, null);
            }
        });
    }

    private static void deliverErr(final StreamCb cb, final String err) {
        UI.post(new Runnable() {
            @Override public void run() { cb.done(null, err); }
        });
    }

    private static String friendly(int code, String provider, String raw) {
        String msg = raw == null ? "" : raw;
        if (msg.length() > 240) msg = msg.substring(0, 240) + "…";
        if (code == 401) return "API key rejected by " + provider + " (401). "
                + "Check the key in Settings → AI key.\n\n" + msg;
        if (code == 402) return "DeepSeek balance is empty (402). Top up or use another provider.\n\n" + msg;
        if (code == 403) return "Key rejected by " + provider + " (403).\n\n" + msg;
        if (code == 429) return "Rate limit reached (429). Wait a moment and try again.\n\n" + msg;
        return provider + " error " + code + ". " + msg;
    }

    private static String safe(String s) {
        if (s == null) return "";
        s = s.replace("\n", " ").trim();
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
