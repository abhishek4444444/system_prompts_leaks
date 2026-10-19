package in.sarkarialert.app;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;

import org.json.JSONObject;

/** Minimal HTTP helper used by the AI assistant and the translator. */
final class WebApi {

    private WebApi() { }

    static String get(String surl) throws Exception {
        return call("GET", surl, null, null);
    }

    static String get(String surl, String apiKey) throws Exception {
        return call("GET", surl, null, apiKey);
    }

    static String post(String surl, String json) throws Exception {
        return call("POST", surl, json, null);
    }

    static String post(String surl, String json, String apiKey) throws Exception {
        return call("POST", surl, json, apiKey);
    }

    private static String call(String method, String surl, String json, String apiKey) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(surl).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(90000); // AI answers can take a while
            conn.setRequestMethod(method);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36");
            conn.setRequestProperty("Accept-Encoding", "gzip");
            if (apiKey != null && apiKey.length() > 0) {
                conn.setRequestProperty("x-goog-api-key", apiKey);
            }
            if (json != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                OutputStream os = conn.getOutputStream();
                os.write(json.getBytes("UTF-8"));
                os.flush();
                os.close();
            }
            int code = conn.getResponseCode();
            if (code >= 400) {
                String body = drain(conn.getErrorStream(), conn);
                throw new HttpError(code, body);
            }
            return drain(conn.getInputStream(), conn);
        } finally {
            conn.disconnect();
        }
    }

    private static String drain(InputStream raw, HttpURLConnection conn) throws Exception {
        if (raw == null) return "";
        InputStream in = raw;
        try {
            String enc = conn.getContentEncoding();
            if (enc != null && enc.toLowerCase().contains("gzip")) in = new GZIPInputStream(in);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } finally {
            try { in.close(); } catch (Exception ignored) { }
        }
    }

    static class HttpError extends Exception {
        final int code;
        final String body;
        HttpError(int code, String body) {
            super("HTTP " + code);
            this.code = code;
            this.body = body == null ? "" : body;
        }
        /** pull Google's human-readable message out of the JSON error body */
        String rawMessage() {
            try {
                String m = new JSONObject(body).getJSONObject("error").optString("message", "");
                return m.length() > 0 ? m : null;
            } catch (Exception e) {
                return body.length() > 0 && body.length() < 200 ? body : null;
            }
        }
    }
}
