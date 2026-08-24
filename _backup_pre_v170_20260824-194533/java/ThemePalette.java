package com.chilenoapps.idepth26;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ThemePalette {
    static final int DEFAULT_ACCENT = 0xFF8B4DFF;
    static final int DEFAULT_SECONDARY = 0xFFB97CFF;
    static final int DEFAULT_BG = 0xFF05070C;
    static final int DEFAULT_SURFACE = 0xFF111722;

    static int accent(SharedPreferences p) {
        return p.getInt(Prefs.THEME_ACCENT, DEFAULT_ACCENT);
    }

    static int secondary(SharedPreferences p) {
        return p.getInt(Prefs.THEME_SECONDARY, DEFAULT_SECONDARY);
    }

    static int background(SharedPreferences p) {
        return p.getInt(Prefs.THEME_BACKGROUND, DEFAULT_BG);
    }

    static int surface(SharedPreferences p) {
        return p.getInt(Prefs.THEME_SURFACE, DEFAULT_SURFACE);
    }

    static void refreshFromCurrentSource(Context context) {
        SharedPreferences p = Prefs.get(context);
        if (!p.getBoolean(Prefs.THEME_AUTO, true)) return;

        Bitmap bitmap = null;
        try {
            String source = p.getString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN);
            if (Prefs.SOURCE_REMOTE.equals(source)) {
                String path = p.getString(Prefs.REMOTE_BACKGROUND_PATH, "");
                if (path != null && !path.isEmpty()) bitmap = decodeFileSampled(path, 360);
            } else if (Prefs.SOURCE_USER.equals(source)) {
                Set<String> set = p.getStringSet(Prefs.IMAGE_URIS, new LinkedHashSet<>());
                if (set != null && !set.isEmpty()) {
                    List<String> uris = new ArrayList<>(set);
                    int index = Math.floorMod(p.getInt(Prefs.CURRENT_INDEX, 0), uris.size());
                    bitmap = decodeUriSampled(context, Uri.parse(uris.get(index)), 360);
                }
            } else {
                int index = Math.floorMod(p.getInt(Prefs.BUILTIN_INDEX, 0), BuiltInWallpapers.count());
                bitmap = BitmapFactory.decodeResource(context.getResources(), BuiltInWallpapers.THUMBNAILS[index]);
            }
        } catch (Exception ignored) {}

        if (bitmap != null) {
            applyFromBitmap(context, bitmap);
            try { bitmap.recycle(); } catch (Exception ignored) {}
        }
    }

    static void applyFromBitmap(Context context, Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int stepX = Math.max(1, width / 36);
        int stepY = Math.max(1, height / 48);

        long sumR = 0, sumG = 0, sumB = 0;
        int count = 0;
        int bestColor = DEFAULT_ACCENT;
        float bestScore = -1f;
        float[] hsv = new float[3];

        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int c = bitmap.getPixel(x, y);
                if (Color.alpha(c) < 180) continue;
                int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
                sumR += r; sumG += g; sumB += b; count++;

                Color.RGBToHSV(r, g, b, hsv);
                float saturation = hsv[1];
                float value = hsv[2];
                if (value < 0.16f || value > 0.96f) continue;
                float centerBias = 1f - Math.abs(value - 0.64f);
                float score = saturation * 1.3f + centerBias * 0.55f;
                if (score > bestScore) {
                    bestScore = score;
                    bestColor = c;
                }
            }
        }

        int avg = count == 0 ? DEFAULT_ACCENT : Color.rgb(
                (int) (sumR / count),
                (int) (sumG / count),
                (int) (sumB / count));

        Color.colorToHSV(bestColor, hsv);
        if (hsv[1] < 0.38f) hsv[1] = 0.38f;
        if (hsv[2] < 0.68f) hsv[2] = 0.68f;
        int accent = Color.HSVToColor(hsv);

        float[] secondaryHsv = hsv.clone();
        secondaryHsv[0] = (secondaryHsv[0] + 32f) % 360f;
        secondaryHsv[1] = Math.max(0.30f, secondaryHsv[1] * 0.86f);
        secondaryHsv[2] = Math.min(1f, Math.max(0.72f, secondaryHsv[2]));
        int secondary = Color.HSVToColor(secondaryHsv);

        int bgSeed = mix(avg, accent, 0.28f);
        int background = mix(Color.BLACK, bgSeed, 0.11f);
        int surface = mix(Color.rgb(12, 15, 22), accent, 0.14f);

        SharedPreferences p = Prefs.get(context);
        p.edit()
                .putInt(Prefs.THEME_ACCENT, accent)
                .putInt(Prefs.THEME_SECONDARY, secondary)
                .putInt(Prefs.THEME_BACKGROUND, background)
                .putInt(Prefs.THEME_SURFACE, surface)
                .apply();
    }

    static int autoClockColor(SharedPreferences p) {
        String mode = p.getString(Prefs.CLOCK_COLOR_MODE, "auto");
        if ("accent".equals(mode)) return accent(p);
        if ("custom".equals(mode)) return p.getInt(Prefs.CLOCK_COLOR, Color.WHITE);
        return Color.WHITE;
    }

    static int mix(int a, int b, float amountB) {
        amountB = Math.max(0f, Math.min(1f, amountB));
        float amountA = 1f - amountB;
        return Color.rgb(
                Math.round(Color.red(a) * amountA + Color.red(b) * amountB),
                Math.round(Color.green(a) * amountA + Color.green(b) * amountB),
                Math.round(Color.blue(a) * amountA + Color.blue(b) * amountB));
    }

    static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    static Bitmap decodeFileSampled(String path, int maxSide) {
        File file = new File(path == null ? "" : path);
        if (!file.exists()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = calculateSample(bounds.outWidth, bounds.outHeight, maxSide);
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(path, opts);
    }

    static Bitmap decodeUriSampled(Context context, Uri uri, int maxSide) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = calculateSample(bounds.outWidth, bounds.outHeight, maxSide);
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int calculateSample(int width, int height, int maxSide) {
        int sample = 1;
        int largest = Math.max(width, height);
        while (largest / sample > Math.max(160, maxSide) * 2) sample *= 2;
        return Math.max(1, sample);
    }

    private ThemePalette() {}
}
