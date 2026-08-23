package com.chilenoapps.idepth26;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class WallpaperAdjustActivity extends Activity {
    private SharedPreferences prefs;
    private AdjustView preview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        preview = new AdjustView();
        root.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(18), dp(20), dp(18), dp(12));
        top.setBackgroundColor(0x66000000);

        TextView title = new TextView(this);
        title.setText("Ajustar wallpaper");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Arraste para mover • belisque com dois dedos para ampliar/reduzir • toque duas vezes para redefinir");
        hint.setTextColor(0xFFE0E0E0);
        hint.setTextSize(13);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(5);
        top.addView(hint, hp);

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        root.addView(top, topLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(dp(12), dp(10), dp(12), dp(18));
        actions.setBackgroundColor(0x88000000);

        Button cancel = button("Cancelar");
        cancel.setOnClickListener(v -> finish());
        actions.addView(cancel, weight());

        Button reset = button("Redefinir");
        reset.setOnClickListener(v -> preview.resetTransform());
        actions.addView(reset, weight());

        Button save = button("Salvar ajuste");
        save.setOnClickListener(v -> {
            preview.saveTransform();
            sendBroadcast(new Intent(DepthWallpaperService.ACTION_REFRESH).setPackage(getPackageName()));
            Toast.makeText(this, "Ajuste salvo.", Toast.LENGTH_SHORT).show();
            finish();
        });
        actions.addView(save, weight());

        FrameLayout.LayoutParams actionsLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(actions, actionsLp);

        setContentView(root);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(3);
        p.rightMargin = dp(3);
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class AdjustView extends View {
        private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ScaleGestureDetector scaleDetector;
        private final GestureDetector gestureDetector;

        private Bitmap background;
        private Bitmap foreground;
        private float posX;
        private float posY;
        private float userScale;
        private float lastX;
        private float lastY;
        private boolean dragging;

        AdjustView() {
            super(WallpaperAdjustActivity.this);
            setBackgroundColor(Color.BLACK);
            posX = clamp(prefs.getFloat(Prefs.WALLPAPER_POSITION_X, 0f), -1f, 1f);
            posY = clamp(prefs.getFloat(Prefs.WALLPAPER_POSITION_Y, 0f), -1f, 1f);
            userScale = clamp(prefs.getFloat(Prefs.WALLPAPER_SCALE, 1f), 1f, 2.2f);
            loadLayers();

            timePaint.setColor(Color.WHITE);
            timePaint.setTextAlign(Paint.Align.CENTER);
            timePaint.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
            datePaint.setColor(0xEFFFFFFF);
            datePaint.setTextAlign(Paint.Align.CENTER);
            datePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

            scaleDetector = new ScaleGestureDetector(WallpaperAdjustActivity.this,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override
                        public boolean onScale(ScaleGestureDetector detector) {
                            userScale = clamp(userScale * detector.getScaleFactor(), 1f, 2.2f);
                            posX = clamp(posX, -1f, 1f);
                            posY = clamp(posY, -1f, 1f);
                            invalidate();
                            return true;
                        }
                    });

            gestureDetector = new GestureDetector(WallpaperAdjustActivity.this,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(MotionEvent e) {
                            return true;
                        }

                        @Override
                        public boolean onDoubleTap(MotionEvent e) {
                            resetTransform();
                            return true;
                        }
                    });
        }

        private void loadLayers() {
            String source = prefs.getString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN);
            if (Prefs.SOURCE_USER.equals(source)) {
                Set<String> set = prefs.getStringSet(Prefs.IMAGE_URIS, new LinkedHashSet<>());
                if (set != null && !set.isEmpty()) {
                    List<String> uris = new ArrayList<>(set);
                    int index = Math.floorMod(prefs.getInt(Prefs.CURRENT_INDEX, 0), uris.size());
                    try (InputStream in = getContentResolver().openInputStream(Uri.parse(uris.get(index)))) {
                        background = BitmapFactory.decodeStream(in);
                    } catch (Exception ignored) {}
                }
            } else if (Prefs.SOURCE_REMOTE.equals(source)) {
                String bgPath = prefs.getString(Prefs.REMOTE_BACKGROUND_PATH, "");
                String fgPath = prefs.getString(Prefs.REMOTE_FOREGROUND_PATH, "");
                if (bgPath != null && !bgPath.isEmpty()) background = BitmapFactory.decodeFile(bgPath);
                if (fgPath != null && !fgPath.isEmpty()) foreground = BitmapFactory.decodeFile(fgPath);
            }

            if (background == null) {
                int index = Math.floorMod(prefs.getInt(Prefs.BUILTIN_INDEX, 0), BuiltInWallpapers.count());
                background = BitmapFactory.decodeResource(getResources(), BuiltInWallpapers.BACKGROUNDS[index]);
                foreground = BitmapFactory.decodeResource(getResources(), BuiltInWallpapers.FOREGROUNDS[index]);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.BLACK);
            if (background == null) return;

            drawLayer(canvas, background, 0f);
            if (prefs.getBoolean(Prefs.CLOCK, true) && prefs.getBoolean(Prefs.CLOCK_BEHIND, true)) {
                drawClock(canvas);
            }
            if (foreground != null) drawLayer(canvas, foreground, 0.018f);
            if (prefs.getBoolean(Prefs.CLOCK, true) && !prefs.getBoolean(Prefs.CLOCK_BEHIND, true)) {
                drawClock(canvas);
            }
        }

        private void drawLayer(Canvas canvas, Bitmap bitmap, float extraScale) {
            float cw = canvas.getWidth();
            float ch = canvas.getHeight();
            float bw = bitmap.getWidth();
            float bh = bitmap.getHeight();
            float base = Math.max(cw / bw, ch / bh);
            float safety = 1f + (prefs.getInt(Prefs.ZOOM, 38) / 100f) * 0.24f + extraScale;
            float scale = base * safety * userScale;
            float dw = bw * scale;
            float dh = bh * scale;
            float maxX = Math.max(0f, (dw - cw) / 2f);
            float maxY = Math.max(0f, (dh - ch) / 2f);
            float left = (cw - dw) / 2f + posX * maxX;
            float top = (ch - dh) / 2f + posY * maxY;
            canvas.drawBitmap(bitmap, null, new RectF(left, top, left + dw, top + dh), imagePaint);
        }

        private void drawClock(Canvas canvas) {
            float width = canvas.getWidth();
            float height = canvas.getHeight();
            float timeSize = Math.max(60f, width * 0.19f);
            float dateSize = Math.max(18f, width * 0.043f);
            timePaint.setTextSize(timeSize);
            timePaint.setShadowLayer(12f, 0f, 3f, 0x65000000);
            datePaint.setTextSize(dateSize);
            datePaint.setShadowLayer(8f, 0f, 2f, 0x55000000);
            Date now = new Date();
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
            String date = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now);
            float baseline = height * 0.235f;
            canvas.drawText(time, width / 2f, baseline, timePaint);
            canvas.drawText(date, width / 2f, baseline + dateSize * 1.7f, datePaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);

            if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = event.getX();
                        lastY = event.getY();
                        dragging = true;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (dragging && background != null) {
                            float dx = event.getX() - lastX;
                            float dy = event.getY() - lastY;
                            lastX = event.getX();
                            lastY = event.getY();
                            float[] overflow = currentOverflow();
                            if (overflow[0] > 1f) posX = clamp(posX + dx / overflow[0], -1f, 1f);
                            if (overflow[1] > 1f) posY = clamp(posY + dy / overflow[1], -1f, 1f);
                            invalidate();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        dragging = false;
                        return true;
                }
            }
            return true;
        }

        private float[] currentOverflow() {
            if (background == null || getWidth() == 0 || getHeight() == 0) return new float[]{1f, 1f};
            float bw = background.getWidth();
            float bh = background.getHeight();
            float base = Math.max(getWidth() / bw, getHeight() / bh);
            float safety = 1f + (prefs.getInt(Prefs.ZOOM, 38) / 100f) * 0.24f;
            float dw = bw * base * safety * userScale;
            float dh = bh * base * safety * userScale;
            return new float[]{Math.max(1f, (dw - getWidth()) / 2f), Math.max(1f, (dh - getHeight()) / 2f)};
        }

        void resetTransform() {
            posX = 0f;
            posY = 0f;
            userScale = 1f;
            invalidate();
        }

        void saveTransform() {
            prefs.edit()
                    .putFloat(Prefs.WALLPAPER_POSITION_X, posX)
                    .putFloat(Prefs.WALLPAPER_POSITION_Y, posY)
                    .putFloat(Prefs.WALLPAPER_SCALE, userScale)
                    .apply();
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
