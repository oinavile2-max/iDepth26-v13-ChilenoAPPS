package com.chilenoapps.idepth26;

import android.graphics.Typeface;

final class ClockStyles {
    static final String[] LABELS = {
            "Fina", "Condensada", "Leve", "Moderna",
            "Média", "Arredondada", "Negrito", "Black",
            "Serifada", "Serifada forte", "Clássica", "Mono",
            "Display", "Robusta"
    };

    static final String[] KEYS = {
            "thin", "condensed", "light", "regular",
            "medium", "rounded", "bold", "black",
            "serif", "serif_bold", "serif_italic", "mono",
            "display", "strong"
    };

    static Typeface typeface(String key) {
        if (key == null) key = "condensed";
        switch (key) {
            case "thin":
                return Typeface.create("sans-serif-thin", Typeface.NORMAL);
            case "condensed":
                return Typeface.create("sans-serif-condensed", Typeface.NORMAL);
            case "light":
                return Typeface.create("sans-serif-light", Typeface.NORMAL);
            case "regular":
                return Typeface.create("sans-serif", Typeface.NORMAL);
            case "medium":
                return Typeface.create("sans-serif-medium", Typeface.NORMAL);
            case "rounded":
                return Typeface.create("sans-serif-rounded", Typeface.NORMAL);
            case "bold":
                return Typeface.create("sans-serif", Typeface.BOLD);
            case "black":
                return Typeface.create("sans-serif-black", Typeface.NORMAL);
            case "serif":
                return Typeface.create("serif", Typeface.NORMAL);
            case "serif_bold":
                return Typeface.create("serif", Typeface.BOLD);
            case "serif_italic":
                return Typeface.create("serif", Typeface.ITALIC);
            case "mono":
                return Typeface.create("monospace", Typeface.NORMAL);
            case "display":
                return Typeface.create("sans-serif-condensed-medium", Typeface.NORMAL);
            case "strong":
                return Typeface.create("sans-serif-condensed", Typeface.BOLD);
            default:
                return Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        }
    }

    static Typeface dateTypeface(String key) {
        if ("serif".equals(key) || "serif_bold".equals(key) || "serif_italic".equals(key)) {
            return Typeface.create("serif", Typeface.BOLD);
        }
        if ("mono".equals(key)) {
            return Typeface.create("monospace", Typeface.BOLD);
        }
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    static String labelFor(String key) {
        if (key == null) return LABELS[1];
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) return LABELS[i];
        }
        return key;
    }

    private ClockStyles() {}
}