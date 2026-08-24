package com.chilenoapps.idepth26;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class UsageLogger {
    private static final String INSTALL_ID = "telemetry_install_id";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    static String installId(Context context) {
        SharedPreferences prefs = Prefs.get(context.getApplicationContext());
        String installId = prefs.getString(INSTALL_ID, "");
        if (installId == null || installId.isEmpty()) {
            installId = UUID.randomUUID().toString();
            prefs.edit().putString(INSTALL_ID, installId).apply();
        }
        return installId;
    }

    static void event(Context context, String event, String details) {
        Context app = context.getApplicationContext();
        if (!Prefs.get(app).getBoolean(Prefs.USAGE_CONSENT, false)) return;
        IO.execute(() -> {
            try {
                String installId = installId(app);

                JSONObject body = new JSONObject();
                body.put("install_id", installId);
                body.put("event", event == null ? "unknown" : event);
                body.put("details", details == null ? "" : details);
                body.put("app_version", AppConfig.VERSION_NAME);
                body.put("android_version", Build.VERSION.RELEASE);
                // Minimização de dados: não enviamos nome, e-mail, CPF, telefone ou modelo do aparelho.
                body.put("device", "");
                post(AppConfig.USAGE_LOG_URL, body);
            } catch (Exception ignored) {}
        });
    }

    private static void post(String endpoint, JSONObject body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(bytes);
        }
        try { conn.getInputStream().close(); } catch (Exception ignored) {}
        conn.disconnect();
    }

    private UsageLogger() {}
}
