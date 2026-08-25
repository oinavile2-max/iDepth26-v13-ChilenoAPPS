package com.chilenoapps.idepth26;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String FILE = "idepth26_settings";

    static final String IMAGE_URIS = "image_uris";
    static final String CURRENT_INDEX = "current_index";
    static final String BUILTIN_INDEX = "builtin_index";
    static final String USER_FOREGROUND_PATH = "user_foreground_path";
    static final String BUILTIN_FOREGROUND_PATH = "builtin_foreground_path";
    static final String ONLINE_PACK_INDEX = "online_pack_index";
    static final String ONLINE_PACK_FOREGROUND_PATH = "online_pack_foreground_path";

    static final String SOURCE = "source";
    static final String SOURCE_BUILTIN = "builtin";
    static final String SOURCE_USER = "user";
    static final String SOURCE_REMOTE = "remote";
    static final String SOURCE_ONLINE_PACK = "online_pack";

    static final String REMOTE_ID = "remote_id";
    static final String REMOTE_NAME = "remote_name";
    static final String REMOTE_CATEGORY = "remote_category";
    static final String REMOTE_BACKGROUND_PATH = "remote_background_path";
    static final String REMOTE_FOREGROUND_PATH = "remote_foreground_path";
    static final String REMOTE_HOME_PATH = "remote_home_path";
    static final String REMOTE_CLOCK_DEPTH_READY = "remote_clock_depth_ready";

    static final String FAVORITES = "favorites";

    static final String DEPTH = "depth_strength";
    static final String PARALLAX = "parallax_strength";
    static final String ZOOM = "zoom"; // compatibilidade com versões anteriores
    static final String WALLPAPER_POSITION_X = "wallpaper_position_x";
    static final String WALLPAPER_POSITION_Y = "wallpaper_position_y";
    static final String WALLPAPER_SCALE = "wallpaper_scale";
    static final String WALLPAPER_ROTATION = "wallpaper_rotation";
    static final String WALLPAPER_BRIGHTNESS = "wallpaper_brightness";
    static final String WALLPAPER_CONTRAST = "wallpaper_contrast";
    static final String WALLPAPER_DIM = "wallpaper_dim";
    static final String FOREGROUND_MOTION = "foreground_motion";

    static final String CLOCK = "clock_enabled";
    static final String CLOCK_BEHIND = "clock_behind_subject";
    static final String CLOCK_DEPTH = "clock_depth";
    static final String CLOCK_FONT = "clock_font";
    static final String CLOCK_SIZE = "clock_size";
    static final String CLOCK_X = "clock_x";
    static final String CLOCK_Y = "clock_y";
    static final String CLOCK_ALPHA = "clock_alpha";
    static final String CLOCK_SHADOW = "clock_shadow";
    static final String CLOCK_COLOR_MODE = "clock_color_mode";
    static final String CLOCK_COLOR = "clock_color";
    static final String CLOCK_SHOW_DATE = "clock_show_date";
    // Experiencia Neon Depth: relogio grande contornado, integrado ao assunto.
    static final String CLOCK_STYLE = "clock_style"; // depth_outline | solid
    static final String CLOCK_FORMAT = "clock_format"; // hours | full
    static final String CLOCK_STROKE = "clock_stroke"; // 1..12
    static final String CLOCK_FILL = "clock_fill"; // 0..100
    static final String CLOCK_GLOW = "clock_glow"; // 0..100

    static final String THEME_AUTO = "theme_auto";
    static final String THEME_ACCENT = "theme_accent";
    static final String THEME_SECONDARY = "theme_secondary";
    static final String THEME_BACKGROUND = "theme_background";
    static final String THEME_SURFACE = "theme_surface";
    static final String THEME_LUMA = "theme_luma";
    static final String THEME_PALETTE_0 = "theme_palette_0";
    static final String THEME_PALETTE_1 = "theme_palette_1";
    static final String THEME_PALETTE_2 = "theme_palette_2";
    static final String THEME_PALETTE_3 = "theme_palette_3";
    static final String THEME_PALETTE_4 = "theme_palette_4";

    static final String AUTO = "auto_enabled";
    static final String AUTO_MINUTES = "auto_minutes";

    static final String USAGE_CONSENT = "usage_consent";
    static final String USAGE_CONSENT_ASKED = "usage_consent_asked";
    static final String SUBMISSION_NOTICE_ACCEPTED = "submission_notice_accepted";

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
