package com.chilenoapps.idepth26;

import org.json.JSONObject;

final class RemoteWallpaper {
    final String id;
    final String name;
    final String category;
    final String thumbnailUrl;
    final String backgroundUrl;
    final String homescreenUrl;
    final String foregroundUrl;
    final boolean isNew;
    final boolean featured;
    final String depthMode;
    final boolean clockDepthReady;
    final boolean vipOnly;

    RemoteWallpaper(String id, String name, String category, String thumbnailUrl,
                    String backgroundUrl, String homescreenUrl, String foregroundUrl,
                    boolean isNew, boolean featured, String depthMode, boolean clockDepthReady,
                    boolean vipOnly) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.thumbnailUrl = thumbnailUrl;
        this.backgroundUrl = backgroundUrl;
        this.homescreenUrl = homescreenUrl;
        this.foregroundUrl = foregroundUrl;
        this.isNew = isNew;
        this.featured = featured;
        this.depthMode = depthMode;
        this.clockDepthReady = clockDepthReady;
        this.vipOnly = vipOnly;
    }

    static RemoteWallpaper fromJson(JSONObject o) {
        String id = first(o, "id", "slug");
        String name = first(o, "name", "nome");
        String category = first(o, "category", "categoria");
        String thumb = first(o, "thumbnail", "thumbnail_url");
        String bg = first(o, "lockscreen", "background_url", "background");
        String home = first(o, "homescreen", "homescreen_url");
        String fg = first(o, "foreground", "foreground_url");
        boolean isNew = bool(o, true, "new", "is_new");
        boolean featured = bool(o, false, "featured");
        String depthMode = first(o, "depth_mode");
        boolean ready = bool(o, false, "clock_depth_ready");
        boolean vipOnly = bool(o, false, "vip_only", "vip");
        if (id.isEmpty()) id = name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        if (category.isEmpty()) category = "Novidades";
        return new RemoteWallpaper(id, name, category, thumb, bg, home, fg,
                isNew, featured, depthMode, ready, vipOnly);
    }

    private static String first(JSONObject o, String... keys) {
        for (String key : keys) {
            String value = o.optString(key, "");
            if (value != null && !value.equals("null") && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static boolean bool(JSONObject o, boolean fallback, String... keys) {
        for (String key : keys) {
            if (o.has(key) && !o.isNull(key)) return o.optBoolean(key, fallback);
        }
        return fallback;
    }
}
