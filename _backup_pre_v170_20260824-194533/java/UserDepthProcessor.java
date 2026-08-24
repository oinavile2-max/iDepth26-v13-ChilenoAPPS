package com.chilenoapps.idepth26;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult;
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
        if (bitmap == null) {
            callback.onComplete("", false);
            return;
        }

        File dir = new File(app.getFilesDir(), "user_depth");
        if (!dir.exists()) dir.mkdirs();
        File target = new File(dir, Integer.toHexString(uri.toString().hashCode()) + ".png");

        if (target.exists() && target.length() > 1024) {
            bitmap.recycle();
            callback.onComplete(target.getAbsolutePath(), true);
            return;
        }

        SubjectSegmenterOptions options = new SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build();
        SubjectSegmenter segmenter = SubjectSegmentation.getClient(options);

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        segmenter.process(image)
                .addOnSuccessListener(result -> {
                    Bitmap foreground = result.getForegroundBitmap();
                    boolean ok = false;
                    if (foreground != null) {
                        try (FileOutputStream out = new FileOutputStream(target)) {
                            foreground.compress(Bitmap.CompressFormat.PNG, 100, out);
                            ok = target.length() > 1024;
                        } catch (Exception ignored) {}
                    }
                    try { segmenter.close(); } catch (Exception ignored) {}
                    try { bitmap.recycle(); } catch (Exception ignored) {}
                    callback.onComplete(ok ? target.getAbsolutePath() : "", ok);
                })
                .addOnFailureListener(e -> {
                    try { segmenter.close(); } catch (Exception ignored) {}
                    try { bitmap.recycle(); } catch (Exception ignored) {}
                    callback.onComplete("", false);
                });
    }

    private UserDepthProcessor() {}
}
