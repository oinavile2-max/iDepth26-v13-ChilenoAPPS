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
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
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
import android.view.MotionEvent;
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
        private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint uiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint uiTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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

        // Editor direto na tela inicial. Funciona nos launchers que repassam toque ao live wallpaper.
        private final RectF clockBounds = new RectF();
        private final RectF toolbarBounds = new RectF();
        private final RectF autoColorRect = new RectF();
        private final RectF minusRect = new RectF();
        private final RectF plusRect = new RectF();
        private final RectF doneRect = new RectF();
        private final RectF[] paletteRects = {new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
        private boolean editMode;
        private boolean pressedClock;
        private boolean draggingClock;
        private float downX, downY;
        private float lastTouchX, lastTouchY;
        private long lastClockTapAt;
        private int dragPreviewClockX;
        private int dragPreviewClockY;
        private int dragStartClockX;
        private int dragStartClockY;
        private float dragStartTouchX;
        private float dragStartTouchY;

        private final Runnable longPressRunnable = () -> {
            if (pressedClock && visible && !editMode) {
                editMode = true;
                draggingClock = true;
                bumpEditTimeout();
                drawFrame();
            }
        };

        private final Runnable closeEditorRunnable = () -> {
            editMode = false;
            draggingClock = false;
            drawFrame();
        };

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
            setTouchEventsEnabled(true);

            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (rotationSensor == null) rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

            clockPaint.setTextAlign(Paint.Align.CENTER);
            datePaint.setTextAlign(Paint.Align.CENTER);
            uiTextPaint.setTextAlign(Paint.Align.CENTER);

            IntentFilter filter = new IntentFilter(ACTION_REFRESH);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(refreshReceiver, filter);

            loadSelectedLayers(true);
        }

        @Override
        public void onDestroy() {
            handler.removeCallbacks(drawRunnable);
            handler.removeCallbacks(longPressRunnable);
            handler.removeCallbacks(closeEditorRunnable);
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
                handler.removeCallbacks(longPressRunnable);
                handler.removeCallbacks(closeEditorRunnable);
                editMode = false;
                pressedClock = false;
                draggingClock = false;
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
            handler.removeCallbacks(longPressRunnable);
            handler.removeCallbacks(closeEditorRunnable);
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (rotationSensor == null || event.sensor.getType() != rotationSensor.getType()) return;
            float[] matrix = new float[9];
            float[] orientation = new float[3];
            SensorManager.getRotationMatrixFromVector(matrix, event.values);
            SensorManager.getOrientation(matrix, orientation);
            targetX = clamp(orientation[2] / 0.47f, -1f, 1f);
            targetY = clamp(orientation[1] / 0.52f, -1f, 1f);
        }

        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        @Override
        public void onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
            float x = event.getX();
            float y = event.getY();

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = lastTouchX = x;
                    downY = lastTouchY = y;

                    dragStartTouchX = x;
                    dragStartTouchY = y;
                    dragStartClockX = prefs.getInt(Prefs.CLOCK_X, 50);
                    dragStartClockY = prefs.getInt(Prefs.CLOCK_Y, 24);
                    dragPreviewClockX = dragStartClockX;
                    dragPreviewClockY = dragStartClockY;

                    if (editMode) {
                        draggingClock = clockBounds.contains(x, y);
                        bumpEditTimeout();
                    } else if (prefs.getBoolean(Prefs.CLOCK, true) && clockBounds.contains(x, y)) {
                        pressedClock = true;
                        handler.removeCallbacks(longPressRunnable);
                        handler.postDelayed(longPressRunnable, 520L);
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (!editMode && pressedClock) {
                        float moveX = x - downX;
                        float moveY = y - downY;
                        if (moveX * moveX + moveY * moveY > dp(14) * dp(14)) {
                            pressedClock = false;
                            handler.removeCallbacks(longPressRunnable);
                        }
                    }

                    if (editMode && draggingClock) {
                        int width = Math.max(1, getSurfaceHolder().getSurfaceFrame().width());
                        int height = Math.max(1, getSurfaceHolder().getSurfaceFrame().height());

                        int xPercent = dragStartClockX +
                                Math.round((x - dragStartTouchX) / width * 100f);
                        int yPercent = dragStartClockY +
                                Math.round((y - dragStartTouchY) / height * 100f);

                        dragPreviewClockX = clampInt(xPercent, 8, 92);
                        dragPreviewClockY = clampInt(yPercent, 8, 64);

                        lastTouchX = x;
                        lastTouchY = y;
                        bumpEditTimeout();
                        drawFrame();
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longPressRunnable);
                    if (editMode) {
                        if (draggingClock) {
                            prefs.edit()
                                    .putInt(Prefs.CLOCK_X, dragPreviewClockX)
                                    .putInt(Prefs.CLOCK_Y, dragPreviewClockY)
                                    .apply();
                        } else {
                            handleEditorTap(x, y);
                        }

                        draggingClock = false;
                        bumpEditTimeout();
                        drawFrame();
                    } else if (pressedClock) {
                        long now = android.os.SystemClock.uptimeMillis();
                        if (now - lastClockTapAt <= 360L) {
                            editMode = true;
                            draggingClock = false;
                            bumpEditTimeout();
                            drawFrame();
                            lastClockTapAt = 0L;
                        } else {
                            lastClockTapAt = now;
                        }
                    }
                    pressedClock = false;
                    break;

                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPressRunnable);
                    pressedClock = false;
                    draggingClock = false;
                    break;
            }
        }

        private void handleEditorTap(float x, float y) {
            if (minusRect.contains(x, y)) {
                int size = Math.max(60, prefs.getInt(Prefs.CLOCK_SIZE, 82) - 5);
                prefs.edit().putInt(Prefs.CLOCK_SIZE, size).apply();
                return;
            }
            if (plusRect.contains(x, y)) {
                int size = Math.min(150, prefs.getInt(Prefs.CLOCK_SIZE, 82) + 5);
                prefs.edit().putInt(Prefs.CLOCK_SIZE, size).apply();
                return;
            }
            if (doneRect.contains(x, y)) {
                editMode = false;
                return;
            }
            if (autoColorRect.contains(x, y)) {
                prefs.edit().putString(Prefs.CLOCK_COLOR_MODE, "auto").apply();
                return;
            }
            int[] palette = ThemePalette.palette(prefs);
            for (int i = 0; i < paletteRects.length; i++) {
                if (paletteRects[i].contains(x, y)) {
                    prefs.edit().putString(Prefs.CLOCK_COLOR_MODE, "custom").putInt(Prefs.CLOCK_COLOR, palette[i]).apply();
                    return;
                }
            }
        }

        private void bumpEditTimeout() {
            handler.removeCallbacks(closeEditorRunnable);
            if (editMode) handler.postDelayed(closeEditorRunnable, 12000L);
        }

        private void loadSelectedLayers(boolean force) {
            String source = prefs.getString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN);
            if (Prefs.SOURCE_USER.equals(source)) loadUserImage(force);
            else if (Prefs.SOURCE_REMOTE.equals(source)) loadRemote(force);
            else if (Prefs.SOURCE_ONLINE_PACK.equals(source)) loadOnlinePack(force);
            else loadBuiltin(force);

            if (foreground != null && !hasUsefulAlpha(foreground)) {
                try { foreground.recycle(); } catch (Exception ignored) {}
                foreground = null;
            }
            if (background != null && prefs.getBoolean(Prefs.THEME_AUTO, true)) {
                ThemePalette.applyFromBitmap(DepthWallpaperService.this, background);
            }
        }

        private void loadBuiltin(boolean force) {
            int index = Math.floorMod(prefs.getInt(Prefs.BUILTIN_INDEX, 0), BuiltInWallpapers.count());
            String fgPath = prefs.getString(Prefs.BUILTIN_FOREGROUND_PATH, "");
            String key = "b:" + index + ":" + fgPath;
            if (!force && key.equals(lastKey) && background != null) return;
            recycleLayers();
            background = BitmapFactory.decodeResource(getResources(), BuiltInWallpapers.BACKGROUNDS[index]);
            if (fgPath != null && !fgPath.isEmpty()) foreground = BitmapFactory.decodeFile(fgPath);
            else if (BuiltInWallpapers.FOREGROUNDS[index] != 0) foreground = BitmapFactory.decodeResource(getResources(), BuiltInWallpapers.FOREGROUNDS[index]);
            lastKey = key;
        }

        private void loadOnlinePack(boolean force) {
            int index = Math.floorMod(prefs.getInt(Prefs.ONLINE_PACK_INDEX, 0), OnlinePackWallpapers.count());
            String fgPath = prefs.getString(Prefs.ONLINE_PACK_FOREGROUND_PATH, "");
            String key = "op:" + index + ":" + fgPath;
            if (!force && key.equals(lastKey) && background != null) return;
            recycleLayers();
            background = BitmapFactory.decodeResource(getResources(), OnlinePackWallpapers.BACKGROUNDS[index]);
            if (fgPath != null && !fgPath.isEmpty()) foreground = BitmapFactory.decodeFile(fgPath);
            else if (OnlinePackWallpapers.FOREGROUNDS[index] != 0) foreground = BitmapFactory.decodeResource(getResources(), OnlinePackWallpapers.FOREGROUNDS[index]);
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

                    RectF rect = computeWallpaperRect(canvas, background);
                    configureImagePaint();
                    drawWallpaperBitmap(canvas, background, rect, 0f, 0f);

                    if (clockEnabled && behind) drawClock(canvas);

                    if (foreground != null) {
                        // Mantém o foreground alinhado ao lockscreen: elimina a "imagem duplicada".
                        // O movimento extra é opcional e começa em zero.
                        float motion = prefs.getInt(Prefs.FOREGROUND_MOTION, 0) / 100f;
                        float dx = currentX * canvas.getWidth() * 0.020f * motion;
                        float dy = currentY * canvas.getHeight() * 0.010f * motion;
                        drawWallpaperBitmap(canvas, foreground, rect, dx, dy);
                    }

                    if (clockEnabled && !behind) drawClock(canvas);
                    imagePaint.setColorFilter(null);

                    if (editMode && clockEnabled) drawHomeEditor(canvas);
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }

            if (visible) handler.postDelayed(drawRunnable, 33L);
        }

        private RectF computeWallpaperRect(Canvas canvas, Bitmap bitmap) {
            float cw = canvas.getWidth();
            float ch = canvas.getHeight();
            float bw = bitmap.getWidth();
            float bh = bitmap.getHeight();
            float base = Math.max(cw / bw, ch / bh);

            float userScale = clamp(prefs.getFloat(Prefs.WALLPAPER_SCALE, 1f), 1f, 2f);
            float savedX = clamp(prefs.getFloat(Prefs.WALLPAPER_POSITION_X, 0f), -1f, 1f);
            float savedY = clamp(prefs.getFloat(Prefs.WALLPAPER_POSITION_Y, 0f), -1f, 1f);

            float panStrength = Math.max(Math.abs(savedX), Math.abs(savedY));
            float panScale = 1f + 0.16f * panStrength;
            float scale = base * userScale * panScale;

            float dw = bw * scale;
            float dh = bh * scale;
            float maxX = Math.max(0f, (dw - cw) / 2f);
            float maxY = Math.max(0f, (dh - ch) / 2f);

            float parallax = prefs.getInt(Prefs.PARALLAX, 68) / 100f;
            float depth = prefs.getInt(Prefs.DEPTH, 78) / 100f;

            float sensorX = currentX * maxX * parallax * depth * 0.55f;
            float sensorY = currentY * maxY * parallax * depth * 0.55f;
            float left = (cw - dw) / 2f + savedX * maxX + sensorX;
            float top = (ch - dh) / 2f + savedY * maxY + sensorY;
            return new RectF(left, top, left + dw, top + dh);
        }

        private void drawWallpaperBitmap(Canvas canvas, Bitmap bitmap, RectF rect, float dx, float dy) {
            float depth = prefs.getInt(Prefs.DEPTH, 78) / 100f;
            float manualRotation = clamp(prefs.getFloat(Prefs.WALLPAPER_ROTATION, 0f), -10f, 10f);
            float sensorRotation = currentX * 0.32f * depth;
            RectF moved = new RectF(rect);
            moved.offset(dx, dy);
            canvas.save();
            canvas.rotate(manualRotation + sensorRotation, canvas.getWidth() / 2f, canvas.getHeight() / 2f);
            canvas.drawBitmap(bitmap, null, moved, imagePaint);
            canvas.restore();
        }

        private void configureImagePaint() {
            float contrast = prefs.getInt(Prefs.WALLPAPER_CONTRAST, 100) / 100f;
            float brightness = (prefs.getInt(Prefs.WALLPAPER_BRIGHTNESS, 100) - 100) * 2.55f;
            float dim = prefs.getInt(Prefs.WALLPAPER_DIM, 0) / 100f;
            float dimScale = 1f - Math.min(0.75f, dim);
            float c = contrast * dimScale;
            float offset = 128f * (1f - contrast) + brightness;
            ColorMatrix matrix = new ColorMatrix(new float[]{
                    c, 0, 0, 0, offset,
                    0, c, 0, 0, offset,
                    0, 0, c, 0, offset,
                    0, 0, 0, 1, 0
            });
            imagePaint.setColorFilter(new ColorMatrixColorFilter(matrix));
            imagePaint.setAlpha(255);
        }

        private void drawClock(Canvas canvas) {
            float width = canvas.getWidth();
            float height = canvas.getHeight();
            float overallDepth = prefs.getInt(Prefs.DEPTH, 78) / 100f;
            float clockDepth = prefs.getInt(Prefs.CLOCK_DEPTH, 78) / 100f;
            float depth = overallDepth * clockDepth;
            float parallax = prefs.getInt(Prefs.PARALLAX, 68) / 100f;

            int size = prefs.getInt(Prefs.CLOCK_SIZE, 82);
            int xPercent = draggingClock ? dragPreviewClockX : prefs.getInt(Prefs.CLOCK_X, 50);
            int yPercent = draggingClock ? dragPreviewClockY : prefs.getInt(Prefs.CLOCK_Y, 24);

            String style = prefs.getString(Prefs.CLOCK_STYLE, "solid");
            if ("depth_outline".equals(style)) style = "neon";
            boolean outline = "outline".equals(style) || "neon".equals(style);
            boolean neon = "neon".equals(style);

            float timeSize = Math.max(54f, width * 0.205f * (size / 100f));
            int dateScale = prefs.getInt(Prefs.CLOCK_DATE_SIZE, 100);
            float dateSize = Math.max(15f, timeSize * 0.165f * (dateScale / 100f));
            int dateGap = prefs.getInt(Prefs.CLOCK_DATE_GAP, 10);

            float shiftX = currentX * width * 0.060f * depth * parallax;
            float shiftY = currentY * height * 0.032f * depth * parallax;
            float perspective = 1f + currentY * 0.040f * depth;
            float rotation = currentX * 0.55f * depth;

            String font = prefs.getString(Prefs.CLOCK_FONT, "condensed");
            int color = neon
                    ? ThemePalette.neonClockColor(prefs, background, xPercent, yPercent, size)
                    : ThemePalette.clockColor(prefs, background, xPercent, yPercent, size);

            int alpha = Math.round(255f * prefs.getInt(Prefs.CLOCK_ALPHA, 100) / 100f);
            float shadow = prefs.getInt(Prefs.CLOCK_SHADOW, 55) / 100f;

            clockPaint.setTextAlign(Paint.Align.CENTER);
            clockPaint.setTypeface(ClockStyles.typeface(font));
            clockPaint.setColor(color);
            clockPaint.setTextSize(timeSize * perspective);

            datePaint.setTextAlign(Paint.Align.CENTER);
            datePaint.setTypeface(ClockStyles.dateTypeface(font));
            datePaint.setColor(color);
            datePaint.setAlpha(alpha);
            datePaint.setTextSize(dateSize * perspective);

            Date now = new Date();
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
            String date = new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
                    .format(now).toUpperCase(Locale.getDefault());

            float centerX = width * (xPercent / 100f) + shiftX;
            float baseline = height * (yPercent / 100f) + timeSize * 0.80f + shiftY;
            float dateBaseline = baseline - timeSize * 0.92f - dp(dateGap);

            float timeWidth = clockPaint.measureText(time);
            float dateWidth = datePaint.measureText(date);
            float panelWidth = Math.max(timeWidth, dateWidth) + dp(28);
            float panelTop = dateBaseline - dateSize * 1.10f - dp(8);
            float panelBottom = baseline + timeSize * 0.20f + dp(10);
            RectF glassRect = new RectF(
                    centerX - panelWidth / 2f,
                    panelTop,
                    centerX + panelWidth / 2f,
                    panelBottom
            );

            canvas.save();
            canvas.rotate(rotation, centerX, baseline - timeSize * 0.3f);

            if (prefs.getBoolean(Prefs.CLOCK_GLASS_ENABLED, false)) {
                int intensity = prefs.getInt(Prefs.CLOCK_GLASS_INTENSITY, 35);
                int fillAlpha = 10 + Math.round(intensity * 0.55f);
                int strokeAlpha = 30 + Math.round(intensity * 0.70f);

                glassPaint.setStyle(Paint.Style.FILL);
                glassPaint.setColor(Color.argb(Math.min(95, fillAlpha), 255, 255, 255));
                glassPaint.setShadowLayer(dp(4) + dp(10) * intensity / 100f, 0f, dp(3), 0x66000000);
                canvas.drawRoundRect(glassRect, dp(22), dp(22), glassPaint);
                glassPaint.clearShadowLayer();

                glassPaint.setStyle(Paint.Style.STROKE);
                glassPaint.setStrokeWidth(dp(1));
                glassPaint.setColor(ThemePalette.withAlpha(color, Math.min(150, strokeAlpha)));
                canvas.drawRoundRect(glassRect, dp(22), dp(22), glassPaint);
                glassPaint.setStyle(Paint.Style.FILL);
            }

            if (outline) {
                int stroke = prefs.getInt(Prefs.CLOCK_STROKE, 3);
                int fill = prefs.getInt(Prefs.CLOCK_FILL, 18);
                float glow = neon ? prefs.getInt(Prefs.CLOCK_GLOW, 45) / 100f : 0f;
                int neonSize = prefs.getInt(Prefs.CLOCK_NEON_SIZE, 4);

                float strokePx = Math.max(1.7f, timeSize * (0.0048f + stroke * 0.00135f));
                float glowRadius = neon
                        ? dp(2) + timeSize * (0.014f + neonSize * 0.0065f) * glow
                        : 0f;

                clockPaint.setStyle(Paint.Style.STROKE);
                clockPaint.setStrokeWidth(strokePx);
                clockPaint.setAlpha(alpha);

                if (neon && glow > 0f) {
                    clockPaint.setShadowLayer(
                            glowRadius,
                            -shiftX * 0.015f,
                            shiftY * 0.010f,
                            ThemePalette.withAlpha(color, Math.min(230, 75 + Math.round(150f * glow))));
                } else {
                    clockPaint.clearShadowLayer();
                }

                canvas.drawText(time, centerX, baseline, clockPaint);

                if (fill > 0) {
                    clockPaint.clearShadowLayer();
                    clockPaint.setStyle(Paint.Style.FILL);
                    clockPaint.setAlpha(Math.round(alpha * (fill / 100f)));
                    canvas.drawText(time, centerX, baseline, clockPaint);
                }
            } else {
                clockPaint.setStyle(Paint.Style.FILL);
                clockPaint.setAlpha(alpha);
                clockPaint.setShadowLayer(
                        2f + 10f * shadow + 5f * depth,
                        -shiftX * 0.030f,
                        2f + 2f * shadow + shiftY * 0.015f,
                        0x95000000);
                canvas.drawText(time, centerX, baseline, clockPaint);
            }

            clockPaint.clearShadowLayer();
            clockPaint.setStyle(Paint.Style.FILL);
            clockPaint.setAlpha(alpha);

            if (prefs.getBoolean(Prefs.CLOCK_SHOW_DATE, true)) {
                datePaint.setShadowLayer(
                        2f + 7f * shadow,
                        -shiftX * 0.020f,
                        1f + 2f * shadow,
                        0x80000000);
                canvas.drawText(date, centerX, dateBaseline, datePaint);
                datePaint.clearShadowLayer();
            }

            canvas.restore();

            clockBounds.set(
                    glassRect.left,
                    Math.min(glassRect.top, dateBaseline - dateSize * 1.2f),
                    glassRect.right,
                    glassRect.bottom
            );
        }

        private void drawHomeEditor(Canvas canvas) {
            float w = canvas.getWidth();
            float h = canvas.getHeight();
            float margin = dp(16);
            float barH = dp(126);
            float left = margin;
            float right = w - margin;
            float top = h - barH - dp(26);
            float bottom = h - dp(26);
            toolbarBounds.set(left, top, right, bottom);

            uiPaint.setColor(0xE6141414);
            canvas.drawRoundRect(toolbarBounds, dp(24), dp(24), uiPaint);
            uiPaint.setStyle(Paint.Style.STROKE);
            uiPaint.setStrokeWidth(dp(1));
            uiPaint.setColor(0xFF4A4A4A);
            canvas.drawRoundRect(toolbarBounds, dp(24), dp(24), uiPaint);
            uiPaint.setStyle(Paint.Style.FILL);

            uiTextPaint.setColor(Color.WHITE);
            uiTextPaint.setTextSize(dp(12));
            uiTextPaint.setFakeBoldText(true);
            canvas.drawText("Arraste o relógio para mover", w / 2f, top + dp(22), uiTextPaint);

            float cy = top + dp(53);
            float r = dp(14);
            float startX = left + dp(32);
            autoColorRect.set(startX - r, cy - r, startX + r, cy + r);
            uiPaint.setColor(0xFF2A2A2A);
            canvas.drawCircle(startX, cy, r, uiPaint);
            uiTextPaint.setTextSize(dp(8));
            uiTextPaint.setFakeBoldText(true);
            canvas.drawText("A", startX, cy + dp(3), uiTextPaint);

            int[] colors = ThemePalette.palette(prefs);
            float gap = Math.min(dp(44), (right - left - dp(150)) / 5f);
            for (int i = 0; i < 5; i++) {
                float cx = startX + dp(44) + gap * i;
                paletteRects[i].set(cx - r, cy - r, cx + r, cy + r);
                uiPaint.setColor(colors[i]);
                canvas.drawCircle(cx, cy, r, uiPaint);
                uiPaint.setStyle(Paint.Style.STROKE);
                uiPaint.setStrokeWidth(dp(1));
                uiPaint.setColor(0xAAFFFFFF);
                canvas.drawCircle(cx, cy, r, uiPaint);
                uiPaint.setStyle(Paint.Style.FILL);
            }

            float buttonY = top + dp(91);
            float bw = dp(54), bh = dp(34);
            minusRect.set(left + dp(20), buttonY - bh / 2f, left + dp(20) + bw, buttonY + bh / 2f);
            plusRect.set(minusRect.right + dp(8), buttonY - bh / 2f, minusRect.right + dp(8) + bw, buttonY + bh / 2f);
            doneRect.set(right - dp(82), buttonY - bh / 2f, right - dp(20), buttonY + bh / 2f);

            drawEditorButton(canvas, minusRect, "−");
            drawEditorButton(canvas, plusRect, "+");
            drawEditorButton(canvas, doneRect, "OK");

            int size = prefs.getInt(Prefs.CLOCK_SIZE, 82);
            uiTextPaint.setColor(0xFFDADADA);
            uiTextPaint.setTextSize(dp(11));
            uiTextPaint.setFakeBoldText(false);
            canvas.drawText(size + "%", (plusRect.right + doneRect.left) / 2f, buttonY + dp(4), uiTextPaint);

            // contorno discreto no relógio selecionado
            uiPaint.setStyle(Paint.Style.STROKE);
            uiPaint.setStrokeWidth(dp(1));
            uiPaint.setColor(0xCCFFD400);
            canvas.drawRoundRect(clockBounds, dp(10), dp(10), uiPaint);
            uiPaint.setStyle(Paint.Style.FILL);
        }

        private void drawEditorButton(Canvas canvas, RectF rect, String text) {
            uiPaint.setColor(0xFF252525);
            canvas.drawRoundRect(rect, dp(14), dp(14), uiPaint);
            uiTextPaint.setColor(Color.WHITE);
            uiTextPaint.setTextSize("OK".equals(text) ? dp(10) : dp(18));
            uiTextPaint.setFakeBoldText(true);
            canvas.drawText(text, rect.centerX(), rect.centerY() + dp(5), uiTextPaint);
        }

        private boolean hasUsefulAlpha(Bitmap bitmap) {
            if (bitmap == null || !bitmap.hasAlpha()) return false;
            int w = bitmap.getWidth(), h = bitmap.getHeight();
            int sx = Math.max(1, w / 36), sy = Math.max(1, h / 48);
            int total = 0, transparent = 0;
            for (int y = 0; y < h; y += sy) {
                for (int x = 0; x < w; x += sx) {
                    total++;
                    if (Color.alpha(bitmap.getPixel(x, y)) < 245) transparent++;
                }
            }
            return total > 0 && transparent / (float) total >= 0.03f;
        }

        private void drawEmpty(Canvas canvas) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFFFD400);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Math.max(28f, canvas.getWidth() * 0.06f));
            canvas.drawText("Abra o app e escolha um wallpaper", canvas.getWidth() / 2f, canvas.getHeight() / 2f, p);
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }

        private int clampInt(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private float clamp(float v, float min, float max) {
            return Math.max(min, Math.min(max, v));
        }
    }
}
