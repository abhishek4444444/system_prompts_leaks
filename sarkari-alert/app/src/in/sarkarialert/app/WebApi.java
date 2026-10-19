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

    static String get(String surl, String[][] headers) throws Exception {
        return call("GET", surl, null, null, headers);
    }

    static String post(String surl, String json, String[][] headers) throws Exception {
        return call("POST", surl, json, null, headers);
    }

    static String delete(String surl, String[][] headers) throws Exception {
        return call("DELETE", surl, null, null, headers);
    }

    /** binary fetch (used by the image generator) */
    static byte[] getBytes(String surl) throws Exception {
        return getBytes(surl, null, 90000);
    }

    static byte[] getBytes(String surl, String bearer, int readTimeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(surl).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(Math.max(1000, readTimeoutMs));
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36");
            conn.setRequestProperty("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*;q=0.8");
            if (bearer != null && bearer.length() > 0) {
                conn.setRequestProperty("Authorization", "Bearer " + bearer);
            }
            int code = conn.getResponseCode();
            if (code >= 400) {
                throw new HttpError(code, drain(conn.getErrorStream(), conn));
            }
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            java.io.InputStream in = conn.getInputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0 && total < 8 * 1024 * 1024) {
                bo.write(buf, 0, n);
                total += n;
            }
            in.close();
            return bo.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    private static String call(String method, String surl, String json, String apiKey) throws Exception {
        return call(method, surl, json, apiKey, null);
    }

    private static String call(String method, String surl, String json,
                               String apiKey, String[][] headers) throws Exception {
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
            if (headers != null) {
                for (int i = 0; i < headers.length; i++) {
                    if (headers[i] != null && headers[i].length >= 2
                            && headers[i][0] != null && headers[i][1] != null) {
                        conn.setRequestProperty(headers[i][0], headers[i][1]);
                    }
                }
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
        /** pull a human-readable message out of common JSON error bodies */
        String rawMessage() {
            try {
                JSONObject root = new JSONObject(body);
                JSONObject error = root.optJSONObject("error");
                String m = error == null ? "" : error.optString("message", "");
                if (m.length() == 0) m = root.optString("message", "");
                if (m.length() == 0) m = root.optString("detail", "");
                return m.length() > 0 ? m : null;
            } catch (Exception e) {
                return body.length() > 0 && body.length() < 200 ? body : null;
            }
        }
    }
}
