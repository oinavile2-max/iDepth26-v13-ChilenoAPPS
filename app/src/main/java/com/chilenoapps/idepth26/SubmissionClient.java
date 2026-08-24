package com.chilenoapps.idepth26;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SubmissionClient {
    interface Callback {
        void onResult(boolean ok, String message);
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    static void submit(Context context, Uri uri, Callback callback) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                byte[] image = readLimited(app, uri, 12 * 1024 * 1024);
                String mime = app.getContentResolver().getType(uri);
                if (mime == null || !mime.startsWith("image/")) mime = "image/jpeg";
                String ext = mime.contains("png") ? "png" : mime.contains("webp") ? "webp" : "jpg";
                String boundary = "----iDepth26" + UUID.randomUUID();
                HttpURLConnection conn = (HttpURLConnection) new URL(AppConfig.USER_SUBMISSION_URL).openConnection();
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(30000);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                try (OutputStream out = conn.getOutputStream()) {
                    field(out, boundary, "app_version", AppConfig.VERSION_NAME);
                    field(out, boundary, "install_id", UsageLogger.installId(app));
                    file(out, boundary, "wallpaper", "user-wallpaper." + ext, mime, image);
                    out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                String response = readAll(in);
                conn.disconnect();

                boolean ok = code >= 200 && code < 300;
                String message = ok ? "Wallpaper enviado para curadoria." : "Falha no envio.";
                try {
                    JSONObject json = new JSONObject(response);
                    if (json.has("message")) message = json.optString("message", message);
                } catch (Exception ignored) {}
                callback.onResult(ok, message);
            } catch (Exception e) {
                callback.onResult(false, "Falha no envio: " + e.getMessage());
            }
        });
    }

    private static byte[] readLimited(Context context, Uri uri, int max) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("Não foi possível abrir a foto.");
            byte[] buffer = new byte[32 * 1024];
            int n, total = 0;
            while ((n = in.read(buffer)) >= 0) {
                total += n;
                if (total > max) throw new IllegalStateException("Imagem acima de 12 MB.");
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static void field(OutputStream out, String boundary, String name, String value) throws Exception {
        String part = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n";
        out.write(part.getBytes(StandardCharsets.UTF_8));
    }

    private static void file(OutputStream out, String boundary, String name, String fileName,
                             String mime, byte[] bytes) throws Exception {
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: " + mime + "\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
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

    private SubmissionClient() {}
}
