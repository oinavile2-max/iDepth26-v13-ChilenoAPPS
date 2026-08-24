package com.chilenoapps.idepth26;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String FILE = "idepth26_settings";

    static final String IMAGE_URIS = "image_uris";
    static final String CURRENT_INDEX = "current_index";
    static final String BUILTIN_INDEX = "builtin_index";

    static final String SOURCE = "source";
    static final String SOURCE_BUILTIN = "builtin";
    static final String SOURCE_USER = "user";
    static final String SOURCE_REMOTE = "remote";

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
    static final String ZOOM = "zoom";
    static final String WALLPAPER_POSITION_X = "wallpaper_position_x";
    static final String WALLPAPER_POSITION_Y = "wallpaper_position_y";
    static final String WALLPAPER_SCALE = "wallpaper_scale";

    static final String CLOCK = "clock_enabled";
    static final String CLOCK_BEHIND = "clock_behind_subject";
    static final String CLOCK_DEPTH = "clock_depth";
    static final String CLOCK_FONT = "clock_font";
    static final String CLOCK_SIZE = "clock_size";
    static final String CLOCK_Y = "clock_y";
    static final String CLOCK_COLOR_MODE = "clock_color_mode";
    static final String CLOCK_COLOR = "clock_color";
    static final String CLOCK_SHOW_DATE = "clock_show_date";

    static final String THEME_AUTO = "theme_auto";
    static final String THEME_ACCENT = "theme_accent";
    static final String THEME_SECONDARY = "theme_secondary";
    static final String THEME_BACKGROUND = "theme_background";
    static final String THEME_SURFACE = "theme_surface";

    static final String AUTO = "auto_enabled";
    static final String AUTO_MINUTES = "auto_minutes";

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
