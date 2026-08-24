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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ThemePalette {
    static final int DEFAULT_ACCENT = 0xFFFFD400;
    static final int DEFAULT_SECONDARY = 0xFFB7A34A;
    static final int DEFAULT_BG = 0xFF050505;
    static final int DEFAULT_SURFACE = 0xFF111111;

    private static final int[] DEFAULT_PALETTE = {
            0xFFFFFFFF, 0xFFFFD400, 0xFF7E8A97, 0xFF3B4652, 0xFF171717
    };

    static int accent(SharedPreferences p) { return p.getInt(Prefs.THEME_ACCENT, DEFAULT_ACCENT); }
    static int secondary(SharedPreferences p) { return p.getInt(Prefs.THEME_SECONDARY, DEFAULT_SECONDARY); }
    static int background(SharedPreferences p) { return p.getInt(Prefs.THEME_BACKGROUND, DEFAULT_BG); }
    static int surface(SharedPreferences p) { return p.getInt(Prefs.THEME_SURFACE, DEFAULT_SURFACE); }

    static int[] palette(SharedPreferences p) {
        return new int[]{
                p.getInt(Prefs.THEME_PALETTE_0, DEFAULT_PALETTE[0]),
                p.getInt(Prefs.THEME_PALETTE_1, DEFAULT_PALETTE[1]),
                p.getInt(Prefs.THEME_PALETTE_2, DEFAULT_PALETTE[2]),
                p.getInt(Prefs.THEME_PALETTE_3, DEFAULT_PALETTE[3]),
                p.getInt(Prefs.THEME_PALETTE_4, DEFAULT_PALETTE[4])
        };
    }

    static void refreshFromCurrentSource(Context context) {
        SharedPreferences p = Prefs.get(context);
        if (!p.getBoolean(Prefs.THEME_AUTO, true)) return;

        Bitmap bitmap = null;
        try {
            String source = p.getString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN);
            if (Prefs.SOURCE_REMOTE.equals(source)) {
                String path = p.getString(Prefs.REMOTE_BACKGROUND_PATH, "");
                if (path != null && !path.isEmpty()) bitmap = decodeFileSampled(path, 700);
            } else if (Prefs.SOURCE_ONLINE_PACK.equals(source)) {
                int index = Math.floorMod(p.getInt(Prefs.ONLINE_PACK_INDEX, 0), OnlinePackWallpapers.count());
                bitmap = BitmapFactory.decodeResource(context.getResources(), OnlinePackWallpapers.BACKGROUNDS[index]);
            } else if (Prefs.SOURCE_USER.equals(source)) {
                Set<String> set = p.getStringSet(Prefs.IMAGE_URIS, new LinkedHashSet<>());
                if (set != null && !set.isEmpty()) {
                    List<String> uris = new ArrayList<>(set);
                    int index = Math.floorMod(p.getInt(Prefs.CURRENT_INDEX, 0), uris.size());
                    bitmap = decodeUriSampled(context, Uri.parse(uris.get(index)), 700);
                }
            } else {
                int index = Math.floorMod(p.getInt(Prefs.BUILTIN_INDEX, 0), BuiltInWallpapers.count());
                bitmap = BitmapFactory.decodeResource(context.getResources(), BuiltInWallpapers.BACKGROUNDS[index]);
            }
        } catch (Exception ignored) {}

        if (bitmap != null) {
            applyFromBitmap(context, bitmap);
            try { bitmap.recycle(); } catch (Exception ignored) {}
        }
    }

    /**
     * Paleta fiel: as cinco cores são médias de grupos de pixels realmente presentes no wallpaper.
     * Não cria matizes artificiais e evita cinco tons praticamente iguais.
     */
    static void applyFromBitmap(Context context, Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return;

        List<Integer> colors = extractDominantColors(bitmap, 5);
        while (colors.size() < 5) {
            colors.add(colors.isEmpty() ? DEFAULT_PALETTE[colors.size()] : colors.get(colors.size() - 1));
        }

        long sumR = 0, sumG = 0, sumB = 0, count = 0;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int stepX = Math.max(1, w / 64);
        int stepY = Math.max(1, h / 88);
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int c = bitmap.getPixel(x, y);
                if (Color.alpha(c) < 160) continue;
                sumR += Color.red(c);
                sumG += Color.green(c);
                sumB += Color.blue(c);
                count++;
            }
        }
        int avg = count == 0 ? colors.get(0) : Color.rgb(
                (int) (sumR / count), (int) (sumG / count), (int) (sumB / count));

        int accent = chooseAccent(colors);
        int secondary = chooseSecondary(colors, accent);
        int background = mix(Color.BLACK, avg, 0.10f);
        int surface = mix(Color.rgb(15, 15, 15), avg, 0.14f);
        int luma1000 = Math.round(relativeLuminance(avg) * 1000f);

        SharedPreferences.Editor e = Prefs.get(context).edit()
                .putInt(Prefs.THEME_ACCENT, accent)
                .putInt(Prefs.THEME_SECONDARY, secondary)
                .putInt(Prefs.THEME_BACKGROUND, background)
                .putInt(Prefs.THEME_SURFACE, surface)
                .putInt(Prefs.THEME_LUMA, luma1000)
                .putInt(Prefs.THEME_PALETTE_0, colors.get(0))
                .putInt(Prefs.THEME_PALETTE_1, colors.get(1))
                .putInt(Prefs.THEME_PALETTE_2, colors.get(2))
                .putInt(Prefs.THEME_PALETTE_3, colors.get(3))
                .putInt(Prefs.THEME_PALETTE_4, colors.get(4));
        e.apply();
    }

    private static final class Bin {
        int count;
        long r, g, b;
        void add(int color) {
            count++;
            r += Color.red(color);
            g += Color.green(color);
            b += Color.blue(color);
        }
        int color() {
            if (count <= 0) return Color.BLACK;
            return Color.rgb((int) (r / count), (int) (g / count), (int) (b / count));
        }
    }

    private static List<Integer> extractDominantColors(Bitmap bitmap, int wanted) {
        Map<Integer, Bin> bins = new HashMap<>();
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int stepX = Math.max(1, w / 88);
        int stepY = Math.max(1, h / 116);

        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int c = bitmap.getPixel(x, y);
                if (Color.alpha(c) < 160) continue;
                int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
                // 4 bits/canal: suficiente para agrupar pixels próximos, mantendo a média real do grupo.
                int key = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
                Bin bin = bins.get(key);
                if (bin == null) { bin = new Bin(); bins.put(key, bin); }
                bin.add(c);
            }
        }

        List<Bin> ordered = new ArrayList<>(bins.values());
        ordered.sort(Comparator.comparingInt((Bin b) -> b.count).reversed());
        List<Integer> result = new ArrayList<>();

        // Primeiro pega grupos dominantes com boa separação perceptual.
        for (Bin bin : ordered) {
            int c = bin.color();
            boolean tooClose = false;
            for (int existing : result) {
                if (colorDistance(c, existing) < 52f) { tooClose = true; break; }
            }
            if (!tooClose) result.add(c);
            if (result.size() >= wanted) break;
        }

        // Se a imagem for muito monocromática, relaxa a separação sem inventar cores.
        if (result.size() < wanted) {
            for (Bin bin : ordered) {
                int c = bin.color();
                boolean exists = false;
                for (int existing : result) {
                    if (colorDistance(c, existing) < 24f) { exists = true; break; }
                }
                if (!exists) result.add(c);
                if (result.size() >= wanted) break;
            }
        }
        return result;
    }

    private static int chooseAccent(List<Integer> colors) {
        int best = colors.get(0);
        float bestScore = -999f;
        float[] hsv = new float[3];
        for (int c : colors) {
            Color.colorToHSV(c, hsv);
            float l = relativeLuminance(c);
            float score = hsv[1] * 1.25f + (1f - Math.abs(hsv[2] - 0.68f)) * 0.35f;
            if (l < 0.035f || l > 0.94f) score -= 0.55f;
            if (score > bestScore) { bestScore = score; best = c; }
        }
        return best;
    }

    private static int chooseSecondary(List<Integer> colors, int accent) {
        int best = colors.get(0);
        float bestDist = -1f;
        for (int c : colors) {
            float d = colorDistance(c, accent);
            if (d > bestDist) { bestDist = d; best = c; }
        }
        return best;
    }

    static int clockColor(SharedPreferences p, Bitmap bitmap, int xPercent, int yPercent, int sizePercent) {
        String mode = p.getString(Prefs.CLOCK_COLOR_MODE, "auto");
        if ("custom".equals(mode)) return p.getInt(Prefs.CLOCK_COLOR, Color.WHITE);
        if (bitmap != null) return bestPaletteClockColor(p, bitmap, xPercent, yPercent, sizePercent);
        return autoClockColor(p);
    }

    /** AUTO usa primeiro as cores extraídas do próprio wallpaper; preto/branco só entra como fallback de legibilidade. */
    static int autoClockColor(SharedPreferences p) {
        if ("custom".equals(p.getString(Prefs.CLOCK_COLOR_MODE, "auto"))) {
            return p.getInt(Prefs.CLOCK_COLOR, Color.WHITE);
        }
        int[] colors = palette(p);
        float bgLuma = p.getInt(Prefs.THEME_LUMA, 350) / 1000f;
        int best = colors[0];
        float bestContrast = contrastRatio(relativeLuminance(best), bgLuma);
        for (int c : colors) {
            float contrast = contrastRatio(relativeLuminance(c), bgLuma);
            if (contrast > bestContrast) { bestContrast = contrast; best = c; }
        }
        if (bestContrast >= 2.2f) return best;
        return contrastRatio(0f, bgLuma) >= contrastRatio(1f, bgLuma) ? Color.BLACK : Color.WHITE;
    }

    static int bestPaletteClockColor(SharedPreferences p, Bitmap bitmap, int xPercent, int yPercent, int sizePercent) {
        float localLuma = sampleLocalLuma(bitmap, xPercent, yPercent, sizePercent);
        int[] colors = palette(p);
        int best = colors[0];
        float bestContrast = contrastRatio(relativeLuminance(best), localLuma);
        for (int c : colors) {
            float contrast = contrastRatio(relativeLuminance(c), localLuma);
            if (contrast > bestContrast) { bestContrast = contrast; best = c; }
        }
        if (bestContrast >= 2.15f) return best;
        float black = contrastRatio(0f, localLuma);
        float white = contrastRatio(1f, localLuma);
        return black >= white ? Color.BLACK : Color.WHITE;
    }

    static int bestClockColor(Bitmap bitmap, int xPercent, int yPercent, int sizePercent) {
        float luma = sampleLocalLuma(bitmap, xPercent, yPercent, sizePercent);
        return luma > 0.52f ? Color.BLACK : Color.WHITE;
    }

    private static float sampleLocalLuma(Bitmap bitmap, int xPercent, int yPercent, int sizePercent) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return 0.35f;
        float cx = clamp01(xPercent / 100f);
        float cy = clamp01(yPercent / 100f);
        float halfW = Math.min(0.36f, 0.17f + (sizePercent / 100f) * 0.08f);
        float halfH = Math.min(0.23f, 0.09f + (sizePercent / 100f) * 0.05f);
        int left = Math.max(0, Math.round(bitmap.getWidth() * (cx - halfW)));
        int right = Math.min(bitmap.getWidth() - 1, Math.round(bitmap.getWidth() * (cx + halfW)));
        int top = Math.max(0, Math.round(bitmap.getHeight() * (cy - halfH)));
        int bottom = Math.min(bitmap.getHeight() - 1, Math.round(bitmap.getHeight() * (cy + halfH)));

        double sum = 0;
        int count = 0;
        int sx = Math.max(1, Math.max(1, right - left) / 28);
        int sy = Math.max(1, Math.max(1, bottom - top) / 18);
        for (int y = top; y <= bottom; y += sy) {
            for (int x = left; x <= right; x += sx) {
                int c = bitmap.getPixel(x, y);
                if (Color.alpha(c) < 160) continue;
                sum += relativeLuminance(c);
                count++;
            }
        }
        return count == 0 ? 0.35f : (float) (sum / count);
    }

    private static float contrastRatio(float a, float b) {
        float lighter = Math.max(a, b);
        float darker = Math.min(a, b);
        return (lighter + 0.05f) / (darker + 0.05f);
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

    static float relativeLuminance(int color) {
        double r = linear(Color.red(color) / 255.0);
        double g = linear(Color.green(color) / 255.0);
        double b = linear(Color.blue(color) / 255.0);
        return (float) (0.2126 * r + 0.7152 * g + 0.0722 * b);
    }

    private static double linear(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static float colorDistance(int a, int b) {
        float dr = Color.red(a) - Color.red(b);
        float dg = Color.green(a) - Color.green(b);
        float db = Color.blue(a) - Color.blue(b);
        return (float) Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

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
        } catch (Exception ignored) { return null; }
    }

    private static int calculateSample(int width, int height, int maxSide) {
        int sample = 1;
        int largest = Math.max(width, height);
        while (largest / sample > Math.max(160, maxSide) * 2) sample *= 2;
        return Math.max(1, sample);
    }

    private ThemePalette() {}
}
