package com.chilenoapps.idepth26;

final class BuiltInWallpapers {
    static final String[] NAMES = {
            "Retro Mood",
            "Noir Woman",
            "Tropical Sunset",
            "Bottle Live Preview",
            "Bottle Studio Preview",
            "Stitch Night",
            "Hooded Red Eyes",
            "Spider-Man Red",
            "Lightning McQueen Snow",
            "Lightning McQueen Night",
            "Spider-Gwen",
            "Black Spider-Man",
            "Spider-Man Comic",
            "Firefly Honkai Star Rail",
            "GTA VI",
            "Jennie Cosmopolitan",
            "Jennie Purple",
            "Mountain Tim",
            "Preity Mukhundhan",
            "Spring Flowers",
            "Zenvo Aurora Tur"
    };

    static final int[] BACKGROUNDS = {
            R.drawable.wall_01_retro_mood_cassette_bg,
            R.drawable.wall_02_noir_woman_hat_bg,
            R.drawable.wall_03_tropical_palm_sunset_bg,
            R.drawable.wall_04_bottle_live_preview_bg,
            R.drawable.wall_05_bottle_studio_preview_bg,
            R.drawable.wall_06_stitch_night_bg,
            R.drawable.wall_07_hooded_red_eyes_bg,
            R.drawable.wall_08_spiderman_red_bg,
            R.drawable.wall_09_lightning_mcqueen_snow_bg,
            R.drawable.wall_10_lightning_mcqueen_night_bg,
            R.drawable.wall_11_spider_gwen_bg,
            R.drawable.wall_12_black_spiderman_bg,
            R.drawable.wall_13_spiderman_comic_bg,
            R.drawable.wall_14_firefly_honkai_star_rail_bg,
            R.drawable.wall_15_gta_vi_bg,
            R.drawable.wall_16_jennie_cosmopolitan_bg,
            R.drawable.wall_17_jennie_purple_bg,
            R.drawable.wall_18_mountain_tim_jojo_bg,
            R.drawable.wall_19_preity_mukhundhan_bg,
            R.drawable.wall_20_spring_flowers_bg,
            R.drawable.wall_21_zenvo_aurora_tur_bg
    };

    // As imagens enviadas sao arquivos unicos, sem foreground alpha separado.
    // Usamos uma camada transparente para preservar a estrutura atual do app.
    static final int[] FOREGROUNDS = {
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg,
            R.drawable.wall_clear_fg
    };

    static final int[] THUMBNAILS = {
            R.drawable.wall_01_retro_mood_cassette_thumb,
            R.drawable.wall_02_noir_woman_hat_thumb,
            R.drawable.wall_03_tropical_palm_sunset_thumb,
            R.drawable.wall_04_bottle_live_preview_thumb,
            R.drawable.wall_05_bottle_studio_preview_thumb,
            R.drawable.wall_06_stitch_night_thumb,
            R.drawable.wall_07_hooded_red_eyes_thumb,
            R.drawable.wall_08_spiderman_red_thumb,
            R.drawable.wall_09_lightning_mcqueen_snow_thumb,
            R.drawable.wall_10_lightning_mcqueen_night_thumb,
            R.drawable.wall_11_spider_gwen_thumb,
            R.drawable.wall_12_black_spiderman_thumb,
            R.drawable.wall_13_spiderman_comic_thumb,
            R.drawable.wall_14_firefly_honkai_star_rail_thumb,
            R.drawable.wall_15_gta_vi_thumb,
            R.drawable.wall_16_jennie_cosmopolitan_thumb,
            R.drawable.wall_17_jennie_purple_thumb,
            R.drawable.wall_18_mountain_tim_jojo_thumb,
            R.drawable.wall_19_preity_mukhundhan_thumb,
            R.drawable.wall_20_spring_flowers_thumb,
            R.drawable.wall_21_zenvo_aurora_tur_thumb
    };

    static int count() { return NAMES.length; }
    private BuiltInWallpapers() {}
}
