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
    private int accent;
    private int surface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        accent = ThemePalette.accent(prefs);
        surface = ThemePalette.surface(prefs);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        preview = new AdjustView();
        root.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(18), dp(18), dp(18), dp(14));
        top.setBackgroundColor(0x72000000);

        TextView title = new TextView(this);
        title.setText("Ajustar wallpaper");
        title.setTextColor(Color.WHITE);
        title.setTextSize(23);
        title.setTypeface(ClockStyles.dateTypeface("regular"));
        top.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Arraste para mover • pinça com 2 dedos para zoom • toque 2x para redefinir");
        hint.setTextColor(0xFFD6DAE2);
        hint.setTextSize(13);
        top.addView(hint, marginTop(5));

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        root.addView(top, topLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(dp(10), dp(10), dp(10), dp(18));
        actions.setBackgroundColor(0xCC05070C);

        Button cancel = button("Cancelar", false);
        cancel.setOnClickListener(v -> finish());
        actions.addView(cancel, weight());

        Button reset = button("Redefinir", false);
        reset.setOnClickListener(v -> preview.resetTransform());
        actions.addView(reset, weight());

        Button save = button("Salvar", true);
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

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(primary ? accent : ThemePalette.mix(surface, accent, 0.12f));
        d.setCornerRadius(dp(16));
        if (!primary) d.setStroke(dp(1), ThemePalette.withAlpha(accent, 80));
        b.setBackground(d);
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(3);
        p.rightMargin = dp(3);
        return p;
    }

    private LinearLayout.LayoutParams marginTop(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(top);
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

            scaleDetector = new ScaleGestureDetector(WallpaperAdjustActivity.this,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override
                        public boolean onScale(ScaleGestureDetector detector) {
                            userScale = clamp(userScale * detector.getScaleFactor(), 1f, 2.2f);
                            invalidate();
                            return true;
                        }
                    });

            gestureDetector = new GestureDetector(WallpaperAdjustActivity.this,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override public boolean onDown(MotionEvent e) { return true; }
                        @Override public boolean onDoubleTap(MotionEvent e) {
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
            if (prefs.getBoolean(Prefs.CLOCK, true) && prefs.getBoolean(Prefs.CLOCK_BEHIND, true)) drawClock(canvas);
            if (foreground != null) drawLayer(canvas, foreground, 0.032f);
            if (prefs.getBoolean(Prefs.CLOCK, true) && !prefs.getBoolean(Prefs.CLOCK_BEHIND, true)) drawClock(canvas);
        }

        private void drawLayer(Canvas canvas, Bitmap bitmap, float extraScale) {
            float cw = canvas.getWidth();
            float ch = canvas.getHeight();
            float bw = bitmap.getWidth();
            float bh = bitmap.getHeight();
            float base = Math.max(cw / bw, ch / bh);
            float safety = 1f + (prefs.getInt(Prefs.ZOOM, 40) / 100f) * 0.25f + extraScale;
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
            int size = prefs.getInt(Prefs.CLOCK_SIZE, 100);
            float timeSize = Math.max(62f, width * 0.205f * (size / 100f));
            float dateSize = Math.max(16f, timeSize * 0.165f);
            int color = ThemePalette.autoClockColor(prefs);
            String font = prefs.getString(Prefs.CLOCK_FONT, "condensed");

            timePaint.setTextAlign(Paint.Align.CENTER);
            timePaint.setTypeface(ClockStyles.typeface(font));
            timePaint.setColor(color);
            timePaint.setTextSize(timeSize);
            timePaint.setShadowLayer(12f, 0f, 3f, 0x85000000);

            datePaint.setTextAlign(Paint.Align.CENTER);
            datePaint.setTypeface(ClockStyles.dateTypeface(font));
            datePaint.setColor(color);
            datePaint.setTextSize(dateSize);
            datePaint.setShadowLayer(7f, 0f, 2f, 0x72000000);

            Date now = new Date();
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
            String date = new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
                    .format(now).toUpperCase(Locale.getDefault());
            float baseline = height * (prefs.getInt(Prefs.CLOCK_Y, 24) / 100f) + timeSize * 0.82f;
            canvas.drawText(time, width / 2f, baseline, timePaint);
            if (prefs.getBoolean(Prefs.CLOCK_SHOW_DATE, true)) {
                canvas.drawText(date, width / 2f, baseline - timeSize * 1.06f, datePaint);
            }
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
            float safety = 1f + (prefs.getInt(Prefs.ZOOM, 40) / 100f) * 0.25f;
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
