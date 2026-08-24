package com.chilenoapps.idepth26;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
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
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
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
        UsageLogger.event(this, "editor_open", prefs.getString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN));
        accent = 0xFFFFD400;
        surface = 0xFF111111;
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(this);
        title.setText("Studio");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        titleLp.setMargins(dp(22), dp(16), 0, 0);
        root.addView(title, titleLp);

        FrameLayout previewCard = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable previewBg = new android.graphics.drawable.GradientDrawable();
        previewBg.setColor(0xFF0B0B0B);
        previewBg.setCornerRadius(dp(28));
        previewBg.setStroke(dp(1), 0xFF2D2D2D);
        previewCard.setBackground(previewBg);
        previewCard.setClipToOutline(true);

        preview = new AdjustView();
        previewCard.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams previewLp = new FrameLayout.LayoutParams(dp(238), dp(430), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        previewLp.topMargin = dp(70);
        root.addView(previewCard, previewLp);

        TextView note = new TextView(this);
        note.setText("ⓘ Preview em tempo real");
        note.setTextColor(0xFFE8E8E8);
        note.setTextSize(12);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(14), dp(8), dp(14), dp(8));
        android.graphics.drawable.GradientDrawable noteBg = new android.graphics.drawable.GradientDrawable();
        noteBg.setColor(0xE6161616);
        noteBg.setCornerRadius(dp(20));
        note.setBackground(noteBg);
        FrameLayout.LayoutParams noteLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        noteLp.topMargin = dp(472);
        root.addView(note, noteLp);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(12));
        android.graphics.drawable.GradientDrawable panelBg = new android.graphics.drawable.GradientDrawable();
        panelBg.setColor(0xF5101010);
        panelBg.setCornerRadii(new float[]{dp(26),dp(26),dp(26),dp(26),0,0,0,0});
        panel.setBackground(panelBg);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        String[] labels = {"Básico", "Tipografia", "Efeitos", "Transformar"};
        for (int i = 0; i < labels.length; i++) {
            TextView tab = new TextView(this);
            tab.setText(labels[i]);
            tab.setGravity(Gravity.CENTER);
            tab.setTextSize(12);
            tab.setTextColor(i == 0 ? accent : 0xFFCECECE);
            tab.setPadding(dp(6), dp(8), dp(6), dp(8));
            tabs.addView(tab, weight());
        }
        panel.addView(tabs);

        android.widget.ScrollView editorScroll = new android.widget.ScrollView(this);
        editorScroll.setFillViewport(true);
        LinearLayout editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setPadding(dp(6), dp(6), dp(6), dp(8));

        CheckBox clock = new CheckBox(this);
        clock.setText("Mostrar relógio");
        clock.setTextColor(Color.WHITE);
        clock.setButtonTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{accent, 0xFF777777}));
        clock.setChecked(prefs.getBoolean(Prefs.CLOCK, true));
        clock.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(Prefs.CLOCK, checked).apply();
            preview.invalidate();
            UsageLogger.event(this, "personalize_clock_toggle", String.valueOf(checked));
        });
        editor.addView(clock);

        CheckBox behind = new CheckBox(this);
        behind.setText("Relógio atrás do assunto");
        behind.setTextColor(Color.WHITE);
        behind.setButtonTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{accent, 0xFF777777}));
        behind.setChecked(prefs.getBoolean(Prefs.CLOCK_BEHIND, true));
        behind.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked && !VipManager.isVip(this)) {
                buttonView.setChecked(false);
                UsageLogger.event(this, "vip_gate", "clock_behind_subject");
                startActivity(new Intent(this, VipActivity.class));
                return;
            }
            prefs.edit().putBoolean(Prefs.CLOCK_BEHIND, checked).apply();
            preview.invalidate();
        });
        editor.addView(behind);

        Button font = button("Fonte: " + prefs.getString(Prefs.CLOCK_FONT, "condensed"), false);
        font.setOnClickListener(v -> {
            String current = prefs.getString(Prefs.CLOCK_FONT, "condensed");
            int index = 0;
            for (int i = 0; i < ClockStyles.KEYS.length; i++) {
                if (ClockStyles.KEYS[i].equals(current)) { index = i; break; }
            }
            int nextIndex = (index + 1) % ClockStyles.KEYS.length;
            if (nextIndex >= 3 && !VipManager.isVip(this)) {
                startActivity(new Intent(this, VipActivity.class));
                return;
            }
            String next = ClockStyles.KEYS[nextIndex];
            prefs.edit().putString(Prefs.CLOCK_FONT, next).apply();
            font.setText("Fonte: " + next);
            preview.invalidate();
        });
        editor.addView(font, marginTop(5));

        editor.addView(label("Tamanho do relógio"), marginTop(7));
        SeekBar clockSize = seekBar(90, Math.max(0, Math.min(90, prefs.getInt(Prefs.CLOCK_SIZE, 100) - 60)));
        clockSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                prefs.edit().putInt(Prefs.CLOCK_SIZE, progress + 60).apply();
                preview.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        editor.addView(clockSize);

        editor.addView(label("Profundidade"), marginTop(4));
        SeekBar depth = seekBar(100, prefs.getInt(Prefs.DEPTH, 78));
        depth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                prefs.edit().putInt(Prefs.DEPTH, progress).apply();
                preview.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        editor.addView(depth);

        editorScroll.addView(editor);
        panel.addView(editorScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(200)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        Button cancel = button("Voltar", false);
        cancel.setOnClickListener(v -> finish());
        actions.addView(cancel, weight());

        Button reset = button("Redefinir", false);
        reset.setOnClickListener(v -> preview.resetTransform());
        actions.addView(reset, weight());

        Button save = button("Aplicar", true);
        save.setOnClickListener(v -> {
            preview.saveTransform();
            sendBroadcast(new Intent(DepthWallpaperService.ACTION_REFRESH).setPackage(getPackageName()));
            UsageLogger.event(this, "wallpaper_apply", prefs.getString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN));
            try {
                Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
                intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        new ComponentName(this, DepthWallpaperService.class));
                startActivity(intent);
            } catch (Exception e) {
                try {
                    startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
                } catch (Exception ignored) {
                    Toast.makeText(this, "Abra as configurações de wallpaper do Android.", Toast.LENGTH_LONG).show();
                }
            }
        });
        actions.addView(save, weight());
        panel.addView(actions, marginTop(6));

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(panel, panelLp);

        setContentView(root);
    }

    private TextView label(String value) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(0xFFE8E8E8);
        t.setTextSize(12);
        return t;
    }

    private SeekBar seekBar(int max, int progress) {
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.setProgress(progress);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            s.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
            s.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        return s;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.BLACK : Color.WHITE);
        b.setTextSize(13);
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(primary ? 0xFFFFD400 : 0xFF191919);
        d.setCornerRadius(dp(20));
        if (!primary) d.setStroke(dp(1), 0xFF303030);
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
                    String fgPath = prefs.getString(Prefs.USER_FOREGROUND_PATH, "");
                    if (fgPath != null && !fgPath.isEmpty()) foreground = BitmapFactory.decodeFile(fgPath);
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
