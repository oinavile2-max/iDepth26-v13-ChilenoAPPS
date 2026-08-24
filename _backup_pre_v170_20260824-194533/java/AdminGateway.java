package com.chilenoapps.idepth26;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class AdminGateway {
    static JSONObject login(String loginId, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("login_id", loginId == null ? "" : loginId.replaceAll("\\D", ""));
        body.put("password", password == null ? "" : password);
        return request(AppConfig.ADMIN_AUTH_URL, "POST", body, null);
    }

    static JSONObject dashboard(String token) throws Exception {
        return request(AppConfig.ADMIN_DASHBOARD_URL, "GET", null, token);
    }

    static JSONObject submissionAction(String token, String submissionId, String action) throws Exception {
        JSONObject body = new JSONObject();
        body.put("submission_id", submissionId == null ? "" : submissionId);
        body.put("action", action == null ? "" : action);
        return request(AppConfig.ADMIN_DASHBOARD_URL, "POST", body, token);
    }

    static JSONObject setVip(String token, String installId, boolean enabled, int durationDays) throws Exception {
        JSONObject body = new JSONObject();
        body.put("action", "vip_set");
        body.put("install_id", installId == null ? "" : installId);
        body.put("enabled", enabled);
        body.put("duration_days", Math.max(0, durationDays));
        return request(AppConfig.ADMIN_DASHBOARD_URL, "POST", body, token);
    }

    private static JSONObject request(String endpoint, String method, JSONObject body, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setConnectTimeout(9000);
        conn.setReadTimeout(18000);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(in);
        conn.disconnect();
        JSONObject result;
        try { result = new JSONObject(text); }
        catch (Exception e) { result = new JSONObject().put("ok", false).put("error", text); }
        result.put("_http", code);
        return result;
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private AdminGateway() {}
}
