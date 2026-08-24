package com.chilenoapps.idepth26;

import android.graphics.Typeface;

final class ClockStyles {
    static final String[] LABELS = {
            "Fina", "Condensada", "Leve", "Moderna",
            "Negrito", "Serif", "Mono", "Black"
    };

    static final String[] KEYS = {
            "thin", "condensed", "light", "regular",
            "bold", "serif", "mono", "black"
    };

    static Typeface typeface(String key) {
        if (key == null) key = "thin";
        switch (key) {
            case "condensed":
                return Typeface.create("sans-serif-condensed", Typeface.NORMAL);
            case "light":
                return Typeface.create("sans-serif-light", Typeface.NORMAL);
            case "regular":
                return Typeface.create("sans-serif", Typeface.NORMAL);
            case "bold":
                return Typeface.create("sans-serif-condensed", Typeface.BOLD);
            case "serif":
                return Typeface.create("serif", Typeface.NORMAL);
            case "mono":
                return Typeface.create("monospace", Typeface.NORMAL);
            case "black":
                return Typeface.create("sans-serif-black", Typeface.NORMAL);
            case "thin":
            default:
                return Typeface.create("sans-serif-thin", Typeface.NORMAL);
        }
    }

    static Typeface dateTypeface(String key) {
        if ("serif".equals(key)) return Typeface.create("serif", Typeface.BOLD);
        if ("mono".equals(key)) return Typeface.create("monospace", Typeface.BOLD);
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    private ClockStyles() {}
}
