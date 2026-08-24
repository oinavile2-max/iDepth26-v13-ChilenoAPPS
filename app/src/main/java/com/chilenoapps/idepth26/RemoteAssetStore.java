package com.chilenoapps.idepth26;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class RemoteAssetStore {
    static Bitmap loadThumbnail(Context context, RemoteWallpaper item) {
        String thumb = item.thumbnailUrl == null || item.thumbnailUrl.isEmpty()
                ? item.backgroundUrl : item.thumbnailUrl;
        if (thumb == null || thumb.isEmpty()) return null;
        File file = thumbFile(context, item.id);
        try {
            if (!file.exists() || file.length() == 0) download(thumb, file);
            Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (decoded == null && file.exists()) {
                file.delete();
                download(thumb, file);
                decoded = BitmapFactory.decodeFile(file.getAbsolutePath());
            }
            return decoded;
        } catch (Exception ignored) {
            return null;
        }
    }

    static void selectAndDownload(Context context, RemoteWallpaper item) throws Exception {
        File dir = new File(context.getFilesDir(), "remote_wallpapers/" + safe(item.id));
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Não foi possível criar cache.");

        File bg = new File(dir, "background.jpg");
        download(item.backgroundUrl, bg);

        File fg = null;
        if (item.foregroundUrl != null && !item.foregroundUrl.isEmpty()) {
            fg = new File(dir, "foreground.png");
            download(item.foregroundUrl, fg);
        }

        File home = null;
        if (item.homescreenUrl != null && !item.homescreenUrl.isEmpty()) {
            home = new File(dir, "homescreen.jpg");
            try { download(item.homescreenUrl, home); } catch (Exception ignored) { home = null; }
        }

        Prefs.get(context).edit()
                .putString(Prefs.SOURCE, Prefs.SOURCE_REMOTE)
                .putString(Prefs.REMOTE_ID, item.id)
                .putString(Prefs.REMOTE_NAME, item.name)
                .putString(Prefs.REMOTE_CATEGORY, item.category)
                .putString(Prefs.REMOTE_BACKGROUND_PATH, bg.getAbsolutePath())
                .putString(Prefs.REMOTE_FOREGROUND_PATH, fg == null ? "" : fg.getAbsolutePath())
                .putString(Prefs.REMOTE_HOME_PATH, home == null ? "" : home.getAbsolutePath())
                .putBoolean(Prefs.REMOTE_CLOCK_DEPTH_READY, item.clockDepthReady)
                .apply();
    }

    private static File thumbFile(Context context, String id) {
        File dir = new File(context.getCacheDir(), "remote_thumbs_v15");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, safe(id) + ".img");
    }

    private static String safe(String value) {
        return value == null ? "item" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String normalizeAddress(String address) {
        if (address == null) return "";
        String out = address.trim();
        out = out.replace("/dropzone//", "/dropzone/");
        // Evita HTTP 400 em nomes de arquivo com espaços sem depender do servidor.
        out = out.replace(" ", "%20");
        return out;
    }

    private static void download(String address, File target) throws Exception {
        address = normalizeAddress(address);
        if (address.isEmpty()) throw new IllegalArgumentException("URL vazia");
        if (target.exists() && target.length() > 1024) return;

        File tmp = new File(target.getAbsolutePath() + ".tmp");
        if (tmp.exists()) tmp.delete();

        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setUseCaches(false);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(22000);
        conn.setRequestProperty("User-Agent", "iDepth26/1.5");
        conn.setRequestProperty("Cache-Control", "no-cache");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            String finalAddress = address;
            conn.disconnect();
            throw new IllegalStateException("Falha no download: HTTP " + code + " • " + shortUrl(finalAddress));
        }
        try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buffer = new byte[32 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        } finally {
            conn.disconnect();
        }

        if (tmp.length() < 256) {
            tmp.delete();
            throw new IllegalStateException("Arquivo remoto vazio ou inválido.");
        }
        if (target.exists()) target.delete();
        if (!tmp.renameTo(target)) throw new IllegalStateException("Falha ao salvar arquivo.");
    }

    private static String shortUrl(String value) {
        if (value == null) return "";
        return value.length() <= 60 ? value : value.substring(value.length() - 60);
    }

    private RemoteAssetStore() {}
}
