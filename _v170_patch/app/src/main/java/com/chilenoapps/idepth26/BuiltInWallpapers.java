package com.chilenoapps.idepth26;

final class BuiltInWallpapers {
    static final int FREE_COUNT = 5;
    static final String[] NAMES = {
            "Misty Forest", "Neon Hexagons", "Starfish Night", "Dark Roses",
            "Red Porsche", "Cassette Head", "Dope Portrait", "Shadow Mask",
            "Cool Tiger", "Geometric Dark", "Hoodie Cat", "Desert Pinup",
            "Monochrome Portrait", "Minimal Desert", "Black White Brush", "Beige Curves",
            "Raven Grenade", "Urban Camo", "Digital Portrait", "Cyber Woman",
            "Dark Batman", "Neon Face", "Venus Neon", "Feather Blue",
            "Candy Skull", "Cyberpunk Woman", "Neon Gamer Girl", "Luffy Figure",
            "Neon Hotel", "Rainy Cyber City", "Color Waves", "Neon Astronaut",
            "Classic Car Dark", "Abstract Earth", "Pixel Astronaut", "Moon Ride",
            "Polygon Tiger", "Blue Geometry", "Neutral Minimal", "Pop Art Face",
            "Tattoo Anime Girl", "Cyber Mask", "TV Head", "Outdoor Selfie",
            "Portrait Black", "Supreme Girl", "Dark Hand", "Cable Cars",
            "Retro Car", "Miles Morales Red", "Lightning McQueen Snow", "Racing McQueen Blue",
            "Spider Gwen", "Black Spider Man", "Spider Verse", "Minimal Character",
            "Beach Bonfire", "Cyberpunk City Girl", "Firefly Honkai Star Rail", "GTA VI Sunset",
            "Garage Hot Rod", "Pink Rose", "Green Leaves"
    };
    static final int[] BACKGROUNDS = {
            R.drawable.offline_001_bg, R.drawable.offline_002_bg, R.drawable.offline_003_bg, R.drawable.offline_004_bg, R.drawable.offline_005_bg,
            R.drawable.offline_006_bg, R.drawable.offline_007_bg, R.drawable.offline_008_bg, R.drawable.offline_009_bg, R.drawable.offline_010_bg,
            R.drawable.offline_011_bg, R.drawable.offline_012_bg, R.drawable.offline_013_bg, R.drawable.offline_014_bg, R.drawable.offline_015_bg,
            R.drawable.offline_016_bg, R.drawable.offline_017_bg, R.drawable.offline_018_bg, R.drawable.offline_019_bg, R.drawable.offline_020_bg,
            R.drawable.offline_021_bg, R.drawable.offline_022_bg, R.drawable.offline_023_bg, R.drawable.offline_024_bg, R.drawable.offline_025_bg,
            R.drawable.offline_026_bg, R.drawable.offline_027_bg, R.drawable.offline_028_bg, R.drawable.offline_029_bg, R.drawable.offline_030_bg,
            R.drawable.offline_031_bg, R.drawable.offline_032_bg, R.drawable.offline_033_bg, R.drawable.offline_034_bg, R.drawable.offline_035_bg,
            R.drawable.offline_036_bg, R.drawable.offline_037_bg, R.drawable.offline_038_bg, R.drawable.offline_039_bg, R.drawable.offline_040_bg,
            R.drawable.offline_041_bg, R.drawable.offline_042_bg, R.drawable.offline_043_bg, R.drawable.offline_044_bg, R.drawable.offline_045_bg,
            R.drawable.offline_046_bg, R.drawable.offline_047_bg, R.drawable.offline_048_bg, R.drawable.offline_049_bg, R.drawable.offline_050_bg,
            R.drawable.offline_051_bg, R.drawable.offline_052_bg, R.drawable.offline_053_bg, R.drawable.offline_054_bg, R.drawable.offline_055_bg,
            R.drawable.offline_056_bg, R.drawable.offline_057_bg, R.drawable.offline_058_bg, R.drawable.offline_059_bg, R.drawable.offline_060_bg,
            R.drawable.offline_061_bg, R.drawable.offline_062_bg, R.drawable.offline_063_bg
    };
    static final int[] THUMBNAILS = {
            R.drawable.offline_001_thumb, R.drawable.offline_002_thumb, R.drawable.offline_003_thumb, R.drawable.offline_004_thumb, R.drawable.offline_005_thumb,
            R.drawable.offline_006_thumb, R.drawable.offline_007_thumb, R.drawable.offline_008_thumb, R.drawable.offline_009_thumb, R.drawable.offline_010_thumb,
            R.drawable.offline_011_thumb, R.drawable.offline_012_thumb, R.drawable.offline_013_thumb, R.drawable.offline_014_thumb, R.drawable.offline_015_thumb,
            R.drawable.offline_016_thumb, R.drawable.offline_017_thumb, R.drawable.offline_018_thumb, R.drawable.offline_019_thumb, R.drawable.offline_020_thumb,
            R.drawable.offline_021_thumb, R.drawable.offline_022_thumb, R.drawable.offline_023_thumb, R.drawable.offline_024_thumb, R.drawable.offline_025_thumb,
            R.drawable.offline_026_thumb, R.drawable.offline_027_thumb, R.drawable.offline_028_thumb, R.drawable.offline_029_thumb, R.drawable.offline_030_thumb,
            R.drawable.offline_031_thumb, R.drawable.offline_032_thumb, R.drawable.offline_033_thumb, R.drawable.offline_034_thumb, R.drawable.offline_035_thumb,
            R.drawable.offline_036_thumb, R.drawable.offline_037_thumb, R.drawable.offline_038_thumb, R.drawable.offline_039_thumb, R.drawable.offline_040_thumb,
            R.drawable.offline_041_thumb, R.drawable.offline_042_thumb, R.drawable.offline_043_thumb, R.drawable.offline_044_thumb, R.drawable.offline_045_thumb,
            R.drawable.offline_046_thumb, R.drawable.offline_047_thumb, R.drawable.offline_048_thumb, R.drawable.offline_049_thumb, R.drawable.offline_050_thumb,
            R.drawable.offline_051_thumb, R.drawable.offline_052_thumb, R.drawable.offline_053_thumb, R.drawable.offline_054_thumb, R.drawable.offline_055_thumb,
            R.drawable.offline_056_thumb, R.drawable.offline_057_thumb, R.drawable.offline_058_thumb, R.drawable.offline_059_thumb, R.drawable.offline_060_thumb,
            R.drawable.offline_061_thumb, R.drawable.offline_062_thumb, R.drawable.offline_063_thumb
    };
    static final int[] FOREGROUNDS = new int[NAMES.length];
    static boolean isVipOnly(int index) { return index >= FREE_COUNT; }
    static int count() { return NAMES.length; }
    private BuiltInWallpapers() {}
}
