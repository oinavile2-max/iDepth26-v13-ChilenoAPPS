package com.chilenoapps.idepth26;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class RemoteCatalog {
    static final String FUNCTION_URL =
            "https://eyxwgmcxullybhsqboqh.supabase.co/functions/v1/wallpaper-catalog";
    static final String STORAGE_FALLBACK_URL =
            "https://eyxwgmcxullybhsqboqh.supabase.co/storage/v1/object/public/idepth26/catalog.json";
    private static final String CACHE_FILE = "remote_catalog.json";

    static List<RemoteWallpaper> loadCached(Context context) {
        File file = new File(context.getFilesDir(), CACHE_FILE);
        if (!file.exists()) return new ArrayList<>();
        try (InputStream in = new FileInputStream(file)) {
            return parse(readAll(in));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    static List<RemoteWallpaper> fetchOnline(Context context) throws Exception {
        Exception firstError = null;
        String functionUrl = FUNCTION_URL + "?t=" + System.currentTimeMillis();
        for (String url : new String[]{functionUrl, STORAGE_FALLBACK_URL}) {
            try {
                String json = fetch(url);
                List<RemoteWallpaper> list = parse(json);
                if (!list.isEmpty()) {
                    try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), CACHE_FILE))) {
                        out.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                    return list;
                }
            } catch (Exception e) {
                if (firstError == null) firstError = e;
            }
        }
        if (firstError != null) throw firstError;
        return new ArrayList<>();
    }

    private static String fetch(String address) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setUseCaches(false);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Accept-Charset", "utf-8");
        conn.setRequestProperty("Cache-Control", "no-cache, no-store");
        conn.setRequestProperty("Pragma", "no-cache");
        conn.setRequestProperty("User-Agent", "iDepth26/1.6.1");
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = in == null ? "" : readAll(in);
        conn.disconnect();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code + " em " + address);
        return body;
    }

    private static String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    static List<RemoteWallpaper> parse(String json) throws Exception {
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.startsWith("\uFEFF")) trimmed = trimmed.substring(1).trim();
        JSONArray array;
        if (trimmed.startsWith("[")) {
            array = new JSONArray(trimmed);
        } else {
            JSONObject root = new JSONObject(trimmed);
            array = root.optJSONArray("wallpapers");
            if (array == null) array = root.optJSONArray("data");
            if (array == null) array = root.optJSONArray("items");
            if (array == null) array = root.optJSONArray("results");
            if (array == null) {
                JSONObject resultObject = root.optJSONObject("result");
                if (resultObject != null) {
                    array = resultObject.optJSONArray("wallpapers");
                    if (array == null) array = resultObject.optJSONArray("data");
                }
            }
            if (array == null) array = new JSONArray();
        }

        List<RemoteWallpaper> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null) continue;
            RemoteWallpaper item = RemoteWallpaper.fromJson(obj);
            if (!item.name.isEmpty() && !item.backgroundUrl.isEmpty()) result.add(item);
        }
        return result;
    }

    private RemoteCatalog() {}
}
