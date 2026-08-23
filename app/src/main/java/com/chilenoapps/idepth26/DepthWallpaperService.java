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
import android.graphics.Typeface;
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
        private final Runnable drawRunnable = this::drawFrame;
        private final SharedPreferences prefs = Prefs.get(DepthWallpaperService.this);
        private SensorManager sensorManager;
        private Sensor rotationSensor;
        private Bitmap background;
        private Bitmap foreground;
        private boolean visible;
        private float targetX, targetY, currentX, currentY;
        private String lastKey = "";

        private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
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

            clockPaint.setColor(Color.WHITE);
            clockPaint.setTextAlign(Paint.Align.CENTER);
            datePaint.setColor(0xEFFFFFFF);
            datePaint.setTextAlign(Paint.Align.CENTER);

            IntentFilter filter = new IntentFilter(ACTION_REFRESH);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(refreshReceiver, filter);
            }
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
                if (rotationSensor != null) {
                    sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
                }
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
            targetX = clamp(orientation[2] / 0.55f, -1f, 1f);
            targetY = clamp(orientation[1] / 0.55f, -1f, 1f);
        }

        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        private void loadSelectedLayers(boolean force) {
            String source = prefs.getString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN);
            if (Prefs.SOURCE_USER.equals(source)) {
                loadUserImage(force);
            } else if (Prefs.SOURCE_REMOTE.equals(source)) {
                loadRemote(force);
            } else {
                loadBuiltin(force);
            }
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
            String key = "u:" + uri;
            if (!force && key.equals(lastKey) && background != null) return;
            try (InputStream in = getContentResolver().openInputStream(Uri.parse(uri))) {
                Bitmap decoded = BitmapFactory.decodeStream(in);
                if (decoded != null) {
                    recycleLayers();
                    background = decoded;
                    foreground = null;
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

        private void drawFrame() {
            handler.removeCallbacks(drawRunnable);
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;
                canvas.drawColor(Color.BLACK);

                if (background != null) {
                    drawLayer(canvas, background, 0.52f, 0.00f);
                    boolean clockEnabled = prefs.getBoolean(Prefs.CLOCK, true);
                    boolean behind = prefs.getBoolean(Prefs.CLOCK_BEHIND, true);
                    if (clockEnabled && behind) drawClock(canvas);
                    if (foreground != null) drawLayer(canvas, foreground, 1.18f, 0.018f);
                    if (clockEnabled && !behind) drawClock(canvas);
                } else {
                    drawEmpty(canvas);
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }

            currentX += (targetX - currentX) * 0.075f;
            currentY += (targetY - currentY) * 0.075f;
            if (visible) handler.postDelayed(drawRunnable, 33L);
        }

        private void drawLayer(Canvas canvas, Bitmap bitmap, float motionFactor, float extraScale) {
            float cw = canvas.getWidth();
            float ch = canvas.getHeight();
            float bw = bitmap.getWidth();
            float bh = bitmap.getHeight();
            float base = Math.max(cw / bw, ch / bh);
            float zoom = 1f + (prefs.getInt(Prefs.ZOOM, 38) / 100f) * 0.24f + extraScale;
            float userScale = Math.max(1f, Math.min(2.2f, prefs.getFloat(Prefs.WALLPAPER_SCALE, 1f)));
            float scale = base * zoom * userScale;
            float dw = bw * scale;
            float dh = bh * scale;
            float strength = prefs.getInt(Prefs.DEPTH, 70) / 100f;
            float maxX = Math.max(0f, (dw - cw) / 2f);
            float maxY = Math.max(0f, (dh - ch) / 2f);
            float savedX = Math.max(-1f, Math.min(1f, prefs.getFloat(Prefs.WALLPAPER_POSITION_X, 0f)));
            float savedY = Math.max(-1f, Math.min(1f, prefs.getFloat(Prefs.WALLPAPER_POSITION_Y, 0f)));
            float offsetX = savedX * maxX + currentX * maxX * strength * motionFactor;
            float offsetY = savedY * maxY + currentY * maxY * strength * motionFactor;
            float left = (cw - dw) / 2f + offsetX;
            float top = (ch - dh) / 2f + offsetY;
            canvas.drawBitmap(bitmap, null, new RectF(left, top, left + dw, top + dh), imagePaint);
        }

        private void drawClock(Canvas canvas) {
            float width = canvas.getWidth();
            float height = canvas.getHeight();
            float timeSize = Math.max(60f, width * 0.19f);
            float dateSize = Math.max(18f, width * 0.043f);
            float depth = prefs.getInt(Prefs.CLOCK_DEPTH, 70) / 100f;

            // O relógio ocupa um plano intermediário: move mais que o fundo e menos que o primeiro plano.
            float shiftX = currentX * width * 0.042f * depth;
            float shiftY = currentY * height * 0.022f * depth;
            float perspective = 1f + currentY * 0.028f * depth;

            clockPaint.setTextSize(timeSize * perspective);
            clockPaint.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
            clockPaint.setShadowLayer(12f + 8f * depth, -shiftX * 0.06f, 3f + shiftY * 0.03f, 0x72000000);
            datePaint.setTextSize(dateSize * perspective);
            datePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            datePaint.setShadowLayer(8f + 5f * depth, -shiftX * 0.04f, 2f + shiftY * 0.02f, 0x62000000);

            Date now = new Date();
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
            String date = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now);
            float baseline = height * 0.235f + shiftY;
            float centerX = width / 2f + shiftX;
            canvas.drawText(time, centerX, baseline, clockPaint);
            canvas.drawText(date, centerX, baseline + dateSize * 1.7f, datePaint);
        }

        private void drawEmpty(Canvas canvas) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFF9C8BFF);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Math.max(28f, canvas.getWidth() * 0.06f));
            canvas.drawText("Abra o app e escolha um wallpaper", canvas.getWidth()/2f, canvas.getHeight()/2f, p);
        }

        private float clamp(float v, float min, float max) {
            return Math.max(min, Math.min(max, v));
        }
    }
}
