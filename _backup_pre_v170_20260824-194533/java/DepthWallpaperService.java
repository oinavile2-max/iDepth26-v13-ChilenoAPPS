package com.chilenoapps.idepth26;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DepthWallpaperService extends WallpaperService {
    public static final String ACTION_REFRESH = DepthWallpaperService.class.getName() + ".REFRESH";

    @Override
    public Engine onCreateEngine() {
        return new DepthEngine();
    }

    private class DepthEngine extends Engine implements SensorEventListener {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint clockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Runnable drawRunnable = this::drawFrame;
        private final SharedPreferences prefs = Prefs.get(DepthWallpaperService.this);

        private SensorManager sensorManager;
        private Sensor rotationSensor;
        private Bitmap background;
        private Bitmap foreground;
        private boolean visible;
        private float targetX, targetY;
        private float currentX, currentY;
        private float velocityX, velocityY;
        private String lastKey = "";

        private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                loadSelectedLayers(true);
                drawFrame();
            }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (rotationSensor == null) rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

            clockPaint.setTextAlign(Paint.Align.CENTER);
            datePaint.setTextAlign(Paint.Align.CENTER);

            IntentFilter filter = new IntentFilter(ACTION_REFRESH);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(refreshReceiver, filter);

            loadSelectedLayers(true);
        }

        @Override
        public void onDestroy() {
            handler.removeCallbacks(drawRunnable);
            if (sensorManager != null) sensorManager.unregisterListener(this);
            try { unregisterReceiver(refreshReceiver); } catch (Exception ignored) {}
            recycleLayers();
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) {
                if (rotationSensor != null) sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
                loadSelectedLayers(false);
                drawFrame();
            } else {
                handler.removeCallbacks(drawRunnable);
                if (sensorManager != null) sensorManager.unregisterListener(this);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            drawFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            visible = false;
            handler.removeCallbacks(drawRunnable);
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (rotationSensor == null || event.sensor.getType() != rotationSensor.getType()) return;
            float[] matrix = new float[9];
            float[] orientation = new float[3];
            SensorManager.getRotationMatrixFromVector(matrix, event.values);
            SensorManager.getOrientation(matrix, orientation);

            // Amplitude um pouco maior, mas com suavização de mola para não ficar tremendo.
            targetX = clamp(orientation[2] / 0.47f, -1f, 1f);
            targetY = clamp(orientation[1] / 0.52f, -1f, 1f);
        }

        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        private void loadSelectedLayers(boolean force) {
            String source = prefs.getString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN);
            if (Prefs.SOURCE_USER.equals(source)) loadUserImage(force);
            else if (Prefs.SOURCE_REMOTE.equals(source)) loadRemote(force);
            else loadBuiltin(force);
        }

        private void loadBuiltin(boolean force) {
            int index = Math.floorMod(prefs.getInt(Prefs.BUILTIN_INDEX, 0), BuiltInWallpapers.count());
            String key = "b:" + index;
            if (!force && key.equals(lastKey) && background != null) return;
            recycleLayers();
            background = BitmapFactory.decodeResource(getResources(), BuiltInWallpapers.BACKGROUNDS[index]);
            foreground = BitmapFactory.decodeResource(getResources(), BuiltInWallpapers.FOREGROUNDS[index]);
            lastKey = key;
        }

        private void loadRemote(boolean force) {
            String bgPath = prefs.getString(Prefs.REMOTE_BACKGROUND_PATH, "");
            String fgPath = prefs.getString(Prefs.REMOTE_FOREGROUND_PATH, "");
            String id = prefs.getString(Prefs.REMOTE_ID, "remote");
            String key = "r:" + id + ":" + bgPath;
            if (!force && key.equals(lastKey) && background != null) return;
            if (bgPath == null || bgPath.isEmpty()) {
                prefs.edit().putString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN).apply();
                loadBuiltin(force);
                return;
            }

            Bitmap bg = BitmapFactory.decodeFile(bgPath);
            if (bg == null) {
                prefs.edit().putString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN).apply();
                loadBuiltin(force);
                return;
            }
            Bitmap fg = null;
            if (fgPath != null && !fgPath.isEmpty()) fg = BitmapFactory.decodeFile(fgPath);
            recycleLayers();
            background = bg;
            foreground = fg;
            lastKey = key;
        }

        private void loadUserImage(boolean force) {
            Set<String> set = prefs.getStringSet(Prefs.IMAGE_URIS, new LinkedHashSet<>());
            if (set == null || set.isEmpty()) {
                prefs.edit().putString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN).apply();
                loadBuiltin(force);
                return;
            }
            List<String> uris = new ArrayList<>(set);
            int index = Math.floorMod(prefs.getInt(Prefs.CURRENT_INDEX, 0), uris.size());
            String uri = uris.get(index);
            String fgPath = prefs.getString(Prefs.USER_FOREGROUND_PATH, "");
            String key = "u:" + uri + ":" + fgPath;
            if (!force && key.equals(lastKey) && background != null) return;
            try (InputStream in = getContentResolver().openInputStream(Uri.parse(uri))) {
                Bitmap decoded = BitmapFactory.decodeStream(in);
                if (decoded != null) {
                    Bitmap fg = null;
                    if (fgPath != null && !fgPath.isEmpty()) fg = BitmapFactory.decodeFile(fgPath);
                    recycleLayers();
                    background = decoded;
                    foreground = fg;
                    lastKey = key;
                }
            } catch (Exception ignored) {}
        }

        private void recycleLayers() {
            if (background != null) background.recycle();
            if (foreground != null) foreground.recycle();
            background = null;
            foreground = null;
        }

        private void updateMotion() {
            // spring + damping: movimento forte, porém estável.
            float spring = 0.095f;
            float damping = 0.80f;
            velocityX = (velocityX + (targetX - currentX) * spring) * damping;
            velocityY = (velocityY + (targetY - currentY) * spring) * damping;
            currentX += velocityX;
            currentY += velocityY;
        }

        private void drawFrame() {
            handler.removeCallbacks(drawRunnable);
            updateMotion();

            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;
                canvas.drawColor(Color.BLACK);

                if (background == null) {
                    drawEmpty(canvas);
                } else {
                    boolean clockEnabled = prefs.getBoolean(Prefs.CLOCK, true);
                    boolean behind = prefs.getBoolean(Prefs.CLOCK_BEHIND, true);

                    drawDepthLayer(canvas, background, 0.34f, -0.38f, 0.000f);
                    drawAmbientShade(canvas);

                    if (clockEnabled && behind) drawClock(canvas);

                    if (foreground != null) {
                        drawDepthLayer(canvas, foreground, 1.20f, 0.78f, 0.032f);
                    }

                    if (clockEnabled && !behind) drawClock(canvas);
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }

            if (visible) handler.postDelayed(drawRunnable, 33L);
        }

        private void drawDepthLayer(Canvas canvas, Bitmap bitmap, float plane, float rotationFactor, float extraScale) {
            float cw = canvas.getWidth();
            float ch = canvas.getHeight();
            float bw = bitmap.getWidth();
            float bh = bitmap.getHeight();
            if (bw <= 0 || bh <= 0) return;

            float base = Math.max(cw / bw, ch / bh);
            float safety = 1f + (prefs.getInt(Prefs.ZOOM, 40) / 100f) * 0.25f + extraScale;
            float userScale = clamp(prefs.getFloat(Prefs.WALLPAPER_SCALE, 1f), 1f, 2.2f);
            float scale = base * safety * userScale;

            // Variação sutil de escala com inclinação aumenta a percepção de profundidade.
            float parallaxStrength = prefs.getInt(Prefs.PARALLAX, 68) / 100f;
            float depthStrength = prefs.getInt(Prefs.DEPTH, 78) / 100f;
            float breathing = 1f + Math.abs(currentY) * 0.010f * depthStrength * plane;
            scale *= breathing;

            float dw = bw * scale;
            float dh = bh * scale;
            float maxX = Math.max(0f, (dw - cw) / 2f);
            float maxY = Math.max(0f, (dh - ch) / 2f);

            float savedX = clamp(prefs.getFloat(Prefs.WALLPAPER_POSITION_X, 0f), -1f, 1f);
            float savedY = clamp(prefs.getFloat(Prefs.WALLPAPER_POSITION_Y, 0f), -1f, 1f);

            float sensorX = currentX * maxX * parallaxStrength * depthStrength * plane;
            float sensorY = currentY * maxY * parallaxStrength * depthStrength * plane;
            float offsetX = savedX * maxX + sensorX;
            float offsetY = savedY * maxY + sensorY;

            float left = (cw - dw) / 2f + offsetX;
            float top = (ch - dh) / 2f + offsetY;

            canvas.save();
            float rotation = currentX * rotationFactor * depthStrength;
            canvas.rotate(rotation, cw / 2f, ch / 2f);
            canvas.drawBitmap(bitmap, null, new RectF(left, top, left + dw, top + dh), imagePaint);
            canvas.restore();
        }

        private void drawAmbientShade(Canvas canvas) {
            int accent = ThemePalette.accent(prefs);
            int tint = ThemePalette.withAlpha(accent, 18);
            shadePaint.setColor(tint);
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), shadePaint);
        }

        private void drawClock(Canvas canvas) {
            float width = canvas.getWidth();
            float height = canvas.getHeight();
            float depth = prefs.getInt(Prefs.CLOCK_DEPTH, 75) / 100f;
            float parallax = prefs.getInt(Prefs.PARALLAX, 68) / 100f;
            int size = prefs.getInt(Prefs.CLOCK_SIZE, 100);
            int yPercent = prefs.getInt(Prefs.CLOCK_Y, 24);

            float timeSize = Math.max(62f, width * 0.205f * (size / 100f));
            float dateSize = Math.max(16f, timeSize * 0.165f);

            // Plano intermediário entre fundo e primeiro plano.
            float shiftX = currentX * width * 0.050f * depth * parallax;
            float shiftY = currentY * height * 0.026f * depth * parallax;
            float perspective = 1f + currentY * 0.034f * depth;
            float rotation = currentX * 0.50f * depth;

            String font = prefs.getString(Prefs.CLOCK_FONT, "condensed");
            int color = ThemePalette.autoClockColor(prefs);

            clockPaint.setTypeface(ClockStyles.typeface(font));
            clockPaint.setColor(color);
            clockPaint.setTextSize(timeSize * perspective);
            clockPaint.setShadowLayer(12f + 8f * depth, -shiftX * 0.045f, 3f + shiftY * 0.03f, 0x85000000);

            datePaint.setTypeface(ClockStyles.dateTypeface(font));
            datePaint.setColor(color);
            datePaint.setTextSize(dateSize * perspective);
            datePaint.setShadowLayer(7f + 5f * depth, -shiftX * 0.035f, 2f + shiftY * 0.02f, 0x72000000);

            Date now = new Date();
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
            String date = new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
                    .format(now).toUpperCase(Locale.getDefault());

            float centerX = width / 2f + shiftX;
            float baseline = height * (yPercent / 100f) + timeSize * 0.82f + shiftY;

            canvas.save();
            canvas.rotate(rotation, centerX, baseline - timeSize * 0.3f);
            canvas.drawText(time, centerX, baseline, clockPaint);
            if (prefs.getBoolean(Prefs.CLOCK_SHOW_DATE, true)) {
                canvas.drawText(date, centerX, baseline - timeSize * 1.06f, datePaint);
            }
            canvas.restore();
        }

        private void drawEmpty(Canvas canvas) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(ThemePalette.accent(prefs));
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Math.max(28f, canvas.getWidth() * 0.06f));
            canvas.drawText("Abra o app e escolha um wallpaper", canvas.getWidth() / 2f, canvas.getHeight() / 2f, p);
        }

        private float clamp(float v, float min, float max) {
            return Math.max(min, Math.min(max, v));
        }
    }
}
