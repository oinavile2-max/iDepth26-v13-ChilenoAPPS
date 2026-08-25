package com.chilenoapps.idepth26;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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
import android.widget.ScrollView;
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
    private LinearLayout editor;
    private TextView[] tabs;
    private int activeTab = 0;
    private final int accent = 0xFFFFD400;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        ensureEditorDefaults();
        UsageLogger.event(this, "editor_open", prefs.getString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN));
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(this);
        title.setText("Studio");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        titleLp.setMargins(dp(22), dp(16), 0, 0);
        root.addView(title, titleLp);

        FrameLayout previewCard = new FrameLayout(this);
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setColor(0xFF0B0B0B);
        previewBg.setCornerRadius(dp(28));
        previewBg.setStroke(dp(1), 0xFF2D2D2D);
        previewCard.setBackground(previewBg);
        previewCard.setClipToOutline(true);

        preview = new AdjustView();
        previewCard.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams previewLp = new FrameLayout.LayoutParams(
                dp(238), dp(430), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        previewLp.topMargin = dp(70);
        root.addView(previewCard, previewLp);

        TextView note = new TextView(this);
        note.setText("Arraste o relógio para mover • arraste a imagem para enquadrar");
        note.setTextColor(0xFFE8E8E8);
        note.setTextSize(10);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(12), dp(7), dp(12), dp(7));
        GradientDrawable noteBg = new GradientDrawable();
        noteBg.setColor(0xE6161616);
        noteBg.setCornerRadius(dp(20));
        note.setBackground(noteBg);
        FrameLayout.LayoutParams noteLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        noteLp.topMargin = dp(472);
        root.addView(note, noteLp);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(9), dp(12), dp(10));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(0xF5101010);
        panelBg.setCornerRadii(new float[]{dp(26), dp(26), dp(26), dp(26), 0, 0, 0, 0});
        panel.setBackground(panelBg);

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setGravity(Gravity.CENTER);
        String[] labels = {"Básico", "Relógio", "Efeitos", "Transformar"};
        tabs = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView tab = new TextView(this);
            tab.setText(labels[i]);
            tab.setGravity(Gravity.CENTER);
            tab.setTextSize(10.5f);
            tab.setSingleLine(true);
            tab.setMaxLines(1);
            tab.setTextScaleX("Transformar".equals(labels[i]) ? 0.90f : 1f);
            tab.setPadding(dp(2), dp(9), dp(2), dp(9));
            tab.setOnClickListener(v -> showTab(index));
            tabs[i] = tab;
            tabRow.addView(tab, weight());
        }
        panel.addView(tabRow);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setPadding(dp(6), dp(4), dp(6), dp(6));
        scroll.addView(editor);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(245)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        Button cancel = button("Voltar", false);
        cancel.setOnClickListener(v -> finish());
        actions.addView(cancel, weight());

        Button reset = button("Redefinir", false);
        reset.setOnClickListener(v -> {
            preview.resetAll();
            showTab(activeTab);
        });
        actions.addView(reset, weight());

        Button save = button("Aplicar", true);
        save.setOnClickListener(v -> {
            preview.saveTransform();
            showLockScreenClockWarning();
        });
        actions.addView(save, weight());
        panel.addView(actions, marginTop(5));

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(panel, panelLp);

        setContentView(root);
        showTab(0);
    }

    private void showLockScreenClockWarning() {
        if (!prefs.getBoolean(Prefs.CLOCK, true)) {
            applyWallpaperNow();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Evite dois relógios na tela de bloqueio")
                .setMessage("O Android pode mostrar o relógio do sistema junto com o relógio do iDepth 26. Para uma aparência limpa, recomendamos ocultar ou reduzir o relógio da tela de bloqueio nas configurações do aparelho.")
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("Abrir ajustes do sistema", (d, w) -> openSystemLockSettings())
                .setPositiveButton("Continuar assim", (d, w) -> applyWallpaperNow())
                .show();
    }

    private void openSystemLockSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(this, "Abra os ajustes da tela de bloqueio do aparelho.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void applyWallpaperNow() {
        refreshLiveWallpaper();
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
    }

    private void ensureEditorDefaults() {
        SharedPreferences.Editor e = prefs.edit();
        if (!prefs.contains(Prefs.CLOCK_BEHIND)) e.putBoolean(Prefs.CLOCK_BEHIND, true);
        if (!prefs.contains(Prefs.CLOCK_X)) e.putInt(Prefs.CLOCK_X, 50);
        if (!prefs.contains(Prefs.CLOCK_Y)) e.putInt(Prefs.CLOCK_Y, 24);
        if (!prefs.contains(Prefs.CLOCK_ALPHA)) e.putInt(Prefs.CLOCK_ALPHA, 100);
        if (!prefs.contains(Prefs.CLOCK_SHADOW)) e.putInt(Prefs.CLOCK_SHADOW, 55);
        if (!prefs.contains(Prefs.CLOCK_SIZE)) e.putInt(Prefs.CLOCK_SIZE, 82);
        if (!prefs.contains(Prefs.CLOCK_STYLE)) e.putString(Prefs.CLOCK_STYLE, "solid");
        if (!prefs.contains(Prefs.CLOCK_FORMAT)) e.putString(Prefs.CLOCK_FORMAT, "full");
        if (!prefs.contains(Prefs.CLOCK_STROKE)) e.putInt(Prefs.CLOCK_STROKE, 3);
        if (!prefs.contains(Prefs.CLOCK_FILL)) e.putInt(Prefs.CLOCK_FILL, 18);
        if (!prefs.contains(Prefs.CLOCK_GLOW)) e.putInt(Prefs.CLOCK_GLOW, 45);
        if (!prefs.contains(Prefs.CLOCK_NEON_SIZE)) e.putInt(Prefs.CLOCK_NEON_SIZE, 4);
        if (!prefs.contains(Prefs.CLOCK_GLASS_ENABLED)) e.putBoolean(Prefs.CLOCK_GLASS_ENABLED, false);
        if (!prefs.contains(Prefs.CLOCK_GLASS_INTENSITY)) e.putInt(Prefs.CLOCK_GLASS_INTENSITY, 35);
        if (!prefs.contains(Prefs.CLOCK_DATE_SIZE)) e.putInt(Prefs.CLOCK_DATE_SIZE, 100);
        if (!prefs.contains(Prefs.CLOCK_DATE_GAP)) e.putInt(Prefs.CLOCK_DATE_GAP, 10);
        if (!prefs.contains(Prefs.WALLPAPER_SCALE)) e.putFloat(Prefs.WALLPAPER_SCALE, 1f);
        if (!prefs.contains(Prefs.WALLPAPER_ROTATION)) e.putFloat(Prefs.WALLPAPER_ROTATION, 0f);
        if (!prefs.contains(Prefs.WALLPAPER_BRIGHTNESS)) e.putInt(Prefs.WALLPAPER_BRIGHTNESS, 100);
        if (!prefs.contains(Prefs.WALLPAPER_CONTRAST)) e.putInt(Prefs.WALLPAPER_CONTRAST, 100);
        if (!prefs.contains(Prefs.WALLPAPER_DIM)) e.putInt(Prefs.WALLPAPER_DIM, 0);
        if (!prefs.contains(Prefs.FOREGROUND_MOTION)) e.putInt(Prefs.FOREGROUND_MOTION, 0);
        e.apply();
    }

    private void showTab(int index) {
        activeTab = index;
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setTextColor(i == index ? accent : 0xFFCECECE);
            tabs[i].setTypeface(i == index ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        editor.removeAllViews();
        if (index == 0) buildBasic();
        else if (index == 1) buildClock();
        else if (index == 2) buildEffects();
        else buildTransform();
    }

    private void buildBasic() {
        CheckBox clock = checkbox("Mostrar relógio", prefs.getBoolean(Prefs.CLOCK, true));
        clock.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(Prefs.CLOCK, checked).apply();
            preview.invalidate();
        });
        editor.addView(clock);

        CheckBox behind = checkbox("Relógio com profundidade", prefs.getBoolean(Prefs.CLOCK_BEHIND, true));
        behind.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(Prefs.CLOCK_BEHIND, checked).apply();
            preview.invalidate();
        });
        editor.addView(behind, marginTop(2));

        addIntSlider("Profundidade do wallpaper e do relógio", 0, 100,
                prefs.getInt(Prefs.DEPTH, 78), value -> {
                    prefs.edit().putInt(Prefs.DEPTH, value).putInt(Prefs.CLOCK_DEPTH, value).apply();
                    preview.invalidate();
                });

        TextView hint = label("O relógio com profundidade já vem ativado. Desative apenas se quiser o relógio sempre na frente.");
        hint.setTextColor(0xFFAAAAAA);
        editor.addView(hint, marginTop(8));
    }

    private void buildClock() {
        TextView format = label("Formato do horário: 07:28");
        format.setTextColor(0xFFFFD400);
        editor.addView(format);

        String currentStyle = normalizeClockStyle(prefs.getString(Prefs.CLOCK_STYLE, "solid"));
        Button style = button("Estilo: " + clockStyleLabel(currentStyle), false);
        style.setOnClickListener(v -> {
            String now = normalizeClockStyle(prefs.getString(Prefs.CLOCK_STYLE, "solid"));
            String next = "solid".equals(now) ? "outline" : ("outline".equals(now) ? "neon" : "solid");
            prefs.edit().putString(Prefs.CLOCK_STYLE, next).putString(Prefs.CLOCK_FORMAT, "full").apply();
            style.setText("Estilo: " + clockStyleLabel(next));
            preview.invalidate();
        });
        editor.addView(style, marginTop(5));

        String selectedFont = prefs.getString(Prefs.CLOCK_FONT, "condensed");
        Button font = button("Fonte: " + ClockStyles.labelFor(selectedFont), false);
        font.setOnClickListener(v -> {
            String current = prefs.getString(Prefs.CLOCK_FONT, "condensed");
            int currentIndex = 0;
            for (int i = 0; i < ClockStyles.KEYS.length; i++) {
                if (ClockStyles.KEYS[i].equals(current)) { currentIndex = i; break; }
            }
            int nextIndex = (currentIndex + 1) % ClockStyles.KEYS.length;
            if (nextIndex >= 3 && !VipManager.isVip(this)) {
                startActivity(new Intent(this, VipActivity.class));
                return;
            }
            String next = ClockStyles.KEYS[nextIndex];
            prefs.edit().putString(Prefs.CLOCK_FONT, next).apply();
            font.setText("Fonte: " + ClockStyles.labelFor(next));
            preview.invalidate();
        });
        editor.addView(font, marginTop(5));

        addClockSizeStepper();

        addIntSlider("Posição horizontal do relógio", 8, 92,
                prefs.getInt(Prefs.CLOCK_X, 50), value -> {
                    prefs.edit().putInt(Prefs.CLOCK_X, value).apply();
                    preview.invalidate();
                });

        addIntSlider("Posição vertical do relógio", 8, 64,
                prefs.getInt(Prefs.CLOCK_Y, 24), value -> {
                    prefs.edit().putInt(Prefs.CLOCK_Y, value).apply();
                    preview.invalidate();
                });

        CheckBox showDate = checkbox("Mostrar data", prefs.getBoolean(Prefs.CLOCK_SHOW_DATE, true));
        showDate.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(Prefs.CLOCK_SHOW_DATE, checked).apply();
            preview.invalidate();
        });
        editor.addView(showDate, marginTop(4));

        addIntSlider("Tamanho da data", 60, 130,
                prefs.getInt(Prefs.CLOCK_DATE_SIZE, 100), value -> {
                    prefs.edit().putInt(Prefs.CLOCK_DATE_SIZE, value).apply();
                    preview.invalidate();
                });

        addIntSlider("Espaçamento da data", 0, 40,
                prefs.getInt(Prefs.CLOCK_DATE_GAP, 10), value -> {
                    prefs.edit().putInt(Prefs.CLOCK_DATE_GAP, value).apply();
                    preview.invalidate();
                });

        TextView paletteLabel = label("Cores extraídas do wallpaper atual");
        editor.addView(paletteLabel, marginTop(8));
        editor.addView(buildPalettePicker(), marginTop(5));

        addIntSlider("Espessura do contorno", 1, 10,
                prefs.getInt(Prefs.CLOCK_STROKE, 3), value -> {
                    prefs.edit().putInt(Prefs.CLOCK_STROKE, value).apply();
                    preview.invalidate();
                });

        addIntSlider("Preenchimento do relógio", 0, 100,
                prefs.getInt(Prefs.CLOCK_FILL, 18), value -> {
                    prefs.edit().putInt(Prefs.CLOCK_FILL, value).apply();
                    preview.invalidate();
                });

        addIntSlider("Espessura do neon", 1, 12,
                prefs.getInt(Prefs.CLOCK_NEON_SIZE, 4), value -> {
                    prefs.edit().putInt(Prefs.CLOCK_NEON_SIZE, value).apply();
                    preview.invalidate();
                });

        addIntSlider("Intensidade do neon", 0, 100,
                prefs.getInt(Prefs.CLOCK_GLOW, 45), value -> {
                    prefs.edit().putInt(Prefs.CLOCK_GLOW, value).apply();
                    preview.invalidate();
                });

        CheckBox glass = checkbox("Efeito vidro no relógio", prefs.getBoolean(Prefs.CLOCK_GLASS_ENABLED, false));
        glass.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(Prefs.CLOCK_GLASS_ENABLED, checked).apply();
            preview.invalidate();
        });
        editor.addView(glass, marginTop(4));

        addIntSlider("Intensidade do vidro", 0, 100,
                prefs.getInt(Prefs.CLOCK_GLASS_INTENSITY, 35), value -> {
                    prefs.edit().putInt(Prefs.CLOCK_GLASS_INTENSITY, value).apply();
                    preview.invalidate();
                });

        addIntSlider("Opacidade do relógio", 25, 100,
                prefs.getInt(Prefs.CLOCK_ALPHA, 100), value -> {
                    prefs.edit().putInt(Prefs.CLOCK_ALPHA, value).apply();
                    preview.invalidate();
                });

        addIntSlider("Sombra", 0, 100,
                prefs.getInt(Prefs.CLOCK_SHADOW, 55), value -> {
                    prefs.edit().putInt(Prefs.CLOCK_SHADOW, value).apply();
                    preview.invalidate();
                });
    }

    private String normalizeClockStyle(String style) {
        if ("depth_outline".equals(style)) return "neon";
        if (!"solid".equals(style) && !"outline".equals(style) && !"neon".equals(style)) return "solid";
        return style;
    }

    private String clockStyleLabel(String style) {
        if ("outline".equals(style)) return "Contorno";
        if ("neon".equals(style)) return "Neon";
        return "Sólido";
    }

    private void buildEffects() {
        addIntSlider("Profundidade", 0, 100, prefs.getInt(Prefs.DEPTH, 78), value -> {
            prefs.edit().putInt(Prefs.DEPTH, value).putInt(Prefs.CLOCK_DEPTH, value).apply();
            preview.invalidate();
        });
        addIntSlider("Parallax", 0, 100, prefs.getInt(Prefs.PARALLAX, 68), value -> {
            prefs.edit().putInt(Prefs.PARALLAX, value).apply();
            preview.invalidate();
        });
        addIntSlider("Brilho", 60, 140, prefs.getInt(Prefs.WALLPAPER_BRIGHTNESS, 100), value -> {
            prefs.edit().putInt(Prefs.WALLPAPER_BRIGHTNESS, value).apply();
            preview.invalidate();
        });
        addIntSlider("Contraste", 60, 140, prefs.getInt(Prefs.WALLPAPER_CONTRAST, 100), value -> {
            prefs.edit().putInt(Prefs.WALLPAPER_CONTRAST, value).apply();
            preview.invalidate();
        });
        addIntSlider("Escurecimento", 0, 45, prefs.getInt(Prefs.WALLPAPER_DIM, 0), value -> {
            prefs.edit().putInt(Prefs.WALLPAPER_DIM, value).apply();
            preview.invalidate();
        });
        addIntSlider("Movimento do primeiro plano", 0, 20, prefs.getInt(Prefs.FOREGROUND_MOTION, 0), value -> {
            prefs.edit().putInt(Prefs.FOREGROUND_MOTION, value).apply();
            preview.invalidate();
        });
    }

    private void buildTransform() {
        addWallpaperZoomStepper();

        addFloatPositionSlider("Posição horizontal do wallpaper", true);
        addFloatPositionSlider("Posição vertical do wallpaper", false);

        TextView rotationLabel = label("Rotação do wallpaper");
        editor.addView(rotationLabel, marginTop(7));
        SeekBar rotation = seekBar(20, Math.round(preview.getWallpaperRotation() + 10f));
        rotation.setOnSeekBarChangeListener(simpleSeek(progress -> {
            preview.setWallpaperRotation(progress - 10f);
            preview.invalidate();
        }));
        editor.addView(rotation);

        TextView hint = label("Pinça: zoom • duplo toque: redefinir enquadramento");
        hint.setTextColor(0xFF9D9D9D);
        editor.addView(hint, marginTop(5));
    }

    private void addClockSizeStepper() {
        TextView label = label("Tamanho do relógio");
        editor.addView(label, marginTop(7));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        Button minus = smallButton("−");
        Button plus = smallButton("+");
        TextView value = valuePill(prefs.getInt(Prefs.CLOCK_SIZE, 82) + "%");

        minus.setOnClickListener(v -> {
            int next = Math.max(60, prefs.getInt(Prefs.CLOCK_SIZE, 82) - 5);
            prefs.edit().putInt(Prefs.CLOCK_SIZE, next).apply();
            value.setText(next + "%");
            preview.invalidate();
        });
        plus.setOnClickListener(v -> {
            int next = Math.min(150, prefs.getInt(Prefs.CLOCK_SIZE, 82) + 5);
            prefs.edit().putInt(Prefs.CLOCK_SIZE, next).apply();
            value.setText(next + "%");
            preview.invalidate();
        });

        row.addView(minus, new LinearLayout.LayoutParams(dp(50), dp(44)));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        vp.leftMargin = dp(8); vp.rightMargin = dp(8);
        row.addView(value, vp);
        row.addView(plus, new LinearLayout.LayoutParams(dp(50), dp(44)));
        editor.addView(row, marginTop(4));
    }

    private void addWallpaperZoomStepper() {
        TextView label = label("Zoom do wallpaper");
        editor.addView(label, marginTop(3));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Button minus = smallButton("−");
        Button plus = smallButton("+");
        SeekBar zoom = seekBar(100, Math.round((preview.getUserScale() - 1f) * 100f));
        TextView value = valuePill(Math.round(preview.getUserScale() * 100f) + "%");

        zoom.setOnSeekBarChangeListener(simpleSeek(progress -> {
            float scale = 1f + progress / 100f;
            preview.setUserScale(scale);
            value.setText(Math.round(scale * 100f) + "%");
        }));
        minus.setOnClickListener(v -> {
            int p = Math.max(0, zoom.getProgress() - 5);
            zoom.setProgress(p);
        });
        plus.setOnClickListener(v -> {
            int p = Math.min(100, zoom.getProgress() + 5);
            zoom.setProgress(p);
        });

        row.addView(minus, new LinearLayout.LayoutParams(dp(48), dp(44)));
        LinearLayout.LayoutParams zp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        zp.leftMargin = dp(5); zp.rightMargin = dp(5);
        row.addView(zoom, zp);
        row.addView(plus, new LinearLayout.LayoutParams(dp(48), dp(44)));
        editor.addView(row, marginTop(3));
        editor.addView(value, marginTop(2));
    }

    private void addFloatPositionSlider(String title, boolean horizontal) {
        editor.addView(label(title), marginTop(7));
        float current = horizontal ? preview.getPosX() : preview.getPosY();
        SeekBar bar = seekBar(200, Math.round((current + 1f) * 100f));
        bar.setOnSeekBarChangeListener(simpleSeek(progress -> {
            float value = progress / 100f - 1f;
            if (horizontal) preview.setPosX(value); else preview.setPosY(value);
        }));
        editor.addView(bar);
    }

    private LinearLayout buildPalettePicker() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView auto = new TextView(this);
        auto.setText("AUTO");
        auto.setTextSize(9);
        auto.setTextColor(Color.WHITE);
        auto.setGravity(Gravity.CENTER);
        auto.setBackground(rounded(0xFF242424, dp(13), 0xFF4A4A4A));
        auto.setOnClickListener(v -> {
            prefs.edit().putString(Prefs.CLOCK_COLOR_MODE, "auto").apply();
            preview.invalidate();
        });
        row.addView(auto, new LinearLayout.LayoutParams(dp(52), dp(42)));

        int[] colors = ThemePalette.palette(prefs);
        for (int color : colors) {
            View swatch = new View(this);
            swatch.setBackground(rounded(color, dp(18), 0xFF606060));
            swatch.setOnClickListener(v -> {
                prefs.edit().putString(Prefs.CLOCK_COLOR_MODE, "custom").putInt(Prefs.CLOCK_COLOR, color).apply();
                preview.invalidate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
            lp.leftMargin = dp(7);
            row.addView(swatch, lp);
        }
        return row;
    }

    private void addIntSlider(String title, int min, int max, int current, IntValueListener listener) {
        TextView label = label(title + "  " + current);
        editor.addView(label, marginTop(7));
        SeekBar bar = seekBar(max - min, clampInt(current, min, max) - min);
        bar.setOnSeekBarChangeListener(simpleSeek(progress -> {
            int value = progress + min;
            label.setText(title + "  " + value);
            listener.onValue(value);
        }));
        editor.addView(bar);
    }

    private CheckBox checkbox(String text, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setTextColor(Color.WHITE);
        c.setTextSize(14);
        c.setButtonTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{accent, 0xFF777777}));
        c.setChecked(checked);
        return c;
    }

    private TextView label(String value) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(0xFFE8E8E8);
        t.setTextSize(12);
        return t;
    }

    private TextView valuePill(String value) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(12);
        t.setGravity(Gravity.CENTER);
        t.setBackground(rounded(0xFF191919, dp(16), 0xFF303030));
        return t;
    }

    private SeekBar seekBar(int max, int progress) {
        SeekBar s = new SeekBar(this);
        s.setMax(Math.max(1, max));
        s.setProgress(clampInt(progress, 0, Math.max(1, max)));
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            s.setProgressTintList(ColorStateList.valueOf(accent));
            s.setThumbTintList(ColorStateList.valueOf(accent));
        }
        return s;
    }

    private SeekBar.OnSeekBarChangeListener simpleSeek(IntValueListener listener) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) listener.onValue(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.BLACK : Color.WHITE);
        b.setTextSize(13);
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        b.setBackground(rounded(primary ? accent : 0xFF191919, dp(20), primary ? 0 : 0xFF303030));
        return b;
    }

    private Button smallButton(String text) {
        Button b = button(text, false);
        b.setTextSize(20);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeColor != 0) d.setStroke(dp(1), strokeColor);
        return d;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(3);
        p.rightMargin = dp(3);
        return p;
    }

    private LinearLayout.LayoutParams marginTop(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(top);
        return p;
    }

    private void refreshLiveWallpaper() {
        sendBroadcast(new Intent(DepthWallpaperService.ACTION_REFRESH).setPackage(getPackageName()));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private interface IntValueListener { void onValue(int value); }

    private class AdjustView extends View {
        private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ScaleGestureDetector scaleDetector;
        private final GestureDetector gestureDetector;
        private final RectF clockBounds = new RectF();

        private Bitmap background;
        private Bitmap foreground;
        private float posX;
        private float posY;
        private float userScale;
        private float rotation;
        private float lastX;
        private float lastY;
        private boolean draggingWallpaper;
        private boolean draggingClock;
        private int dragClockX;
        private int dragClockY;
        private int dragStartClockX;
        private int dragStartClockY;
        private float clockDragStartX;
        private float clockDragStartY;

        AdjustView() {
            super(WallpaperAdjustActivity.this);
            setBackgroundColor(Color.BLACK);
            posX = clamp(prefs.getFloat(Prefs.WALLPAPER_POSITION_X, 0f), -1f, 1f);
            posY = clamp(prefs.getFloat(Prefs.WALLPAPER_POSITION_Y, 0f), -1f, 1f);
            userScale = clamp(prefs.getFloat(Prefs.WALLPAPER_SCALE, 1f), 1f, 2f);
            rotation = clamp(prefs.getFloat(Prefs.WALLPAPER_ROTATION, 0f), -10f, 10f);
            dragClockX = prefs.getInt(Prefs.CLOCK_X, 50);
            dragClockY = prefs.getInt(Prefs.CLOCK_Y, 24);
            loadLayers();

            scaleDetector = new ScaleGestureDetector(WallpaperAdjustActivity.this,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override public boolean onScale(ScaleGestureDetector detector) {
                            if (!draggingClock) {
                                userScale = clamp(userScale * detector.getScaleFactor(), 1f, 2f);
                                invalidate();
                            }
                            return true;
                        }
                    });

            gestureDetector = new GestureDetector(WallpaperAdjustActivity.this,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override public boolean onDown(MotionEvent e) { return true; }
                        @Override public boolean onDoubleTap(MotionEvent e) {
                            resetTransformOnly();
                            showTab(activeTab);
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
            } else if (Prefs.SOURCE_ONLINE_PACK.equals(source)) {
                int index = Math.floorMod(prefs.getInt(Prefs.ONLINE_PACK_INDEX, 0), OnlinePackWallpapers.count());
                background = BitmapFactory.decodeResource(getResources(), OnlinePackWallpapers.BACKGROUNDS[index]);
                String fgPath = prefs.getString(Prefs.ONLINE_PACK_FOREGROUND_PATH, "");
                if (fgPath != null && !fgPath.isEmpty()) foreground = BitmapFactory.decodeFile(fgPath);
                else if (OnlinePackWallpapers.FOREGROUNDS[index] != 0) foreground = BitmapFactory.decodeResource(getResources(), OnlinePackWallpapers.FOREGROUNDS[index]);
            }

            if (background == null) {
                int index = Math.floorMod(prefs.getInt(Prefs.BUILTIN_INDEX, 0), BuiltInWallpapers.count());
                background = BitmapFactory.decodeResource(getResources(), BuiltInWallpapers.BACKGROUNDS[index]);
                String fgPath = prefs.getString(Prefs.BUILTIN_FOREGROUND_PATH, "");
                if (fgPath != null && !fgPath.isEmpty()) foreground = BitmapFactory.decodeFile(fgPath);
                else if (BuiltInWallpapers.FOREGROUNDS[index] != 0) foreground = BitmapFactory.decodeResource(getResources(), BuiltInWallpapers.FOREGROUNDS[index]);
            }

            if (foreground != null && !hasUsefulAlpha(foreground)) {
                try { foreground.recycle(); } catch (Exception ignored) {}
                foreground = null;
            }
            if (background != null && prefs.getBoolean(Prefs.THEME_AUTO, true)) {
                ThemePalette.applyFromBitmap(WallpaperAdjustActivity.this, background);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.BLACK);
            if (background == null) return;

            RectF rect = layerRect(canvas, background);
            configureImagePaint();
            drawBitmapWithTransform(canvas, background, rect, 0f, 0f);

            boolean showClock = prefs.getBoolean(Prefs.CLOCK, true);
            boolean behind = prefs.getBoolean(Prefs.CLOCK_BEHIND, true);
            if (showClock && behind) drawClock(canvas);

            if (foreground != null) {
                // A camada frontal usa o MESMO enquadramento do fundo para não criar imagem duplicada.
                // Apenas uma separação opcional e pequena é permitida em "Efeitos".
                float motion = prefs.getInt(Prefs.FOREGROUND_MOTION, 0) / 100f;
                float dx = getWidth() * 0.008f * motion;
                float dy = -getHeight() * 0.004f * motion;
                drawBitmapWithTransform(canvas, foreground, rect, dx, dy);
            }

            if (showClock && !behind) drawClock(canvas);
            imagePaint.setColorFilter(null);
        }

        private RectF layerRect(Canvas canvas, Bitmap bitmap) {
            float cw = canvas.getWidth();
            float ch = canvas.getHeight();
            float bw = bitmap.getWidth();
            float bh = bitmap.getHeight();
            float base = Math.max(cw / bw, ch / bh);

            float panStrength = Math.max(Math.abs(posX), Math.abs(posY));
            float panScale = 1f + 0.16f * panStrength;
            float scale = base * userScale * panScale;

            float dw = bw * scale;
            float dh = bh * scale;
            float maxX = Math.max(0f, (dw - cw) / 2f);
            float maxY = Math.max(0f, (dh - ch) / 2f);
            float left = (cw - dw) / 2f + posX * maxX;
            float top = (ch - dh) / 2f + posY * maxY;
            return new RectF(left, top, left + dw, top + dh);
        }

        private void drawBitmapWithTransform(Canvas canvas, Bitmap bitmap, RectF rect, float dx, float dy) {
            canvas.save();
            canvas.rotate(rotation, getWidth() / 2f, getHeight() / 2f);
            RectF moved = new RectF(rect);
            moved.offset(dx, dy);
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
            int size = prefs.getInt(Prefs.CLOCK_SIZE, 82);
            int xPercent = draggingClock ? dragClockX : prefs.getInt(Prefs.CLOCK_X, 50);
            int yPercent = draggingClock ? dragClockY : prefs.getInt(Prefs.CLOCK_Y, 24);
            float overallDepth = prefs.getInt(Prefs.DEPTH, 78) / 100f;
            float clockDepth = prefs.getInt(Prefs.CLOCK_DEPTH, 78) / 100f;
            float effectiveDepth = overallDepth * clockDepth;

            String style = normalizeClockStyle(prefs.getString(Prefs.CLOCK_STYLE, "solid"));
            boolean outline = "outline".equals(style) || "neon".equals(style);
            boolean neon = "neon".equals(style);

            float timeSize = Math.max(46f, width * 0.205f * (size / 100f));
            timeSize *= 1f + effectiveDepth * 0.018f;

            int dateScale = prefs.getInt(Prefs.CLOCK_DATE_SIZE, 100);
            float dateSize = Math.max(13f, timeSize * 0.165f * (dateScale / 100f));
            int dateGap = prefs.getInt(Prefs.CLOCK_DATE_GAP, 10);

            int color = neon
                    ? ThemePalette.neonClockColor(prefs, background, xPercent, yPercent, size)
                    : ThemePalette.clockColor(prefs, background, xPercent, yPercent, size);

            String font = prefs.getString(Prefs.CLOCK_FONT, "condensed");
            int alpha = Math.round(255f * prefs.getInt(Prefs.CLOCK_ALPHA, 100) / 100f);
            float shadow = prefs.getInt(Prefs.CLOCK_SHADOW, 55) / 100f;

            timePaint.setTextAlign(Paint.Align.CENTER);
            timePaint.setTypeface(ClockStyles.typeface(font));
            timePaint.setColor(color);
            timePaint.setTextSize(timeSize);

            datePaint.setTextAlign(Paint.Align.CENTER);
            datePaint.setTypeface(ClockStyles.dateTypeface(font));
            datePaint.setColor(color);
            datePaint.setAlpha(alpha);
            datePaint.setTextSize(dateSize);

            Date now = new Date();
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
            String date = new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
                    .format(now).toUpperCase(Locale.getDefault());

            float centerX = width * (xPercent / 100f);
            float baseline = height * (yPercent / 100f) + timeSize * 0.80f;
            float dateBaseline = baseline - timeSize * 0.92f - dp(dateGap);

            float timeWidth = timePaint.measureText(time);
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

                float strokePx = Math.max(1.5f, timeSize * (0.0048f + stroke * 0.00135f));
                float glowRadius = neon
                        ? dp(2) + timeSize * (0.014f + neonSize * 0.0065f) * glow
                        : 0f;

                timePaint.setStyle(Paint.Style.STROKE);
                timePaint.setStrokeWidth(strokePx);
                timePaint.setAlpha(alpha);

                if (neon && glow > 0f) {
                    timePaint.setShadowLayer(
                            glowRadius,
                            0f,
                            0f,
                            ThemePalette.withAlpha(color, Math.min(230, 75 + Math.round(150f * glow))));
                } else {
                    timePaint.clearShadowLayer();
                }

                canvas.drawText(time, centerX, baseline, timePaint);

                if (fill > 0) {
                    timePaint.clearShadowLayer();
                    timePaint.setStyle(Paint.Style.FILL);
                    timePaint.setAlpha(Math.round(alpha * (fill / 100f)));
                    canvas.drawText(time, centerX, baseline, timePaint);
                }
            } else {
                timePaint.setStyle(Paint.Style.FILL);
                timePaint.setAlpha(alpha);
                timePaint.setShadowLayer(
                        2f + 10f * shadow + 5f * effectiveDepth,
                        0f,
                        2f + 2f * shadow,
                        0x95000000);
                canvas.drawText(time, centerX, baseline, timePaint);
            }

            timePaint.clearShadowLayer();
            timePaint.setStyle(Paint.Style.FILL);
            timePaint.setAlpha(alpha);

            if (prefs.getBoolean(Prefs.CLOCK_SHOW_DATE, true)) {
                datePaint.setShadowLayer(2f + 7f * shadow, 0f, 1f + 2f * shadow, 0x80000000);
                canvas.drawText(date, centerX, dateBaseline, datePaint);
                datePaint.clearShadowLayer();
            }

            clockBounds.set(
                    glassRect.left,
                    Math.min(glassRect.top, dateBaseline - dateSize * 1.2f),
                    glassRect.right,
                    glassRect.bottom
            );
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

                        if (prefs.getBoolean(Prefs.CLOCK, true) && clockBounds.contains(lastX, lastY)) {
                            draggingClock = true;
                            draggingWallpaper = false;
                            clockDragStartX = lastX;
                            clockDragStartY = lastY;
                            dragStartClockX = prefs.getInt(Prefs.CLOCK_X, 50);
                            dragStartClockY = prefs.getInt(Prefs.CLOCK_Y, 24);
                            dragClockX = dragStartClockX;
                            dragClockY = dragStartClockY;
                        } else {
                            draggingClock = false;
                            draggingWallpaper = true;
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getX() - lastX;
                        float dy = event.getY() - lastY;
                        lastX = event.getX();
                        lastY = event.getY();

                        if (draggingClock) {
                            float totalDx = event.getX() - clockDragStartX;
                            float totalDy = event.getY() - clockDragStartY;
                            int x = dragStartClockX + Math.round(totalDx / Math.max(1f, getWidth()) * 100f);
                            int y = dragStartClockY + Math.round(totalDy / Math.max(1f, getHeight()) * 100f);
                            dragClockX = clampInt(x, 8, 92);
                            dragClockY = clampInt(y, 8, 64);
                            invalidate();
                        } else if (draggingWallpaper && background != null) {
                            float[] overflow = currentOverflow();
                            if (overflow[0] > 1f) posX = clamp(posX + dx / overflow[0], -1f, 1f);
                            if (overflow[1] > 1f) posY = clamp(posY + dy / overflow[1], -1f, 1f);
                            invalidate();
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (draggingClock) {
                            prefs.edit()
                                    .putInt(Prefs.CLOCK_X, dragClockX)
                                    .putInt(Prefs.CLOCK_Y, dragClockY)
                                    .apply();
                        }
                        draggingClock = false;
                        draggingWallpaper = false;
                        invalidate();
                        return true;
                }
            }
            return true;
        }

        private float[] currentOverflow() {
            if (background == null || getWidth() == 0 || getHeight() == 0) return new float[]{1f, 1f};
            float base = Math.max(getWidth() / (float) background.getWidth(), getHeight() / (float) background.getHeight());
            float dw = background.getWidth() * base * userScale;
            float dh = background.getHeight() * base * userScale;
            return new float[]{Math.max(1f, (dw - getWidth()) / 2f), Math.max(1f, (dh - getHeight()) / 2f)};
        }

        void resetTransformOnly() {
            posX = 0f;
            posY = 0f;
            userScale = 1f;
            rotation = 0f;
            invalidate();
        }

        void resetAll() {
            resetTransformOnly();
            prefs.edit()
                    .putInt(Prefs.CLOCK_X, 50)
                    .putInt(Prefs.CLOCK_Y, 24)
                    .putInt(Prefs.CLOCK_SIZE, 82)
                    .putInt(Prefs.CLOCK_ALPHA, 100)
                    .putInt(Prefs.CLOCK_SHADOW, 70)
                    .putInt(Prefs.DEPTH, 78)
                    .putInt(Prefs.CLOCK_DEPTH, 78)
                    .putInt(Prefs.PARALLAX, 68)
                    .putInt(Prefs.WALLPAPER_BRIGHTNESS, 100)
                    .putInt(Prefs.WALLPAPER_CONTRAST, 100)
                    .putInt(Prefs.WALLPAPER_DIM, 0)
                    .putInt(Prefs.FOREGROUND_MOTION, 0)
                    .putString(Prefs.CLOCK_COLOR_MODE, "auto")
                    .putString(Prefs.CLOCK_STYLE, "solid")
                    .putString(Prefs.CLOCK_FORMAT, "full")
                    .putInt(Prefs.CLOCK_STROKE, 5)
                    .putInt(Prefs.CLOCK_FILL, 8)
                    .putInt(Prefs.CLOCK_GLOW, 45)
                    .putInt(Prefs.CLOCK_NEON_SIZE, 4)
                    .putBoolean(Prefs.CLOCK_GLASS_ENABLED, false)
                    .putInt(Prefs.CLOCK_GLASS_INTENSITY, 35)
                    .putInt(Prefs.CLOCK_DATE_SIZE, 100)
                    .putInt(Prefs.CLOCK_DATE_GAP, 10)
                    .apply();
            saveTransform();
            invalidate();
        }

        void saveTransform() {
            prefs.edit()
                    .putFloat(Prefs.WALLPAPER_POSITION_X, posX)
                    .putFloat(Prefs.WALLPAPER_POSITION_Y, posY)
                    .putFloat(Prefs.WALLPAPER_SCALE, userScale)
                    .putFloat(Prefs.WALLPAPER_ROTATION, rotation)
                    .apply();
        }

        float getPosX() { return posX; }
        float getPosY() { return posY; }
        float getUserScale() { return userScale; }
        float getWallpaperRotation() { return rotation; }
        void setPosX(float v) { posX = clamp(v, -1f, 1f); invalidate(); }
        void setPosY(float v) { posY = clamp(v, -1f, 1f); invalidate(); }
        void setUserScale(float v) { userScale = clamp(v, 1f, 2f); invalidate(); }
        void setWallpaperRotation(float v) { rotation = clamp(v, -10f, 10f); invalidate(); }

        private boolean hasUsefulAlpha(Bitmap bitmap) {
            if (bitmap == null || !bitmap.hasAlpha()) return false;
            int w = bitmap.getWidth(), h = bitmap.getHeight();
            int sx = Math.max(1, w / 36), sy = Math.max(1, h / 48);
            int total = 0, transparent = 0;
            for (int y = 0; y < h; y += sy) {
                for (int x = 0; x < w; x += sx) {
                    int a = Color.alpha(bitmap.getPixel(x, y));
                    total++;
                    if (a < 245) transparent++;
                }
            }
            return total > 0 && transparent / (float) total >= 0.03f;
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
