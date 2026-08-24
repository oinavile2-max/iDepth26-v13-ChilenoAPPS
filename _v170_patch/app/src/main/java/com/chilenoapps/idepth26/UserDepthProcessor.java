package com.chilenoapps.idepth26;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import java.io.File;
import java.io.FileOutputStream;

final class UserDepthProcessor {
    interface Callback {
        void onComplete(String foregroundPath, boolean ok);
    }

    static void prepare(Context context, Uri uri, Callback callback) {
        Context app = context.getApplicationContext();
        Bitmap bitmap = ThemePalette.decodeUriSampled(app, uri, 1600);
        prepareBitmap(app, bitmap, "user_" + Integer.toHexString(uri.toString().hashCode()), callback);
    }

    static void prepareResource(Context context, int resId, String cacheKey, Callback callback) {
        Context app = context.getApplicationContext();
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeResource(app.getResources(), resId, opts);
        if (bitmap != null && Math.max(bitmap.getWidth(), bitmap.getHeight()) > 1800) {
            float s = 1800f / Math.max(bitmap.getWidth(), bitmap.getHeight());
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                    Math.max(1, Math.round(bitmap.getWidth() * s)),
                    Math.max(1, Math.round(bitmap.getHeight() * s)), true);
            if (scaled != bitmap) bitmap.recycle();
            bitmap = scaled;
        }
        prepareBitmap(app, bitmap, "pack_" + safe(cacheKey), callback);
    }

    private static void prepareBitmap(Context app, Bitmap bitmap, String cacheKey, Callback callback) {
        if (bitmap == null) {
            callback.onComplete("", false);
            return;
        }

        File dir = new File(app.getFilesDir(), "depth_masks");
        if (!dir.exists()) dir.mkdirs();
        File target = new File(dir, safe(cacheKey) + ".png");

        if (target.exists() && target.length() > 1024) {
            try { bitmap.recycle(); } catch (Exception ignored) {}
            callback.onComplete(target.getAbsolutePath(), true);
            return;
        }

        SubjectSegmenterOptions options = new SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build();
        SubjectSegmenter segmenter = SubjectSegmentation.getClient(options);
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        Bitmap input = bitmap;

        segmenter.process(image)
                .addOnSuccessListener(result -> {
                    Bitmap foreground = result.getForegroundBitmap();
                    boolean ok = false;
                    if (foreground != null) {
                        try (FileOutputStream out = new FileOutputStream(target)) {
                            foreground.compress(Bitmap.CompressFormat.PNG, 100, out);
                            ok = target.length() > 1024;
                        } catch (Exception ignored) {}
                        try { foreground.recycle(); } catch (Exception ignored) {}
                    }
                    try { segmenter.close(); } catch (Exception ignored) {}
                    try { input.recycle(); } catch (Exception ignored) {}
                    callback.onComplete(ok ? target.getAbsolutePath() : "", ok);
                })
                .addOnFailureListener(e -> {
                    try { segmenter.close(); } catch (Exception ignored) {}
                    try { input.recycle(); } catch (Exception ignored) {}
                    callback.onComplete("", false);
                });
    }

    private static String safe(String value) {
        String s = value == null ? "wallpaper" : value.toLowerCase().replaceAll("[^a-z0-9_-]+", "_");
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    private UserDepthProcessor() {}
}
