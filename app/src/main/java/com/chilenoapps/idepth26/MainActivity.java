package com.chilenoapps.idepth26;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

public class MainActivity extends Activity {
    private static final int PICK_IMAGES = 1001;

    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private SharedPreferences prefs;
    private FrameLayout content;
    private List<RemoteWallpaper> remote = new ArrayList<>();
    private String page = "home";
    private String categoryPage = null;

    private int accent;
    private int secondary;
    private int bg;
    private int surface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        ensureDefaults();
        remote = RemoteCatalog.loadCached(this);
        readTheme();
        applyWindowTheme();
        setContentView(buildShell());
        showHome();
        refreshCatalog(false);
        io.execute(() -> {
            ThemePalette.refreshFromCurrentSource(this);
            runOnUiThread(() -> {
                readTheme();
                applyWindowTheme();
                rebuildShell();
            });
        });
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void ensureDefaults() {
        SharedPreferences.Editor e = prefs.edit();
        if (!prefs.contains(Prefs.DEPTH)) e.putInt(Prefs.DEPTH, 78);
        if (!prefs.contains(Prefs.PARALLAX)) e.putInt(Prefs.PARALLAX, 68);
        if (!prefs.contains(Prefs.ZOOM)) e.putInt(Prefs.ZOOM, 40);
        if (!prefs.contains(Prefs.CLOCK)) e.putBoolean(Prefs.CLOCK, true);
        if (!prefs.contains(Prefs.CLOCK_BEHIND)) e.putBoolean(Prefs.CLOCK_BEHIND, true);
        if (!prefs.contains(Prefs.CLOCK_DEPTH)) e.putInt(Prefs.CLOCK_DEPTH, 75);
        if (!prefs.contains(Prefs.CLOCK_FONT)) e.putString(Prefs.CLOCK_FONT, "condensed");
        if (!prefs.contains(Prefs.CLOCK_SIZE)) e.putInt(Prefs.CLOCK_SIZE, 100);
        if (!prefs.contains(Prefs.CLOCK_Y)) e.putInt(Prefs.CLOCK_Y, 24);
        if (!prefs.contains(Prefs.CLOCK_COLOR_MODE)) e.putString(Prefs.CLOCK_COLOR_MODE, "auto");
        if (!prefs.contains(Prefs.CLOCK_SHOW_DATE)) e.putBoolean(Prefs.CLOCK_SHOW_DATE, true);
        if (!prefs.contains(Prefs.THEME_AUTO)) e.putBoolean(Prefs.THEME_AUTO, true);
        e.apply();
    }

    private void readTheme() {
        accent = ThemePalette.accent(prefs);
        secondary = ThemePalette.secondary(prefs);
        bg = ThemePalette.background(prefs);
        surface = ThemePalette.surface(prefs);
    }

    private void applyWindowTheme() {
        Window w = getWindow();
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(ThemePalette.mix(Color.BLACK, accent, 0.08f));
    }

    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(6), dp(7), dp(6), dp(8));
        nav.setBackground(rounded(ThemePalette.mix(Color.BLACK, accent, 0.10f), 0, 0));
        nav.addView(navItem("⌂\nInício", "home"), weight());
        nav.addView(navItem("▦\nCategorias", "categories"), weight());
        nav.addView(navItem("▣\nFotos", "photos"), weight());
        nav.addView(navItem("♡\nFavoritos", "favorites"), weight());
        nav.addView(navItem("⚙\nAjustes", "settings"), weight());
        root.addView(nav);
        return root;
    }

    private TextView navItem(String label, String target) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(11);
        t.setTextColor(page.equals(target) ? accent : 0xFFB8BEC9);
        t.setPadding(dp(2), dp(7), dp(2), dp(7));
        if (page.equals(target)) t.setBackground(rounded(ThemePalette.withAlpha(accent, 34), dp(14), 0));
        t.setOnClickListener(v -> {
            if ("home".equals(target)) showHome();
            else if ("categories".equals(target)) showCategories();
            else if ("photos".equals(target)) showPhotos();
            else if ("favorites".equals(target)) showFavorites();
            else showSettings();
            refreshNavOnly();
        });
        return t;
    }

    private void refreshNavOnly() {
        rebuildShell();
    }

    private void rebuildShell() {
        setContentView(buildShell());
        rerender();
    }

    private void showHome() {
        page = "home";
        categoryPage = null;
        LinearLayout root = pageRoot();

        TextView brand = text("iDepth 26", 34, true);
        brand.setLetterSpacing(0.02f);
        root.addView(brand);
        TextView sub = muted("Depth Wallpapers • tema dinâmico • profundidade real em camadas");
        root.addView(sub, marginTop(3));

        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(rounded(surface, dp(28), 1));
        hero.setClipToOutline(true);
        HeroPreview preview = new HeroPreview();
        hero.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(16), dp(12), dp(16), dp(14));
        overlay.setBackground(rounded(0xAA05070C, dp(20), 0));
        TextView current = text(currentSourceText(), 15, true);
        overlay.addView(current);
        TextView mode = muted("Paleta adaptada • " + prefs.getInt(Prefs.DEPTH, 78) + "% profundidade • " +
                prefs.getInt(Prefs.PARALLAX, 68) + "% parallax");
        overlay.addView(mode, marginTop(3));
        FrameLayout.LayoutParams op = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        op.setMargins(dp(10), dp(10), dp(10), dp(10));
        hero.addView(overlay, op);
        root.addView(hero, marginTop(16));

        HorizontalScrollView quick = new HorizontalScrollView(this);
        quick.setHorizontalScrollBarEnabled(false);
        LinearLayout qrow = new LinearLayout(this);
        qrow.setOrientation(LinearLayout.HORIZONTAL);
        qrow.addView(actionChip("◈ Profundidade", () -> showSettings()));
        qrow.addView(actionChip("Aa Fontes", () -> showSettings()));
        qrow.addView(actionChip("◉ Paleta", () -> showSettings()));
        qrow.addView(actionChip("＋ Minhas fotos", () -> showPhotos()));
        quick.addView(qrow);
        root.addView(quick, marginTop(12));

        addSectionTitle(root, "Novos online", "Atualizados sem reinstalar o APK");
        if (remote.isEmpty()) {
            root.addView(muted("Conectando ao catálogo remoto..."), marginTop(8));
        } else {
            List<RemoteWallpaper> news = new ArrayList<>();
            for (RemoteWallpaper w : remote) if (w.isNew) news.add(w);
            if (news.isEmpty()) news.addAll(remote.subList(0, Math.min(12, remote.size())));
            root.addView(buildRemoteGallery(news), marginTop(10));
        }

        Button refresh = secondaryButton("↻ Atualizar catálogo agora");
        refresh.setOnClickListener(v -> refreshCatalog(true));
        root.addView(refresh, marginTop(10));

        addSectionTitle(root, "Depth offline", "20 wallpapers com camadas locais");
        root.addView(buildBuiltinGallery(), marginTop(10));

        Button apply = primaryButton("Aplicar como Live Wallpaper");
        apply.setOnClickListener(v -> openWallpaperPreview());
        root.addView(apply, marginTop(20));

        setPage(root);
    }

    private void showCategories() {
        page = "categories";
        categoryPage = null;
        LinearLayout root = pageRoot();
        addHeader(root, "Categorias", "Coleções online e offline organizadas separadamente.");

        addCategoryCard(root, "Depth offline", BuiltInWallpapers.count(), null);
        Map<String, List<RemoteWallpaper>> grouped = groupByCategory();
        for (Map.Entry<String, List<RemoteWallpaper>> e : grouped.entrySet()) {
            RemoteWallpaper cover = e.getValue().isEmpty() ? null : e.getValue().get(0);
            addCategoryCard(root, e.getKey(), e.getValue().size(), cover);
        }
        if (grouped.isEmpty()) root.addView(muted("Nenhuma categoria online carregada."), marginTop(12));
        setPage(root);
    }

    private void showCategory(String category) {
        page = "category";
        categoryPage = category;
        LinearLayout root = pageRoot();
        Button back = secondaryButton("← Categorias");
        back.setOnClickListener(v -> showCategories());
        root.addView(back);
        addHeader(root, category, "Escolha um wallpaper e personalize antes de aplicar.");
        if ("Depth offline".equals(category)) {
            for (int i = 0; i < BuiltInWallpapers.count(); i++) root.addView(buildBuiltinListCard(i), marginTop(10));
        } else {
            for (RemoteWallpaper w : remote) if (category.equals(w.category)) root.addView(buildRemoteListCard(w), marginTop(10));
        }
        setPage(root);
    }

    private void showPhotos() {
        page = "photos";
        categoryPage = null;
        LinearLayout root = pageRoot();
        addHeader(root, "Minhas fotos", "Sua galeria fica separada dos wallpapers online.");

        LinearLayout importCard = new LinearLayout(this);
        importCard.setOrientation(LinearLayout.VERTICAL);
        importCard.setGravity(Gravity.CENTER);
        importCard.setPadding(dp(18), dp(22), dp(18), dp(22));
        importCard.setBackground(rounded(ThemePalette.withAlpha(accent, 22), dp(22), 1));
        TextView plus = text("＋", 38, false);
        plus.setTextColor(accent);
        plus.setGravity(Gravity.CENTER);
        importCard.addView(plus);
        TextView lbl = text("Importar fotos do celular", 16, true);
        lbl.setGravity(Gravity.CENTER);
        importCard.addView(lbl, marginTop(4));
        importCard.addView(muted("Selecione várias imagens de uma vez"), marginTop(3));
        importCard.setOnClickListener(v -> pickImages());
        root.addView(importCard, marginTop(14));

        List<String> uris = selectedUris();
        addSectionTitle(root, "Sua coleção", uris.size() + " foto(s) importada(s)");
        if (uris.isEmpty()) {
            root.addView(muted("Nenhuma foto importada ainda."), marginTop(10));
        } else {
            buildPhotoGrid(root, uris);
        }
        setPage(root);
    }

    private void showFavorites() {
        page = "favorites";
        categoryPage = null;
        LinearLayout root = pageRoot();
        addHeader(root, "Favoritos", "Wallpapers online e offline que você salvou.");
        Set<String> favs = favorites();
        boolean any = false;

        for (int i = 0; i < BuiltInWallpapers.count(); i++) {
            if (favs.contains("builtin:" + i)) {
                root.addView(buildBuiltinListCard(i), marginTop(10));
                any = true;
            }
        }
        for (RemoteWallpaper w : remote) {
            if (favs.contains("remote:" + w.id)) {
                root.addView(buildRemoteListCard(w), marginTop(10));
                any = true;
            }
        }
        if (!any) root.addView(muted("Nenhum favorito ainda."), marginTop(12));
        setPage(root);
    }

    private void showSettings() {
        page = "settings";
        categoryPage = null;
        LinearLayout root = pageRoot();
        addHeader(root, "Personalizar", "Relógio, fontes, cores, profundidade e comportamento do tema.");

        addSectionTitle(root, "Tema dinâmico", "A interface acompanha a paleta do wallpaper atual");
        CheckBox autoPalette = checkbox("Adaptar paleta ao wallpaper atual", prefs.getBoolean(Prefs.THEME_AUTO, true));
        autoPalette.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(Prefs.THEME_AUTO, checked).apply();
            if (checked) {
                io.execute(() -> {
                    ThemePalette.refreshFromCurrentSource(this);
                    runOnUiThread(() -> { readTheme(); applyWindowTheme(); rebuildShell(); });
                });
            }
        });
        root.addView(autoPalette, marginTop(8));
        root.addView(buildPaletteStrip(), marginTop(10));
        Button syncPalette = secondaryButton("Extrair paleta novamente");
        syncPalette.setOnClickListener(v -> io.execute(() -> {
            ThemePalette.refreshFromCurrentSource(this);
            runOnUiThread(() -> { readTheme(); applyWindowTheme(); rebuildShell(); });
        }));
        root.addView(syncPalette, marginTop(8));

        addSectionTitle(root, "Relógio", "Estilo inspirado em lock screens com controle total");
        CheckBox clock = checkbox("Mostrar relógio", prefs.getBoolean(Prefs.CLOCK, true));
        clock.setOnCheckedChangeListener((b, checked) -> saveAndRefresh(Prefs.CLOCK, checked));
        root.addView(clock, marginTop(8));
        CheckBox behind = checkbox("Relógio entre fundo e primeiro plano", prefs.getBoolean(Prefs.CLOCK_BEHIND, true));
        behind.setOnCheckedChangeListener((b, checked) -> saveAndRefresh(Prefs.CLOCK_BEHIND, checked));
        root.addView(behind, marginTop(4));
        CheckBox showDate = checkbox("Mostrar data", prefs.getBoolean(Prefs.CLOCK_SHOW_DATE, true));
        showDate.setOnCheckedChangeListener((b, checked) -> saveAndRefresh(Prefs.CLOCK_SHOW_DATE, checked));
        root.addView(showDate, marginTop(4));

        root.addView(text("Fontes", 14, true), marginTop(12));
        root.addView(buildFontPicker(), marginTop(8));

        root.addView(text("Cor do relógio", 14, true), marginTop(12));
        root.addView(buildClockColorPicker(), marginTop(8));

        root.addView(text("Tamanho", 14, true), marginTop(12));
        root.addView(seek(prefs.getInt(Prefs.CLOCK_SIZE, 100), 60, 150,
                value -> { prefs.edit().putInt(Prefs.CLOCK_SIZE, value).apply(); notifyWallpaperRefresh(); }));

        root.addView(text("Posição vertical", 14, true), marginTop(8));
        root.addView(seek(prefs.getInt(Prefs.CLOCK_Y, 24), 12, 52,
                value -> { prefs.edit().putInt(Prefs.CLOCK_Y, value).apply(); notifyWallpaperRefresh(); }));

        addSectionTitle(root, "Profundidade 3D", "Movimento com giroscópio e planos independentes");
        root.addView(labeledSeek("Intensidade de profundidade", Prefs.DEPTH, 78), marginTop(8));
        root.addView(labeledSeek("Parallax / movimento", Prefs.PARALLAX, 68), marginTop(8));
        root.addView(labeledSeek("Profundidade do relógio", Prefs.CLOCK_DEPTH, 75), marginTop(8));
        root.addView(labeledSeek("Zoom de proteção", Prefs.ZOOM, 40), marginTop(8));

        addSectionTitle(root, "Ajuste e aplicação", "Posicione, amplie e aplique o Live Wallpaper");
        Button adjust = secondaryButton("Ajustar enquadramento");
        adjust.setOnClickListener(v -> startActivity(new Intent(this, WallpaperAdjustActivity.class)));
        root.addView(adjust, marginTop(8));
        Button apply = primaryButton("Pré-visualizar e aplicar");
        apply.setOnClickListener(v -> openWallpaperPreview());
        root.addView(apply, marginTop(8));

        addSectionTitle(root, "Troca automática", "Alterna wallpapers offline ou suas fotos");
        CheckBox auto = checkbox("Ativar troca automática", prefs.getBoolean(Prefs.AUTO, false));
        auto.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(Prefs.AUTO, checked).apply();
            scheduleAutoChange(checked);
        });
        root.addView(auto, marginTop(8));

        setPage(root);
    }

    private View buildPaletteStrip() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int[] colors = {accent, secondary, surface, ThemePalette.mix(accent, Color.WHITE, 0.45f)};
        for (int color : colors) {
            View v = new View(this);
            v.setBackground(rounded(color, dp(14), 0));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(48), dp(48));
            p.rightMargin = dp(10);
            row.addView(v, p);
        }
        return row;
    }

    private View buildFontPicker() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String selected = prefs.getString(Prefs.CLOCK_FONT, "condensed");
        for (int i = 0; i < ClockStyles.KEYS.length; i++) {
            final String key = ClockStyles.KEYS[i];
            TextView chip = text("12\n" + ClockStyles.LABELS[i], 14, false);
            chip.setGravity(Gravity.CENTER);
            chip.setTypeface(ClockStyles.typeface(key));
            chip.setTextColor(key.equals(selected) ? Color.WHITE : 0xFFD3D6DE);
            chip.setPadding(dp(14), dp(10), dp(14), dp(10));
            chip.setBackground(rounded(key.equals(selected) ? ThemePalette.withAlpha(accent, 100) : surface, dp(16), 1));
            chip.setOnClickListener(v -> {
                prefs.edit().putString(Prefs.CLOCK_FONT, key).apply();
                notifyWallpaperRefresh();
                showSettings();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(92), dp(76));
            lp.rightMargin = dp(8);
            row.addView(chip, lp);
        }
        hsv.addView(row);
        return hsv;
    }

    private View buildClockColorPicker() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int[] colors = {Color.WHITE, accent, 0xFFFF5A36, 0xFFFFD54A, 0xFF87C8FF, 0xFFE8E8E8};
        for (int i = 0; i < colors.length; i++) {
            final int color = colors[i];
            final boolean auto = i == 0;
            View swatch = new View(this);
            swatch.setBackground(rounded(color, dp(14), 1));
            swatch.setOnClickListener(v -> {
                prefs.edit()
                        .putString(Prefs.CLOCK_COLOR_MODE, auto ? "auto" : "custom")
                        .putInt(Prefs.CLOCK_COLOR, color)
                        .apply();
                notifyWallpaperRefresh();
                rerender();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(46), dp(46));
            lp.rightMargin = dp(9);
            row.addView(swatch, lp);
        }
        return row;
    }

    private View labeledSeek(String label, String key, int fallback) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(label, 14, false);
        box.addView(title);
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(prefs.getInt(key, fallback));
        bar.setOnSeekBarChangeListener(simpleSeek(value -> {
            prefs.edit().putInt(key, value).apply();
            notifyWallpaperRefresh();
        }));
        box.addView(bar);
        return box;
    }

    private SeekBar seek(int value, int min, int max, IntConsumer onChange) {
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        bar.setOnSeekBarChangeListener(simpleSeek(v -> onChange.accept(v + min)));
        return bar;
    }

    private SeekBar.OnSeekBarChangeListener simpleSeek(IntConsumer onChange) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) onChange.accept(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }

    private void buildPhotoGrid(LinearLayout root, List<String> uris) {
        for (int i = 0; i < uris.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.addView(photoCard(uris.get(i), i), weightWithMargins());
            if (i + 1 < uris.size()) row.addView(photoCard(uris.get(i + 1), i + 1), weightWithMargins());
            else row.addView(new View(this), weightWithMargins());
            root.addView(row, marginTop(10));
        }
    }

    private View photoCard(String uriString, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(7), dp(7), dp(7), dp(7));
        card.setBackground(rounded(surface, dp(20), 1));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(0xFF151A24);
        card.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)));
        loadUserThumbnail(image, Uri.parse(uriString));
        Button use = primaryButton("Usar esta foto");
        use.setTextSize(12);
        use.setOnClickListener(v -> selectUserPhoto(index));
        card.addView(use, marginTop(6));
        Button remove = secondaryButton("Remover");
        remove.setTextSize(11);
        remove.setOnClickListener(v -> removeUserPhoto(uriString));
        card.addView(remove, marginTop(4));
        image.setOnClickListener(v -> selectUserPhoto(index));
        return card;
    }

    private View buildBuiltinGallery() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < BuiltInWallpapers.count(); i++) {
            final int index = i;
            LinearLayout card = smallCard();
            ImageView image = new ImageView(this);
            image.setImageResource(BuiltInWallpapers.THUMBNAILS[index]);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(205)));
            TextView name = text(BuiltInWallpapers.NAMES[index], 12, false);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(1);
            card.addView(name, marginTop(6));
            Button fav = miniButton(isFavorite("builtin:" + index) ? "♥" : "♡");
            fav.setOnClickListener(v -> {
                toggleFavorite("builtin:" + index);
                fav.setText(isFavorite("builtin:" + index) ? "♥" : "♡");
            });
            card.addView(fav, marginTop(4));
            image.setOnClickListener(v -> selectBuiltin(index));
            name.setOnClickListener(v -> selectBuiltin(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(132), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(10);
            row.addView(card, lp);
        }
        hsv.addView(row);
        return hsv;
    }

    private View buildRemoteGallery(List<RemoteWallpaper> items) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (RemoteWallpaper item : items) {
            LinearLayout card = smallCard();
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(0xFF151A24);
            card.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(215)));
            loadThumbnail(image, item);
            TextView name = text(item.name, 12, false);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            card.addView(name, marginTop(5));
            LinearLayout actions = new LinearLayout(this);
            Button use = miniButton("Usar");
            use.setOnClickListener(v -> selectRemote(item));
            actions.addView(use, weight());
            Button fav = miniButton(isFavorite("remote:" + item.id) ? "♥" : "♡");
            fav.setOnClickListener(v -> {
                toggleFavorite("remote:" + item.id);
                fav.setText(isFavorite("remote:" + item.id) ? "♥" : "♡");
            });
            actions.addView(fav, weight());
            card.addView(actions, marginTop(4));
            image.setOnClickListener(v -> selectRemote(item));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(138), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(10);
            row.addView(card, lp);
        }
        hsv.addView(row);
        return hsv;
    }

    private View buildRemoteListCard(RemoteWallpaper item) {
        LinearLayout card = largeCard();
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(0xFF151A24);
        card.addView(image, new LinearLayout.LayoutParams(dp(112), dp(176)));
        loadThumbnail(image, item);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(13), dp(8), 0, dp(5));
        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        info.addView(text(item.name, 16, true));
        info.addView(muted((item.category == null || item.category.isEmpty() ? "Novidades" : item.category) + (item.isNew ? " • NOVO" : "")), marginTop(4));
        Button use = primaryButton("Baixar e usar");
        use.setOnClickListener(v -> selectRemote(item));
        info.addView(use, marginTop(10));
        Button fav = secondaryButton(isFavorite("remote:" + item.id) ? "♥ Favoritado" : "♡ Favoritar");
        fav.setOnClickListener(v -> { toggleFavorite("remote:" + item.id); showCategory(item.category); });
        info.addView(fav, marginTop(6));
        return card;
    }

    private View buildBuiltinListCard(int index) {
        LinearLayout card = largeCard();
        ImageView image = new ImageView(this);
        image.setImageResource(BuiltInWallpapers.THUMBNAILS[index]);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(image, new LinearLayout.LayoutParams(dp(112), dp(176)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(13), dp(8), 0, dp(5));
        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        info.addView(text(BuiltInWallpapers.NAMES[index], 16, true));
        info.addView(muted("Depth offline • camadas reais"), marginTop(4));
        Button use = primaryButton("Usar");
        use.setOnClickListener(v -> selectBuiltin(index));
        info.addView(use, marginTop(10));
        return card;
    }

    private void addCategoryCard(LinearLayout root, String category, int count, RemoteWallpaper cover) {
        LinearLayout card = largeCard();
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(0xFF151A24);
        card.addView(image, new LinearLayout.LayoutParams(dp(96), dp(130)));
        if (cover != null) loadThumbnail(image, cover);
        else image.setImageResource(BuiltInWallpapers.THUMBNAILS[0]);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14), dp(14), 0, dp(8));
        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        info.addView(text(category == null || category.isEmpty() ? "Novidades" : category, 18, true));
        info.addView(muted(count + " wallpaper(s)"), marginTop(5));
        Button open = secondaryButton("Abrir coleção");
        open.setOnClickListener(v -> showCategory(category));
        info.addView(open, marginTop(8));
        card.setOnClickListener(v -> showCategory(category));
        root.addView(card, marginTop(12));
    }

    private Map<String, List<RemoteWallpaper>> groupByCategory() {
        List<RemoteWallpaper> sorted = new ArrayList<>(remote);
        Collections.sort(sorted, Comparator.comparing(w -> (w.category == null || w.category.isEmpty()) ? "Novidades" : w.category));
        Map<String, List<RemoteWallpaper>> grouped = new LinkedHashMap<>();
        for (RemoteWallpaper w : sorted) {
            String c = (w.category == null || w.category.isEmpty()) ? "Novidades" : w.category;
            grouped.computeIfAbsent(c, k -> new ArrayList<>()).add(w);
        }
        return grouped;
    }

    private void refreshCatalog(boolean userInitiated) {
        if (userInitiated) Toast.makeText(this, "Buscando novos wallpapers...", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                List<RemoteWallpaper> list = RemoteCatalog.fetchOnline(this);
                if (!list.isEmpty()) remote = list;
                runOnUiThread(() -> {
                    if (userInitiated) Toast.makeText(this, remote.size() + " wallpaper(s) online carregado(s).", Toast.LENGTH_SHORT).show();
                    rerender();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (userInitiated) Toast.makeText(this, "Sem conexão. Usando catálogo salvo.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void rerender() {
        if ("home".equals(page)) showHome();
        else if ("categories".equals(page)) showCategories();
        else if ("category".equals(page) && categoryPage != null) showCategory(categoryPage);
        else if ("photos".equals(page)) showPhotos();
        else if ("favorites".equals(page)) showFavorites();
        else showSettings();
    }

    private void loadThumbnail(ImageView image, RemoteWallpaper item) {
        io.execute(() -> {
            Bitmap bitmap = RemoteAssetStore.loadThumbnail(this, item);
            if (bitmap != null) runOnUiThread(() -> image.setImageBitmap(bitmap));
        });
    }

    private void loadUserThumbnail(ImageView image, Uri uri) {
        io.execute(() -> {
            Bitmap b = ThemePalette.decodeUriSampled(this, uri, 420);
            if (b != null) runOnUiThread(() -> image.setImageBitmap(b));
        });
    }

    private void selectRemote(RemoteWallpaper item) {
        Toast.makeText(this, "Baixando " + item.name + "...", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                RemoteAssetStore.selectAndDownload(this, item);
                ThemePalette.refreshFromCurrentSource(this);
                runOnUiThread(() -> {
                    readTheme();
                    applyWindowTheme();
                    notifyWallpaperRefresh();
                    Toast.makeText(this, item.name + " pronto para usar.", Toast.LENGTH_SHORT).show();
                    page = "home";
                    rebuildShell();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Falha ao baixar wallpaper: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void selectBuiltin(int index) {
        prefs.edit()
                .putString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN)
                .putInt(Prefs.BUILTIN_INDEX, index)
                .putFloat(Prefs.WALLPAPER_POSITION_X, 0f)
                .putFloat(Prefs.WALLPAPER_POSITION_Y, 0f)
                .putFloat(Prefs.WALLPAPER_SCALE, 1f)
                .apply();
        io.execute(() -> {
            ThemePalette.refreshFromCurrentSource(this);
            runOnUiThread(() -> {
                readTheme(); applyWindowTheme(); notifyWallpaperRefresh(); page = "home"; rebuildShell();
            });
        });
    }

    private void selectUserPhoto(int index) {
        List<String> uris = selectedUris();
        if (uris.isEmpty()) return;
        int safeIndex = Math.max(0, Math.min(index, uris.size() - 1));
        prefs.edit()
                .putString(Prefs.SOURCE, Prefs.SOURCE_USER)
                .putInt(Prefs.CURRENT_INDEX, safeIndex)
                .putFloat(Prefs.WALLPAPER_POSITION_X, 0f)
                .putFloat(Prefs.WALLPAPER_POSITION_Y, 0f)
                .putFloat(Prefs.WALLPAPER_SCALE, 1f)
                .apply();
        io.execute(() -> {
            ThemePalette.refreshFromCurrentSource(this);
            runOnUiThread(() -> { readTheme(); applyWindowTheme(); notifyWallpaperRefresh(); page = "home"; rebuildShell(); });
        });
    }

    private void removeUserPhoto(String uriString) {
        LinkedHashSet<String> set = new LinkedHashSet<>(selectedUris());
        set.remove(uriString);
        prefs.edit().putStringSet(Prefs.IMAGE_URIS, set).apply();
        if (set.isEmpty() && Prefs.SOURCE_USER.equals(prefs.getString(Prefs.SOURCE, ""))) {
            prefs.edit().putString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN).apply();
        }
        showPhotos();
    }

    private String currentSourceText() {
        String source = prefs.getString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN);
        if (Prefs.SOURCE_REMOTE.equals(source)) return prefs.getString(Prefs.REMOTE_NAME, "Wallpaper online") + " • online";
        if (Prefs.SOURCE_USER.equals(source) && !selectedUris().isEmpty()) return "Minha foto • galeria pessoal";
        int i = Math.floorMod(prefs.getInt(Prefs.BUILTIN_INDEX, 0), BuiltInWallpapers.count());
        return BuiltInWallpapers.NAMES[i] + " • offline depth";
    }

    private void pickImages() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_IMAGES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGES || resultCode != RESULT_OK || data == null) return;
        LinkedHashSet<String> set = new LinkedHashSet<>(selectedUris());
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) addUri(set, clip.getItemAt(i).getUri(), data.getFlags());
        } else if (data.getData() != null) {
            addUri(set, data.getData(), data.getFlags());
        }
        prefs.edit().putStringSet(Prefs.IMAGE_URIS, set).apply();
        Toast.makeText(this, set.size() + " foto(s) na sua galeria.", Toast.LENGTH_SHORT).show();
        showPhotos();
    }

    private void addUri(Set<String> set, Uri uri, int flags) {
        if (uri == null) return;
        try {
            int takeFlags = flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, takeFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        set.add(uri.toString());
    }

    private List<String> selectedUris() {
        Set<String> set = prefs.getStringSet(Prefs.IMAGE_URIS, new LinkedHashSet<>());
        return new ArrayList<>(set == null ? new LinkedHashSet<>() : set);
    }

    private Set<String> favorites() {
        Set<String> set = prefs.getStringSet(Prefs.FAVORITES, new LinkedHashSet<>());
        return new LinkedHashSet<>(set == null ? new LinkedHashSet<>() : set);
    }

    private boolean isFavorite(String key) { return favorites().contains(key); }

    private void toggleFavorite(String key) {
        Set<String> set = favorites();
        if (set.contains(key)) set.remove(key); else set.add(key);
        prefs.edit().putStringSet(Prefs.FAVORITES, set).apply();
    }

    private void openWallpaperPreview() {
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

    private void notifyWallpaperRefresh() {
        sendBroadcast(new Intent(DepthWallpaperService.ACTION_REFRESH).setPackage(getPackageName()));
    }

    private void scheduleAutoChange(boolean enabled) {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AutoChangeReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, 2001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (!enabled) {
            if (am != null) am.cancel(pi);
            return;
        }
        long minutes = Math.max(15, prefs.getInt(Prefs.AUTO_MINUTES, 60));
        long interval = minutes * 60_000L;
        if (am != null) am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval, interval, pi);
    }

    private void saveAndRefresh(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
        notifyWallpaperRefresh();
    }

    private LinearLayout pageRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(34));
        return root;
    }

    private void setPage(LinearLayout pageView) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(pageView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.removeAllViews();
        content.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void addHeader(LinearLayout root, String title, String subtitle) {
        root.addView(text(title, 30, true));
        root.addView(muted(subtitle), marginTop(4));
    }

    private void addSectionTitle(LinearLayout root, String title, String subtitle) {
        root.addView(text(title, 20, true), marginTop(24));
        TextView s = muted(subtitle);
        s.setTextColor(0xFF9EA7B5);
        root.addView(s, marginTop(3));
    }

    private TextView actionChip(String label, Runnable action) {
        TextView t = text(label, 13, true);
        t.setTextColor(0xFFF1F4F9);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(13), dp(10), dp(13), dp(10));
        t.setBackground(rounded(ThemePalette.withAlpha(accent, 30), dp(16), 1));
        t.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.rightMargin = dp(8);
        t.setLayoutParams(p);
        return t;
    }

    private LinearLayout smallCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(7), dp(7), dp(7), dp(7));
        card.setBackground(rounded(surface, dp(21), 1));
        return card;
    }

    private LinearLayout largeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(rounded(surface, dp(22), 1));
        return card;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    private TextView muted(String value) {
        TextView t = text(value, 14, false);
        t.setTextColor(0xFF9CA4B2);
        return t;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setPadding(dp(12), dp(9), dp(12), dp(9));
        b.setBackground(rounded(accent, dp(16), 0));
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setBackground(rounded(ThemePalette.mix(surface, accent, 0.14f), dp(16), 1));
        return b;
    }

    private Button miniButton(String label) {
        Button b = secondaryButton(label);
        b.setTextSize(12);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(4), dp(5), dp(4), dp(5));
        return b;
    }

    private CheckBox checkbox(String label, boolean checked) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setTextColor(0xFFF0F2F6);
        cb.setTextSize(14);
        cb.setChecked(checked);
        cb.setButtonTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{accent, 0xFF737B88}));
        return cb;
    }

    private GradientDrawable rounded(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (stroke > 0) d.setStroke(dp(stroke), ThemePalette.withAlpha(accent, 70));
        return d;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams weightWithMargins() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(4);
        p.rightMargin = dp(4);
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

    private class HeroPreview extends View {
        private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint clockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap background;
        private Bitmap foreground;

        HeroPreview() {
            super(MainActivity.this);
            loadCurrent();
        }

        private void loadCurrent() {
            String source = prefs.getString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN);
            try {
                if (Prefs.SOURCE_REMOTE.equals(source)) {
                    String bgPath = prefs.getString(Prefs.REMOTE_BACKGROUND_PATH, "");
                    String fgPath = prefs.getString(Prefs.REMOTE_FOREGROUND_PATH, "");
                    background = ThemePalette.decodeFileSampled(bgPath, 900);
                    if (fgPath != null && !fgPath.isEmpty()) foreground = ThemePalette.decodeFileSampled(fgPath, 900);
                } else if (Prefs.SOURCE_USER.equals(source)) {
                    List<String> list = selectedUris();
                    if (!list.isEmpty()) {
                        int index = Math.floorMod(prefs.getInt(Prefs.CURRENT_INDEX, 0), list.size());
                        background = ThemePalette.decodeUriSampled(MainActivity.this, Uri.parse(list.get(index)), 900);
                    }
                }
            } catch (Exception ignored) {}
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
            drawCover(canvas, background, 1.04f);
            if (prefs.getBoolean(Prefs.CLOCK, true) && prefs.getBoolean(Prefs.CLOCK_BEHIND, true)) drawClock(canvas);
            if (foreground != null) drawCover(canvas, foreground, 1.07f);
            if (prefs.getBoolean(Prefs.CLOCK, true) && !prefs.getBoolean(Prefs.CLOCK_BEHIND, true)) drawClock(canvas);
            overlayPaint.setShader(new LinearGradient(0, getHeight() * 0.45f, 0, getHeight(),
                    new int[]{0x00000000, 0x24000000, 0xA8000000}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), overlayPaint);
            overlayPaint.setShader(null);
        }

        private void drawCover(Canvas canvas, Bitmap bitmap, float extra) {
            float scale = Math.max(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight()) * extra;
            float dw = bitmap.getWidth() * scale;
            float dh = bitmap.getHeight() * scale;
            float left = (getWidth() - dw) / 2f;
            float top = (getHeight() - dh) / 2f;
            canvas.drawBitmap(bitmap, null, new RectF(left, top, left + dw, top + dh), imagePaint);
        }

        private void drawClock(Canvas canvas) {
            int size = prefs.getInt(Prefs.CLOCK_SIZE, 100);
            float timeSize = getWidth() * 0.22f * (size / 100f);
            float dateSize = Math.max(15f, timeSize * 0.17f);
            float y = getHeight() * (prefs.getInt(Prefs.CLOCK_Y, 24) / 100f);
            String font = prefs.getString(Prefs.CLOCK_FONT, "condensed");
            int color = ThemePalette.autoClockColor(prefs);
            clockPaint.setTextAlign(Paint.Align.CENTER);
            clockPaint.setTypeface(ClockStyles.typeface(font));
            clockPaint.setTextSize(timeSize);
            clockPaint.setColor(color);
            clockPaint.setShadowLayer(10f, 0, 3f, 0x88000000);
            datePaint.setTextAlign(Paint.Align.CENTER);
            datePaint.setTypeface(ClockStyles.dateTypeface(font));
            datePaint.setTextSize(dateSize);
            datePaint.setColor(color);
            datePaint.setShadowLayer(6f, 0, 2f, 0x77000000);
            Date now = new Date();
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
            String date = new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(now).toUpperCase(Locale.getDefault());
            canvas.drawText(time, getWidth() / 2f, y + timeSize * 0.78f, clockPaint);
            if (prefs.getBoolean(Prefs.CLOCK_SHOW_DATE, true)) canvas.drawText(date, getWidth() / 2f, y - dateSize * 0.20f, datePaint);
        }
    }
}
