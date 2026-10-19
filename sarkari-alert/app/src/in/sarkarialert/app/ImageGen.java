package in.sarkarialert.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Multi-provider text-to-image engine.
 *
 * Order is deliberate:
 *   1. A user-supplied Pollinations pk_/sk_ key, if one exists.
 *   2. Pollinations legacy anonymous endpoint (correct path encoding,
 *      no account-only nologo parameter, one documented 15s rate-limit retry).
 *   3. The user's Gemini key with dynamic image-model discovery. This works
 *      when that Google project has image billing enabled.
 *   4. AI Horde's genuinely free anonymous queue as the final fallback.
 *
 * Every provider error is retained so the UI can show the actual HTTP/API
 * reason instead of a misleading generic prompt-length suggestion.
 */
final class ImageGen {

    interface Progress {
        void update(String message);
    }

    private static final String HORDE_API_KEY = "0000000000";
    private static final String HORDE_CLIENT_AGENT =
            "SarkariAlert:1.7.2:github.com/abhishek4444444";
    private static final String[] GEMINI_PREFERRED = {
            "gemini-nano-banana-2.1",
            "gemini-3.1-flash-lite-image",
            "gemini-3.1-flash-image",
            "gemini-3-pro-image",
            "gemini-2.5-flash-image"
    };

    private static volatile boolean cancelled;
    private static volatile String hordeRequestId;

    private ImageGen() { }

    /** Call on the UI thread before starting the worker to avoid a stop race. */
    static void begin() {
        cancelled = false;
        hordeRequestId = null;
    }

    static void cancel() {
        cancelled = true;
        final String id = hordeRequestId;
        if (id != null && id.length() > 0) {
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        WebApi.delete("https://aihorde.net/api/v2/generate/status/" + id,
                                hordeHeaders());
                    } catch (Exception ignored) { }
                }
            }).start();
        }
    }

    static boolean isCancelled() {
        return cancelled;
    }

    static Bitmap generate(String storedKeys, String prompt,
                           Progress progress, StringBuilder errors) {
        String cleanPrompt = prompt == null ? "" : prompt.trim();
        if (cleanPrompt.length() == 0) {
            addError(errors, "Image generator", "The image description was empty.");
            return null;
        }

        Bitmap bitmap = pollinationsKeyed(storedKeys, cleanPrompt, progress, errors);
        if (bitmap != null || cancelled) return bitmap;

        bitmap = pollinationsAnonymous(cleanPrompt, progress, errors);
        if (bitmap != null || cancelled) return bitmap;

        bitmap = gemini(storedKeys, cleanPrompt, progress, errors);
        if (bitmap != null || cancelled) return bitmap;

        return aiHorde(cleanPrompt, progress, errors);
    }

    // ============================== Pollinations ==============================

    private static Bitmap pollinationsKeyed(String storedKeys, String prompt,
                                            Progress progress, StringBuilder errors) {
        List<String> keys = pollinationsKeys(storedKeys);
        if (keys.isEmpty()) return null;

        notifyProgress(progress, "🎨 Generating your image with your Pollinations key…");
        for (int i = 0; i < keys.size() && !cancelled; i++) {
            String key = keys.get(i);
            try {
                int seed = seed();
                String url = "https://gen.pollinations.ai/image/" + encodePath(prompt)
                        + "?width=768&height=768&seed=" + seed;
                return fetchBitmap(url, key, 75000);
            } catch (Exception e) {
                addError(errors, "Pollinations key", cleanError(e));
                if (e instanceof WebApi.HttpError) {
                    int code = ((WebApi.HttpError) e).code;
                    if (code == 401 || code == 402 || code == 403) {
                        // Keep trying another supplied key, if there is one.
                        continue;
                    }
                }
            }
        }
        return null;
    }

    private static Bitmap pollinationsAnonymous(String prompt,
                                                Progress progress, StringBuilder errors) {
        notifyProgress(progress, "🎨 Generating your image… trying the fast free generator");
        String encoded = encodePath(prompt);
        int seed = seed();

        String bare = "https://image.pollinations.ai/prompt/" + encoded
                + "?width=768&height=768&seed=" + seed;
        try {
            return fetchBitmap(bare, null, 60000);
        } catch (Exception e) {
            addError(errors, "Pollinations free", cleanError(e));
            int code = e instanceof WebApi.HttpError ? ((WebApi.HttpError) e).code : 0;

            // The documented anonymous tier allows roughly one request per
            // 15 seconds per IP. One patient retry handles a just-used slot.
            if (code == 429 && !cancelled) {
                notifyProgress(progress,
                        "🎨 The free generator is rate-limited. Waiting 16 seconds and retrying…");
                if (!sleepCancellable(16000)) return null;
                try {
                    return fetchBitmap(bare, null, 60000);
                } catch (Exception retryError) {
                    addError(errors, "Pollinations free retry", cleanError(retryError));
                    return null;
                }
            }

            // Auth/payment/rate-limit responses will be the same for Turbo.
            if (code == 401 || code == 402 || code == 403 || code == 429) return null;
        }

        if (cancelled) return null;
        notifyProgress(progress, "🎨 Fast generator did not return an image. Trying its Turbo model…");
        String turbo = "https://image.pollinations.ai/prompt/" + encoded
                + "?width=768&height=768&model=turbo&seed=" + (seed + 1);
        try {
            return fetchBitmap(turbo, null, 60000);
        } catch (Exception e) {
            addError(errors, "Pollinations Turbo", cleanError(e));
            return null;
        }
    }

    private static List<String> pollinationsKeys(String storedKeys) {
        List<String> out = new ArrayList<String>();
        if (storedKeys == null) return out;
        String[] parts = storedKeys.split("[,;\\n]+");
        for (int i = 0; i < parts.length; i++) {
            String key = parts[i].trim();
            if (key.length() >= 8 && (key.startsWith("pk_") || key.startsWith("sk_"))) {
                out.add(key);
            }
        }
        return out;
    }

    // ============================== Gemini ====================================

    private static Bitmap gemini(String storedKeys, String prompt,
                                 Progress progress, StringBuilder errors) {
        List<String[]> pool = Llm.pool(storedKeys);
        boolean sawGeminiKey = false;

        for (int p = 0; p < pool.size() && !cancelled; p++) {
            String[] providerAndKey = pool.get(p);
            if (!"gemini".equals(providerAndKey[0])) continue;
            sawGeminiKey = true;
            String key = providerAndKey[1];

            notifyProgress(progress, "🎨 Trying your Gemini image model…");
            List<String> models = discoverGeminiImageModels(key, errors);
            int max = Math.min(models.size(), 4);
            for (int m = 0; m < max && !cancelled; m++) {
                String model = models.get(m);
                try {
                    return geminiModel(key, model, prompt);
                } catch (Exception e) {
                    addError(errors, "Gemini " + model, cleanError(e));
                    if (e instanceof WebApi.HttpError) {
                        int code = ((WebApi.HttpError) e).code;
                        // Quota/billing/auth errors normally apply to every
                        // image model on this key; move to the next key/provider.
                        if (code == 401 || code == 403 || code == 429) break;
                    }
                }
            }
        }

        if (!sawGeminiKey) {
            addError(errors, "Gemini", "No Gemini key is configured for the optional fast image model.");
        }
        return null;
    }

    private static List<String> discoverGeminiImageModels(String key, StringBuilder errors) {
        List<String> available = new ArrayList<String>();
        boolean authFailure = false;
        try {
            JSONObject root = new JSONObject(WebApi.get(
                    "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200", key));
            JSONArray models = root.optJSONArray("models");
            if (models == null) throw new Exception("Google returned no model list.");

            for (int i = 0; i < models.length(); i++) {
                JSONObject model = models.optJSONObject(i);
                if (model == null) continue;
                String rawName = model.optString("name", "");
                String name = rawName.startsWith("models/")
                        ? rawName.substring("models/".length()) : rawName;
                String low = name.toLowerCase(Locale.US);
                if (!low.contains("image") && !low.contains("banana")) continue;
                if (low.contains("embedding") || low.contains("tts")
                        || low.contains("audio") || low.contains("live")) continue;

                JSONArray methods = model.optJSONArray("supportedGenerationMethods");
                boolean canGenerate = methods == null;
                if (methods != null) {
                    for (int j = 0; j < methods.length(); j++) {
                        if ("generateContent".equals(methods.optString(j))) {
                            canGenerate = true;
                            break;
                        }
                    }
                }
                if (canGenerate && !available.contains(name)) available.add(name);
            }
        } catch (Exception e) {
            addError(errors, "Gemini model check", cleanError(e));
            if (e instanceof WebApi.HttpError) {
                int code = ((WebApi.HttpError) e).code;
                authFailure = code == 400 || code == 401 || code == 403;
            }
        }

        List<String> ordered = orderGeminiModels(available);
        if (!ordered.isEmpty() || authFailure) return ordered;

        // Model discovery is best-effort; these are only a fallback if the
        // list call failed for a transient/network reason.
        for (int i = 0; i < GEMINI_PREFERRED.length; i++) ordered.add(GEMINI_PREFERRED[i]);
        return ordered;
    }

    static List<String> orderGeminiModels(List<String> available) {
        List<String> ordered = new ArrayList<String>();
        if (available == null) return ordered;
        for (int i = 0; i < GEMINI_PREFERRED.length; i++) {
            if (available.contains(GEMINI_PREFERRED[i])) ordered.add(GEMINI_PREFERRED[i]);
        }
        for (int i = 0; i < available.size(); i++) {
            String name = available.get(i);
            if (name != null && !ordered.contains(name)) ordered.add(name);
        }
        return ordered;
    }

    private static Bitmap geminiModel(String key, String model, String prompt)
            throws Exception {
        JSONObject body = new JSONObject();
        JSONArray parts = new JSONArray()
                .put(new JSONObject().put("text", "Create an image of " + prompt));
        JSONArray contents = new JSONArray()
                .put(new JSONObject().put("parts", parts));
        body.put("contents", contents);
        JSONArray modalities = new JSONArray().put("TEXT").put("IMAGE");
        body.put("generationConfig", new JSONObject().put("responseModalities", modalities));

        String safeModel = model.replace("/", "%2F");
        String response;
        try {
            response = WebApi.post(
                    "https://generativelanguage.googleapis.com/v1beta/models/"
                            + safeModel + ":generateContent",
                    body.toString(), key);
        } catch (WebApi.HttpError e) {
            if (e.code != 404) throw e;
            // Some current image models are only exposed on the stable v1 API.
            response = WebApi.post(
                    "https://generativelanguage.googleapis.com/v1/models/"
                            + safeModel + ":generateContent",
                    body.toString(), key);
        }
        JSONObject root = new JSONObject(response);

        JSONObject error = root.optJSONObject("error");
        if (error != null) {
            String message = error.optString("message", "Google returned an image API error.");
            throw new Exception(message);
        }

        JSONArray candidates = root.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            JSONObject feedback = root.optJSONObject("promptFeedback");
            String reason = feedback == null ? "" : feedback.optString("blockReason", "");
            throw new Exception(reason.length() > 0
                    ? "Google blocked the request: " + reason
                    : "Google returned no image candidate.");
        }

        StringBuilder text = new StringBuilder();
        for (int c = 0; c < candidates.length(); c++) {
            JSONObject candidate = candidates.optJSONObject(c);
            if (candidate == null) continue;
            JSONObject content = candidate.optJSONObject("content");
            if (content == null) continue;
            JSONArray candidateParts = content.optJSONArray("parts");
            if (candidateParts == null) continue;

            for (int i = 0; i < candidateParts.length(); i++) {
                JSONObject part = candidateParts.optJSONObject(i);
                if (part == null) continue;
                JSONObject inline = part.optJSONObject("inlineData");
                if (inline == null) inline = part.optJSONObject("inline_data");
                if (inline != null) {
                    String data = inline.optString("data", "");
                    if (data.length() > 0) {
                        byte[] bytes = Base64.decode(data, Base64.DEFAULT);
                        Bitmap bitmap = decodeImage(bytes);
                        if (bitmap != null) return bitmap;
                        throw new Exception("Google returned undecodable image data.");
                    }
                }
                String partText = part.optString("text", "");
                if (partText.length() > 0) {
                    if (text.length() > 0) text.append(' ');
                    text.append(partText);
                }
            }
        }

        if (text.length() > 0) {
            throw new Exception("Model returned text instead of an image: "
                    + abbreviate(text.toString(), 180));
        }
        throw new Exception("Google response contained no image.");
    }

    // ============================== AI Horde ==================================

    private static class HordeChoice {
        final String model;
        final boolean flux;
        HordeChoice(String model, boolean flux) {
            this.model = model;
            this.flux = flux;
        }
    }

    private static Bitmap aiHorde(String prompt, Progress progress, StringBuilder errors) {
        String requestId = null;
        try {
            notifyProgress(progress, "🎨 Joining the free AI Horde queue… this can take a minute");
            HordeChoice choice = chooseHordeModel();

            JSONObject params = new JSONObject();
            params.put("n", 1);
            params.put("width", choice.flux ? 768 : 512);
            params.put("height", choice.flux ? 768 : 512);
            params.put("steps", choice.flux ? 6 : 20);
            params.put("cfg_scale", choice.flux ? 1 : 7.5);
            params.put("sampler_name", choice.flux ? "k_euler" : "k_dpmpp_2m");
            params.put("karras", !choice.flux);
            params.put("seed", String.valueOf(seed()));

            String hordePrompt = prompt;
            if (!choice.flux && !hordePrompt.contains("###")) {
                hordePrompt += ", detailed, high quality ### blurry, distorted, deformed, "
                        + "extra limbs, low quality, watermark, text";
            }

            JSONObject body = new JSONObject();
            body.put("prompt", hordePrompt);
            body.put("params", params);
            body.put("nsfw", false);
            body.put("censor_nsfw", true);
            body.put("trusted_workers", false);
            body.put("slow_workers", true);
            body.put("replacement_filter", true);
            body.put("r2", true);
            body.put("shared", false);
            body.put("allow_downgrade", true);
            body.put("dry_run", false);
            if (choice.model != null && choice.model.length() > 0) {
                body.put("models", new JSONArray().put(choice.model));
            }

            String submit = WebApi.post("https://aihorde.net/api/v2/generate/async",
                    body.toString(), hordeHeaders());
            JSONObject submitRoot = new JSONObject(submit);
            requestId = submitRoot.optString("id", "");
            if (requestId.length() == 0) {
                String message = submitRoot.optString("message", "");
                throw new Exception(message.length() > 0 ? message
                        : "AI Horde did not return a job ID.");
            }
            hordeRequestId = requestId;

            long deadline = System.currentTimeMillis() + 180000L;
            int transientFailures = 0;
            boolean done = false;
            while (!cancelled && System.currentTimeMillis() < deadline) {
                if (!sleepCancellable(4000)) return null;
                try {
                    String check = WebApi.get(
                            "https://aihorde.net/api/v2/generate/check/" + requestId,
                            hordeHeaders());
                    JSONObject checkRoot = new JSONObject(check);
                    transientFailures = 0;
                    if (checkRoot.optBoolean("faulted", false)) {
                        throw new Exception("The AI Horde job faulted before an image was produced.");
                    }
                    int finished = checkRoot.optInt("finished", 0);
                    int processing = checkRoot.optInt("processing", 0);
                    int queuePosition = checkRoot.optInt("queue_position", 0);
                    int waitTime = checkRoot.optInt("wait_time", 0);
                    done = checkRoot.optBoolean("done", false) || finished >= 1;
                    if (done) break;

                    if (processing > 0) {
                        notifyProgress(progress, "🎨 AI Horde worker is drawing your image…");
                    } else if (queuePosition > 0) {
                        String eta = waitTime > 0 ? " • about " + waitTime + "s" : "";
                        notifyProgress(progress, "🎨 AI Horde queue position "
                                + queuePosition + eta + "…");
                    } else {
                        notifyProgress(progress, "🎨 Waiting for a free AI Horde worker…");
                    }
                } catch (WebApi.HttpError e) {
                    if (e.code == 404 || e.code == 410) throw e;
                    transientFailures++;
                    if (transientFailures >= 4) throw e;
                } catch (Exception e) {
                    // A faulted-job exception above is also an Exception, but
                    // it originates inside the try and should be terminal.
                    String message = e.getMessage();
                    if (message != null && message.contains("job faulted")) throw e;
                    transientFailures++;
                    if (transientFailures >= 4) throw e;
                }
            }

            if (cancelled) return null;
            if (!done) throw new Exception("The free queue took longer than 3 minutes.");

            String status = WebApi.get(
                    "https://aihorde.net/api/v2/generate/status/" + requestId,
                    hordeHeaders());
            JSONObject statusRoot = new JSONObject(status);
            if (statusRoot.optBoolean("faulted", false)) {
                throw new Exception("The AI Horde job faulted before an image was produced.");
            }
            JSONArray generations = statusRoot.optJSONArray("generations");
            if (generations == null || generations.length() == 0) {
                throw new Exception("AI Horde finished without returning an image.");
            }

            for (int i = 0; i < generations.length() && !cancelled; i++) {
                JSONObject generation = generations.optJSONObject(i);
                if (generation == null) continue;
                String image = generation.optString("img", "");
                if (image.length() == 0) continue;
                if (image.startsWith("http://") || image.startsWith("https://")) {
                    try {
                        return fetchBitmap(image, null, 60000);
                    } catch (Exception e) {
                        addError(errors, "AI Horde download", cleanError(e));
                    }
                } else {
                    try {
                        Bitmap bitmap = decodeImage(Base64.decode(image, Base64.DEFAULT));
                        if (bitmap != null) return bitmap;
                    } catch (Exception ignored) { }
                }
            }
            throw new Exception("AI Horde returned an unusable image.");
        } catch (Exception e) {
            if (!cancelled) addError(errors, "AI Horde (free)", cleanError(e));
            return null;
        } finally {
            if (requestId != null && requestId.equals(hordeRequestId)) hordeRequestId = null;
        }
    }

    private static HordeChoice chooseHordeModel() {
        try {
            JSONArray models = new JSONArray(WebApi.get(
                    "https://aihorde.net/api/v2/status/models?type=image", hordeHeaders()));
            String[] fluxNames = {
                    "Flux.1-Schnell fp8 (Compact)",
                    "FLUX.1 [schnell]",
                    "Flux.1 [schnell]"
            };
            for (int i = 0; i < fluxNames.length; i++) {
                if (hordeModelOnline(models, fluxNames[i])) {
                    return new HordeChoice(fluxNames[i], true);
                }
            }
            String[] sdNames = {
                    "AbsoluteReality",
                    "Deliberate",
                    "Dreamshaper",
                    "Edge Of Realism"
            };
            for (int i = 0; i < sdNames.length; i++) {
                if (hordeModelOnline(models, sdNames[i])) {
                    return new HordeChoice(sdNames[i], false);
                }
            }
        } catch (Exception ignored) { }
        // No preferred live model discovered: omit the model restriction and
        // let AI Horde route the job to any compatible image worker.
        return new HordeChoice(null, false);
    }

    private static boolean hordeModelOnline(JSONArray models, String wanted) {
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.optJSONObject(i);
            if (model != null && wanted.equals(model.optString("name", ""))
                    && model.optInt("count", 0) > 0) return true;
        }
        return false;
    }

    private static String[][] hordeHeaders() {
        return new String[][]{
                {"apikey", HORDE_API_KEY},
                {"Client-Agent", HORDE_CLIENT_AGENT}
        };
    }

    // ============================== Shared ====================================

    private static Bitmap fetchBitmap(String url, String bearer, int timeoutMs)
            throws Exception {
        byte[] bytes = WebApi.getBytes(url, bearer, timeoutMs);
        Bitmap bitmap = decodeImage(bytes);
        if (bitmap == null) throw new Exception(invalidDataReason(bytes));
        return bitmap;
    }

    private static Bitmap decodeImage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        while (bounds.outWidth / (sample * 2) > 1024
                || bounds.outHeight / (sample * 2) > 1024) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private static String invalidDataReason(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "Server returned no image data.";
        try {
            int n = Math.min(bytes.length, 256);
            String start = new String(bytes, 0, n, "UTF-8").toLowerCase(Locale.US);
            if (start.contains("<html") || start.contains("<!doctype")
                    || start.trim().startsWith("<")) {
                return "Server returned a web page instead of image bytes.";
            }
            if (start.trim().startsWith("{") || start.trim().startsWith("[")) {
                return "Server returned JSON instead of image bytes: "
                        + abbreviate(start.trim(), 160);
            }
        } catch (Exception ignored) { }
        return "Server returned undecodable image data (" + bytes.length + " bytes).";
    }

    static String encodePath(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8")
                    .replace("+", "%20")
                    .replace("*", "%2A");
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    private static int seed() {
        return (int) (System.currentTimeMillis() % 100000000L);
    }

    private static boolean sleepCancellable(long millis) {
        long end = System.currentTimeMillis() + millis;
        while (!cancelled && System.currentTimeMillis() < end) {
            long remaining = end - System.currentTimeMillis();
            try {
                Thread.sleep(Math.min(200L, Math.max(1L, remaining)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !cancelled;
    }

    private static void notifyProgress(Progress progress, String message) {
        if (progress != null && !cancelled) progress.update(message);
    }

    private static void addError(StringBuilder errors, String provider, String detail) {
        if (errors == null) return;
        if (errors.length() > 0) errors.append('\n');
        errors.append("• ").append(provider).append(": ")
                .append(abbreviate(detail == null ? "Unknown error." : detail, 260));
    }

    private static String cleanError(Exception e) {
        if (e instanceof WebApi.HttpError) {
            WebApi.HttpError http = (WebApi.HttpError) e;
            String raw = http.rawMessage();
            return "HTTP " + http.code + (raw == null || raw.length() == 0 ? "" : " — " + raw);
        }
        String message = e == null ? null : e.getMessage();
        if (message == null || message.length() == 0) {
            return e == null ? "Unknown error." : e.getClass().getSimpleName();
        }
        return message;
    }

    private static String abbreviate(String value, int max) {
        String clean = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (clean.length() <= max) return clean;
        return clean.substring(0, Math.max(0, max - 1)) + "…";
    }
}
