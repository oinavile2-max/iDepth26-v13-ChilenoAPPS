package com.chilenoapps.idepth26;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
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
import android.os.CountDownTimer;
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
    // Visual oficial v1.6.1: preto profundo + amarelo premium.
    private static final int UI_BG = 0xFF050505;
    private static final int UI_SURFACE = 0xFF111111;
    private static final int UI_SURFACE_ALT = 0xFF191919;
    private static final int UI_YELLOW = 0xFFFFD400;
    private static final int UI_MUTED = 0xFFA8A8A8;
    private static final int UI_BORDER = 0xFF2C2C2C;

    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private SharedPreferences prefs;
    private FrameLayout content;
    private List<RemoteWallpaper> remote = new ArrayList<>();
    private String page = "home";
    private String categoryPage = null;
    private int adminTapCount = 0;
    private long adminTapWindowStart = 0L;

    private int accent;
    private int secondary;
    private int bg;
    private int surface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        ensureDefaults();
        maybeAskUsageConsent();
        UsageLogger.event(this, "app_open", "");
        remote = RemoteCatalog.loadCached(this);
        readTheme();
        applyWindowTheme();
        setContentView(buildShell());
        showHome();
        if (savedInstanceState == null) content.post(this::showVipLaunchPopup);
        refreshCatalog(false);
        VipManager.refreshAdminVip(this, changed -> runOnUiThread(() -> {
            if (changed) rerender();
        }));
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
    protected void onResume() {
        super.onResume();
        if (prefs != null) {
            VipManager.refreshAdminVip(this, changed -> runOnUiThread(() -> {
                if (changed) rerender();
            }));
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void showVipLaunchPopup() {
        if (isFinishing() || VipManager.isVip(this)) return;
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        FrameLayout outer = new FrameLayout(this);
        outer.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(22), dp(24), dp(22), dp(20));
        GradientDrawable cardBg = rounded(0xFF101010, dp(28), 1);
        cardBg.setStroke(dp(1), UI_YELLOW);
        card.setBackground(cardBg);

        TextView badge = text("OFERTA TEMPORÁRIA", 12, true);
        badge.setTextColor(Color.BLACK);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(12), dp(7), dp(12), dp(7));
        badge.setBackground(rounded(UI_YELLOW, dp(16), 0));
        card.addView(badge);
        TextView crown = text("♛", 42, true);
        crown.setTextColor(UI_YELLOW);
        crown.setGravity(Gravity.CENTER);
        card.addView(crown, marginTop(12));
        TextView title = text("Desbloqueie o iDepth VIP", 24, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title, marginTop(4));
        TextView body = muted("Acesse todos os wallpapers VIP, profundidade avançada, fontes e editor completo. Confira o desconto temporário disponível no plano VIP.");
        body.setGravity(Gravity.CENTER);
        card.addView(body, marginTop(8));
        Button action = primaryButton("Ver oferta VIP");
        action.setOnClickListener(v -> {
            if (dialog.isShowing()) dialog.dismiss();
            UsageLogger.event(this, "vip_launch_popup_click", "");
            startActivity(new Intent(this, VipActivity.class));
        });
        card.addView(action, marginTop(16));
        TextView countdownText = muted("Fecha automaticamente em 5s");
        countdownText.setGravity(Gravity.CENTER);
        card.addView(countdownText, marginTop(10));
        outer.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        dialog.setContentView(outer);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.72f;
            window.setAttributes(lp);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
        CountDownTimer timer = new CountDownTimer(5000, 1000) {
            @Override public void onTick(long ms) {
                long sec = Math.max(1, (ms + 999) / 1000);
                countdownText.setText("Fecha automaticamente em " + sec + "s");
            }
            @Override public void onFinish() { if (dialog.isShowing()) dialog.dismiss(); }
        };
        dialog.setOnDismissListener(d -> timer.cancel());
        timer.start();
        UsageLogger.event(this, "vip_launch_popup_show", "5s");
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
        if (!prefs.contains(Prefs.CLOCK_X)) e.putInt(Prefs.CLOCK_X, 50);
        if (!prefs.contains(Prefs.CLOCK_Y)) e.putInt(Prefs.CLOCK_Y, 24);
        if (!prefs.contains(Prefs.CLOCK_ALPHA)) e.putInt(Prefs.CLOCK_ALPHA, 100);
        if (!prefs.contains(Prefs.CLOCK_SHADOW)) e.putInt(Prefs.CLOCK_SHADOW, 70);
        if (!prefs.contains(Prefs.WALLPAPER_ROTATION)) e.putFloat(Prefs.WALLPAPER_ROTATION, 0f);
        if (!prefs.contains(Prefs.WALLPAPER_BRIGHTNESS)) e.putInt(Prefs.WALLPAPER_BRIGHTNESS, 100);
        if (!prefs.contains(Prefs.WALLPAPER_CONTRAST)) e.putInt(Prefs.WALLPAPER_CONTRAST, 100);
        if (!prefs.contains(Prefs.WALLPAPER_DIM)) e.putInt(Prefs.WALLPAPER_DIM, 0);
        if (!prefs.contains(Prefs.FOREGROUND_MOTION)) e.putInt(Prefs.FOREGROUND_MOTION, 0);
        if (!prefs.contains(Prefs.CLOCK_COLOR_MODE)) e.putString(Prefs.CLOCK_COLOR_MODE, "auto");
        if (!prefs.contains(Prefs.CLOCK_SHOW_DATE)) e.putBoolean(Prefs.CLOCK_SHOW_DATE, true);
        if (!prefs.contains(Prefs.THEME_AUTO)) e.putBoolean(Prefs.THEME_AUTO, true);
        e.apply();
    }

    private void readTheme() {
        // A paleta do wallpaper continua sendo usada pelo relógio/preview.
        // A interface, porém, usa identidade fixa para consistência visual.
        accent = UI_YELLOW;
        secondary = ThemePalette.secondary(prefs);
        bg = UI_BG;
        surface = UI_SURFACE;
    }

    private void applyWindowTheme() {
        Window w = getWindow();
        w.setStatusBarColor(UI_BG);
        w.setNavigationBarColor(UI_BG);
    }

    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UI_BG);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout navWrap = new FrameLayout(this);
        navWrap.setPadding(dp(14), dp(7), dp(14), dp(14));
        navWrap.setBackgroundColor(UI_BG);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(7), dp(8), dp(7));
        nav.setBackground(rounded(0xF2111111, dp(30), 1));
        nav.addView(navItem("▦\nInício", "home"), weight());
        nav.addView(navItem("▤\nCategorias", "categories"), weight());
        nav.addView(navItem("▣\nFotos", "photos"), weight());
        nav.addView(navItem("♡\nFavoritos", "favorites"), weight());
        nav.addView(navItem("⚙\nAjustes", "settings"), weight());
        navWrap.addView(nav, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(navWrap);
        return root;
    }

    private TextView navItem(String label, String target) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(10.5f);
        boolean selected = page.equals(target);
        t.setTextColor(selected ? Color.BLACK : 0xFFB8B8B8);
        t.setPadding(dp(2), dp(8), dp(2), dp(8));
        if (selected) t.setBackground(rounded(UI_YELLOW, dp(20), 0));
        t.setOnClickListener(v -> {
            if ("settings".equals(target)) handleAdminTap();
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

        Button vip = secondaryButton(VipManager.isVip(this) ? "VIP ativo ✓" : "♛ Conhecer iDepth VIP");
        vip.setOnClickListener(v -> startActivity(new Intent(this, VipActivity.class)));
        root.addView(vip, marginTop(8));

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
        qrow.addView(actionChip("✦ Preview", () -> startActivity(new Intent(this, WallpaperAdjustActivity.class))));
        qrow.addView(actionChip("＋ Minhas fotos", () -> showPhotos()));
        quick.addView(qrow);
        root.addView(quick, marginTop(12));

        int totalOnline = OnlinePackWallpapers.count() + remote.size();
        addSectionTitle(root, "Online", remote.isEmpty()
                ? "5 grátis • restante VIP"
                : remote.size() + " novidade(s) • 5 grátis • restante VIP");
        if (!remote.isEmpty()) {
            List<RemoteWallpaper> news = new ArrayList<>();
            for (RemoteWallpaper w : remote) if (w.isNew) news.add(w);
            if (news.isEmpty()) news.addAll(remote.subList(0, Math.min(6, remote.size())));
            if (news.size() > 6) news = new ArrayList<>(news.subList(0, 6));
            root.addView(buildRemoteGallery(news), marginTop(10));
        }
        root.addView(buildOnlinePackGallery(0, Math.min(6, OnlinePackWallpapers.count())), marginTop(10));
        Button allOnline = secondaryButton("Ver todos os " + totalOnline + " wallpapers online");
        allOnline.setOnClickListener(v -> showOnlinePack());
        root.addView(allOnline, marginTop(10));

        Button refresh = secondaryButton("↻ Atualizar catálogo online");
        refresh.setOnClickListener(v -> refreshCatalog(true));
        root.addView(refresh, marginTop(10));

        addSectionTitle(root, "Offline", "5 grátis • restante VIP");
        root.addView(buildBuiltinGallery(0, Math.min(6, BuiltInWallpapers.count())), marginTop(10));
        Button allOffline = secondaryButton("Ver todos os " + BuiltInWallpapers.count() + " wallpapers offline");
        allOffline.setOnClickListener(v -> showOfflinePack());
        root.addView(allOffline, marginTop(10));

        Button apply = primaryButton("Aplicar como Live Wallpaper");
        apply.setOnClickListener(v -> openWallpaperPreview());
        root.addView(apply, marginTop(20));

        setPage(root);
    }

    private void showOfflinePack() {
        page = "offline";
        categoryPage = null;
        LinearLayout root = pageRoot();
        addHeader(root, "Offline", "");
        TextView info = muted("5 wallpapers grátis • " + Math.max(0, BuiltInWallpapers.count() - BuiltInWallpapers.FREE_COUNT) + " VIP");
        info.setTextColor(UI_MUTED);
        root.addView(info, marginTop(4));
        root.addView(buildBuiltinGallery(), marginTop(14));
        setPage(root);
    }

    private void showOnlinePack() {
        page = "online";
        categoryPage = null;
        LinearLayout root = pageRoot();
        addHeader(root, "Online", "");
        int totalOnline = OnlinePackWallpapers.count() + remote.size();
        TextView info = muted("5 wallpapers grátis • " + Math.max(0, totalOnline - OnlinePackWallpapers.FREE_COUNT) + " VIP");
        info.setTextColor(UI_MUTED);
        root.addView(info, marginTop(4));

        if (!remote.isEmpty()) {
            addSectionTitle(root, "Novidades", "Publicados pelo Telegram");
            root.addView(buildRemoteGallery(remote), marginTop(10));
        }

        addSectionTitle(root, "Catálogo", "Coleção online");
        root.addView(buildOnlinePackGallery(0, OnlinePackWallpapers.count()), marginTop(10));

        Button refresh = secondaryButton("↻ Buscar novas atualizações");
        refresh.setOnClickListener(v -> refreshCatalog(true));
        root.addView(refresh, marginTop(12));
        setPage(root);
    }

    private void showCategories() {
        page = "categories";
        categoryPage = null;
        LinearLayout root = pageRoot();
        addHeader(root, "Categorias", "");

        Button offlinePack = secondaryButton("Offline • " + BuiltInWallpapers.count() + " wallpapers");
        offlinePack.setOnClickListener(v -> showOfflinePack());
        root.addView(offlinePack, marginTop(12));
        Button onlinePack = secondaryButton("Online • " + (OnlinePackWallpapers.count() + remote.size()) + " wallpapers");
        onlinePack.setOnClickListener(v -> showOnlinePack());
        root.addView(onlinePack, marginTop(8));
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
        addHeader(root, category, "");
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
        addHeader(root, "Minhas fotos", "");

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
        addHeader(root, "Favoritos", "");
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
        addHeader(root, "Ajustes", "");

        LinearLayout premium = new LinearLayout(this);
        premium.setOrientation(LinearLayout.HORIZONTAL);
        premium.setGravity(Gravity.CENTER_VERTICAL);
        premium.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable premiumBg = rounded(VipManager.isVip(this) ? 0xFF2A2710 : UI_SURFACE_ALT, dp(24), 1);
        premiumBg.setStroke(dp(1), UI_YELLOW);
        premium.setBackground(premiumBg);

        TextView crown = text("♛", 30, true);
        crown.setTextColor(UI_YELLOW);
        crown.setGravity(Gravity.CENTER);
        premium.addView(crown, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout premiumText = new LinearLayout(this);
        premiumText.setOrientation(LinearLayout.VERTICAL);
        premiumText.setPadding(dp(12), 0, dp(8), 0);
        premiumText.addView(text("Premium", 14, true));
        TextView unlocked = text(VipManager.isVip(this) ? "Unlocked" : "Conheça o VIP", 20, true);
        unlocked.setTextColor(UI_YELLOW);
        premiumText.addView(unlocked, marginTop(2));
        premiumText.addView(muted(VipManager.isVip(this)
                ? "Todos os recursos premium estão liberados."
                : "Profundidade avançada, fontes e catálogo VIP."), marginTop(3));
        premium.addView(premiumText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView status = text(VipManager.isVip(this) ? "Ativo" : "Planos", 12, true);
        status.setTextColor(VipManager.isVip(this) ? Color.BLACK : UI_YELLOW);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(12), dp(7), dp(12), dp(7));
        status.setBackground(rounded(VipManager.isVip(this) ? UI_YELLOW : UI_SURFACE, dp(16), 1));
        premium.addView(status);
        premium.setOnClickListener(v -> startActivity(new Intent(this, VipActivity.class)));
        root.addView(premium, marginTop(16));

        LinearLayout appControls = settingsContent();
        CheckBox consent = checkbox("Compartilhar métricas anônimas de uso", prefs.getBoolean(Prefs.USAGE_CONSENT, false));
        consent.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(Prefs.USAGE_CONSENT, checked).apply());
        appControls.addView(consent);
        CheckBox autoPalette = checkbox("Adaptar paleta do relógio ao wallpaper", prefs.getBoolean(Prefs.THEME_AUTO, true));
        autoPalette.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(Prefs.THEME_AUTO, checked).apply();
            if (checked) io.execute(() -> {
                ThemePalette.refreshFromCurrentSource(this);
                runOnUiThread(() -> notifyWallpaperRefresh());
            });
        });
        appControls.addView(autoPalette, marginTop(4));
        Button syncPalette = secondaryButton("Recalcular paleta do wallpaper");
        syncPalette.setOnClickListener(v -> io.execute(() -> {
            ThemePalette.refreshFromCurrentSource(this);
            runOnUiThread(() -> notifyWallpaperRefresh());
        }));
        appControls.addView(syncPalette, marginTop(8));
        root.addView(settingsGroup("🛡", "App & Permissões", appControls, false), marginTop(14));

        LinearLayout clockControls = settingsContent();
        CheckBox clock = checkbox("Mostrar relógio", prefs.getBoolean(Prefs.CLOCK, true));
        clock.setOnCheckedChangeListener((b, checked) -> saveAndRefresh(Prefs.CLOCK, checked));
        clockControls.addView(clock);
        CheckBox behind = checkbox("Relógio com profundidade", prefs.getBoolean(Prefs.CLOCK_BEHIND, true));
        behind.setOnCheckedChangeListener((b, checked) -> saveAndRefresh(Prefs.CLOCK_BEHIND, checked));
        clockControls.addView(behind, marginTop(4));
        CheckBox showDate = checkbox("Mostrar data", prefs.getBoolean(Prefs.CLOCK_SHOW_DATE, true));
        showDate.setOnCheckedChangeListener((b, checked) -> saveAndRefresh(Prefs.CLOCK_SHOW_DATE, checked));
        clockControls.addView(showDate, marginTop(4));
        clockControls.addView(text("Fonte", 13, true), marginTop(10));
        clockControls.addView(buildFontPicker(), marginTop(7));
        clockControls.addView(text("Cor", 13, true), marginTop(10));
        clockControls.addView(buildClockColorPicker(), marginTop(7));
        clockControls.addView(text("Tamanho", 13, true), marginTop(10));
        clockControls.addView(seek(prefs.getInt(Prefs.CLOCK_SIZE, 100), 60, 150,
                value -> { prefs.edit().putInt(Prefs.CLOCK_SIZE, value).apply(); notifyWallpaperRefresh(); }));
        clockControls.addView(text("Posição horizontal", 13, true), marginTop(7));
        clockControls.addView(seek(prefs.getInt(Prefs.CLOCK_X, 50), 8, 92,
                value -> { prefs.edit().putInt(Prefs.CLOCK_X, value).apply(); notifyWallpaperRefresh(); }));
        clockControls.addView(text("Posição vertical", 13, true), marginTop(7));
        clockControls.addView(seek(prefs.getInt(Prefs.CLOCK_Y, 24), 8, 64,
                value -> { prefs.edit().putInt(Prefs.CLOCK_Y, value).apply(); notifyWallpaperRefresh(); }));
        root.addView(settingsGroup("◷", "Clock Settings", clockControls, false), marginTop(12));

        LinearLayout animation = settingsContent();
        animation.addView(labeledSeek("Profundidade", Prefs.DEPTH, 78));
        animation.addView(labeledSeek("Parallax", Prefs.PARALLAX, 68), marginTop(8));
        animation.addView(labeledSeek("Profundidade do relógio", Prefs.CLOCK_DEPTH, 75), marginTop(8));
        animation.addView(labeledSeek("Zoom de proteção", Prefs.ZOOM, 40), marginTop(8));
        Button studio = primaryButton("Abrir Studio / Preview");
        studio.setOnClickListener(v -> startActivity(new Intent(this, WallpaperAdjustActivity.class)));
        animation.addView(studio, marginTop(12));
        root.addView(settingsGroup("✦", "Animation Settings", animation, false), marginTop(12));

        LinearLayout autoControls = settingsContent();
        CheckBox auto = checkbox("Auto Wallpaper Changer", prefs.getBoolean(Prefs.AUTO, false));
        auto.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(Prefs.AUTO, checked).apply();
            scheduleAutoChange(checked);
        });
        autoControls.addView(auto);
        root.addView(settingsGroup("◴", "Auto Wallpaper Settings", autoControls, false), marginTop(12));

        LinearLayout support = settingsContent();
        support.addView(muted("iDepth 26 • versão " + AppConfig.VERSION_NAME));
        Button vip = secondaryButton("Gerenciar plano VIP");
        vip.setOnClickListener(v -> startActivity(new Intent(this, VipActivity.class)));
        support.addView(vip, marginTop(8));
        root.addView(settingsGroup("?", "Support & About", support, false), marginTop(12));

        setPage(root);
    }

    private View buildPaletteStrip() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int[] colors = ThemePalette.palette(prefs);
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
            final boolean lockedFont = i >= 3 && !VipManager.isVip(this);
            if (lockedFont) chip.setText("♛\n" + ClockStyles.LABELS[i]);
            chip.setOnClickListener(v -> {
                if (lockedFont) {
                    UsageLogger.event(this, "vip_gate", "font:" + key);
                    startActivity(new Intent(this, VipActivity.class));
                    return;
                }
                prefs.edit().putString(Prefs.CLOCK_FONT, key).apply();
                UsageLogger.event(this, "personalize_font", key);
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

        TextView auto = text("AUTO", 10, true);
        auto.setGravity(Gravity.CENTER);
        auto.setBackground(rounded(0xFF242424, dp(14), 1));
        auto.setOnClickListener(v -> {
            prefs.edit().putString(Prefs.CLOCK_COLOR_MODE, "auto").apply();
            notifyWallpaperRefresh();
            rerender();
        });
        LinearLayout.LayoutParams autoLp = new LinearLayout.LayoutParams(dp(54), dp(46));
        autoLp.rightMargin = dp(9);
        row.addView(auto, autoLp);

        int[] colors = ThemePalette.palette(prefs);
        for (int color : colors) {
            final int swatchColor = color;
            View swatch = new View(this);
            swatch.setBackground(rounded(swatchColor, dp(14), 1));
            swatch.setOnClickListener(v -> {
                prefs.edit()
                        .putString(Prefs.CLOCK_COLOR_MODE, "custom")
                        .putInt(Prefs.CLOCK_COLOR, swatchColor)
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
        card.setPadding(0, 0, 0, dp(7));
        card.setBackground(rounded(UI_SURFACE, dp(22), 1));
        card.setClipToOutline(true);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(0xFF151A24);
        card.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)));
        loadUserThumbnail(image, Uri.parse(uriString));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button use = miniButton("Usar");
        use.setOnClickListener(v -> selectUserPhoto(index));
        actions.addView(use, weight());

        String favKey = "user:" + uriString.hashCode();
        Button fav = miniButton(isFavorite(favKey) ? "♥" : "♡");
        fav.setOnClickListener(v -> {
            toggleFavorite(favKey);
            fav.setText(isFavorite(favKey) ? "♥" : "♡");
        });
        actions.addView(fav, weight());
        card.addView(actions, marginTop(6));

        Button submit = secondaryButton("Enviar para curadoria");
        submit.setTextSize(11);
        submit.setOnClickListener(v -> confirmAndSubmitUserWallpaper(Uri.parse(uriString), submit));
        card.addView(submit, marginTop(4));

        Button remove = secondaryButton("Remover");
        remove.setTextSize(11);
        remove.setOnClickListener(v -> removeUserPhoto(uriString));
        card.addView(remove, marginTop(4));
        image.setOnClickListener(v -> selectUserPhoto(index));
        return card;
    }

    private View buildBuiltinGallery() {
        return buildBuiltinGallery(0, BuiltInWallpapers.count());
    }

    private View buildBuiltinGallery(int start, int count) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int end = Math.min(BuiltInWallpapers.count(), start + Math.max(0, count));
        int rowIndex = 0;
        for (int i = start; i < end; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.addView(builtinGridCard(i), weightWithMargins());
            if (i + 1 < end) row.addView(builtinGridCard(i + 1), weightWithMargins());
            else row.addView(new View(this), weightWithMargins());
            grid.addView(row, marginTop(rowIndex++ == 0 ? 0 : 9));
        }
        return grid;
    }

    private View buildOnlinePackGallery(int start, int count) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int end = Math.min(OnlinePackWallpapers.count(), start + Math.max(0, count));
        int rowIndex = 0;
        for (int i = start; i < end; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.addView(onlinePackGridCard(i), weightWithMargins());
            if (i + 1 < end) row.addView(onlinePackGridCard(i + 1), weightWithMargins());
            else row.addView(new View(this), weightWithMargins());
            grid.addView(row, marginTop(rowIndex++ == 0 ? 0 : 9));
        }
        return grid;
    }

    private View buildRemoteGallery(List<RemoteWallpaper> items) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.addView(remoteGridCard(items.get(i)), weightWithMargins());
            if (i + 1 < items.size()) row.addView(remoteGridCard(items.get(i + 1)), weightWithMargins());
            else row.addView(new View(this), weightWithMargins());
            grid.addView(row, marginTop(i == 0 ? 0 : 9));
        }
        return grid;
    }

    private View builtinGridCard(int index) {
        LinearLayout card = smallCard();
        FrameLayout imageFrame = new FrameLayout(this);
        ImageView image = new ImageView(this);
        image.setImageResource(BuiltInWallpapers.THUMBNAILS[index]);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageFrame.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        boolean selected = Prefs.SOURCE_BUILTIN.equals(prefs.getString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN))
                && prefs.getInt(Prefs.BUILTIN_INDEX, 0) == index;
        if (selected || BuiltInWallpapers.isVipOnly(index)) {
            TextView badge = text(selected ? "✓" : "♛", 15, true);
            badge.setTextColor(Color.BLACK);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(rounded(UI_YELLOW, dp(14), 0));
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP | Gravity.END);
            bp.setMargins(0, dp(8), dp(8), 0);
            imageFrame.addView(badge, bp);
        }
        card.addView(imageFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(235)));

        TextView name = text(BuiltInWallpapers.NAMES[index], 12, true);
        name.setMaxLines(1);
        name.setPadding(dp(10), dp(8), dp(10), 0);
        card.addView(name);
        TextView hint = muted(BuiltInWallpapers.isVipOnly(index) ? "Offline • VIP" : "Offline • Free");
        hint.setTextSize(11);
        hint.setPadding(dp(10), 0, dp(10), 0);
        card.addView(hint, marginTop(1));
        imageFrame.setOnClickListener(v -> selectBuiltin(index));
        card.setOnClickListener(v -> selectBuiltin(index));
        return card;
    }

    private View onlinePackGridCard(int index) {
        LinearLayout card = smallCard();
        FrameLayout imageFrame = new FrameLayout(this);
        ImageView image = new ImageView(this);
        image.setImageResource(OnlinePackWallpapers.THUMBNAILS[index]);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageFrame.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        boolean selected = Prefs.SOURCE_ONLINE_PACK.equals(prefs.getString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN))
                && prefs.getInt(Prefs.ONLINE_PACK_INDEX, 0) == index;
        if (selected || OnlinePackWallpapers.isVipOnly(index)) {
            TextView badge = text(selected ? "✓" : "♛", 15, true);
            badge.setTextColor(Color.BLACK);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(rounded(UI_YELLOW, dp(14), 0));
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP | Gravity.END);
            bp.setMargins(0, dp(8), dp(8), 0);
            imageFrame.addView(badge, bp);
        }
        card.addView(imageFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(235)));

        TextView name = text(OnlinePackWallpapers.NAMES[index], 12, true);
        name.setMaxLines(1);
        name.setPadding(dp(10), dp(8), dp(10), 0);
        card.addView(name);
        TextView hint = muted(OnlinePackWallpapers.isVipOnly(index) ? "Online • VIP" : "Online • Free");
        hint.setTextSize(11);
        hint.setPadding(dp(10), 0, dp(10), 0);
        card.addView(hint, marginTop(1));
        imageFrame.setOnClickListener(v -> selectOnlinePack(index));
        card.setOnClickListener(v -> selectOnlinePack(index));
        return card;
    }

    private View remoteGridCard(RemoteWallpaper item) {
        LinearLayout card = smallCard();
        FrameLayout imageFrame = new FrameLayout(this);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(0xFF171717);
        imageFrame.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loadThumbnail(image, item);

        boolean selected = Prefs.SOURCE_REMOTE.equals(prefs.getString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN))
                && item.id.equals(prefs.getString(Prefs.REMOTE_ID, ""));
        if (selected || item.vipOnly) {
            TextView badge = text(selected ? "✓" : "♛", 15, true);
            badge.setTextColor(Color.BLACK);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(rounded(UI_YELLOW, dp(14), 0));
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP | Gravity.END);
            bp.setMargins(0, dp(8), dp(8), 0);
            imageFrame.addView(badge, bp);
        }
        card.addView(imageFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(235)));

        TextView name = text(item.name, 12, true);
        name.setMaxLines(1);
        name.setPadding(dp(10), dp(8), dp(10), 0);
        card.addView(name);
        TextView hint = muted((item.category == null || item.category.isEmpty() ? "Novidades" : item.category)
                + (item.vipOnly ? " • VIP" : ""));
        hint.setTextSize(11);
        hint.setPadding(dp(10), 0, dp(10), 0);
        card.addView(hint, marginTop(1));

        imageFrame.setOnClickListener(v -> selectRemote(item));
        card.setOnClickListener(v -> selectRemote(item));
        return card;
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
        else if ("offline".equals(page)) showOfflinePack();
        else if ("online".equals(page)) showOnlinePack();
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
        if (item.vipOnly && !VipManager.isVip(this)) {
            UsageLogger.event(this, "vip_gate", "remote:" + item.id);
            startActivity(new Intent(this, VipActivity.class));
            return;
        }
        Toast.makeText(this, "Baixando " + item.name + "...", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                RemoteAssetStore.selectAndDownload(this, item);
                prefs.edit().putBoolean(Prefs.CLOCK_BEHIND, true).apply();
                ThemePalette.refreshFromCurrentSource(this);
                runOnUiThread(() -> {
                    readTheme();
                    applyWindowTheme();
                    notifyWallpaperRefresh();
                    UsageLogger.event(this, "wallpaper_select_remote", item.id);
                    Toast.makeText(this, item.name + " pronto para personalizar.", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, WallpaperAdjustActivity.class));
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Falha ao baixar wallpaper: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void selectBuiltin(int index) {
        if (BuiltInWallpapers.isVipOnly(index) && !VipManager.isVip(this)) {
            UsageLogger.event(this, "vip_gate", "builtin:" + index);
            startActivity(new Intent(this, VipActivity.class));
            return;
        }
        prefs.edit()
                .putString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN)
                .putInt(Prefs.BUILTIN_INDEX, index)
                .putString(Prefs.BUILTIN_FOREGROUND_PATH, "")
                .putBoolean(Prefs.CLOCK_BEHIND, true)
                .putFloat(Prefs.WALLPAPER_POSITION_X, 0f)
                .putFloat(Prefs.WALLPAPER_POSITION_Y, 0f)
                .putFloat(Prefs.WALLPAPER_SCALE, 1f)
                .apply();
        Toast.makeText(this, "Preparando profundidade...", Toast.LENGTH_SHORT).show();
        UserDepthProcessor.prepareResource(this, BuiltInWallpapers.BACKGROUNDS[index], "offline_" + index, (foregroundPath, ok) -> runOnUiThread(() -> {
            prefs.edit().putString(Prefs.BUILTIN_FOREGROUND_PATH, foregroundPath).apply();
            io.execute(() -> {
                ThemePalette.refreshFromCurrentSource(this);
                runOnUiThread(() -> {
                    readTheme();
                    applyWindowTheme();
                    notifyWallpaperRefresh();
                    UsageLogger.event(this, ok ? "wallpaper_select_builtin_depth" : "wallpaper_select_builtin", String.valueOf(index));
                    startActivity(new Intent(this, WallpaperAdjustActivity.class));
                });
            });
        }));
    }

    private void selectOnlinePack(int index) {
        if (OnlinePackWallpapers.isVipOnly(index) && !VipManager.isVip(this)) {
            UsageLogger.event(this, "vip_gate", "online_pack:" + index);
            startActivity(new Intent(this, VipActivity.class));
            return;
        }
        prefs.edit()
                .putString(Prefs.SOURCE, Prefs.SOURCE_ONLINE_PACK)
                .putInt(Prefs.ONLINE_PACK_INDEX, index)
                .putString(Prefs.ONLINE_PACK_FOREGROUND_PATH, "")
                .putBoolean(Prefs.CLOCK_BEHIND, true)
                .putFloat(Prefs.WALLPAPER_POSITION_X, 0f)
                .putFloat(Prefs.WALLPAPER_POSITION_Y, 0f)
                .putFloat(Prefs.WALLPAPER_SCALE, 1f)
                .apply();
        Toast.makeText(this, "Preparando profundidade...", Toast.LENGTH_SHORT).show();
        UserDepthProcessor.prepareResource(this, OnlinePackWallpapers.BACKGROUNDS[index], "online_" + index, (foregroundPath, ok) -> runOnUiThread(() -> {
            prefs.edit().putString(Prefs.ONLINE_PACK_FOREGROUND_PATH, foregroundPath).apply();
            io.execute(() -> {
                ThemePalette.refreshFromCurrentSource(this);
                runOnUiThread(() -> {
                    readTheme();
                    applyWindowTheme();
                    notifyWallpaperRefresh();
                    UsageLogger.event(this, ok ? "wallpaper_select_online_pack_depth" : "wallpaper_select_online_pack", String.valueOf(index));
                    startActivity(new Intent(this, WallpaperAdjustActivity.class));
                });
            });
        }));
    }

    private void selectUserPhoto(int index) {
        List<String> uris = selectedUris();
        if (uris.isEmpty()) return;
        int safeIndex = Math.max(0, Math.min(index, uris.size() - 1));
        String selectedUri = uris.get(safeIndex);
        prefs.edit()
                .putString(Prefs.SOURCE, Prefs.SOURCE_USER)
                .putInt(Prefs.CURRENT_INDEX, safeIndex)
                .putString(Prefs.USER_FOREGROUND_PATH, "")
                .putBoolean(Prefs.CLOCK_BEHIND, true)
                .putFloat(Prefs.WALLPAPER_POSITION_X, 0f)
                .putFloat(Prefs.WALLPAPER_POSITION_Y, 0f)
                .putFloat(Prefs.WALLPAPER_SCALE, 1f)
                .apply();

        if (VipManager.isVip(this)) {
            Toast.makeText(this, "Preparando profundidade automática...", Toast.LENGTH_SHORT).show();
            UserDepthProcessor.prepare(this, Uri.parse(selectedUri), (foregroundPath, ok) -> runOnUiThread(() -> {
                prefs.edit().putString(Prefs.USER_FOREGROUND_PATH, foregroundPath).apply();
                UsageLogger.event(this, ok ? "user_depth_ready" : "user_depth_fallback", "");
                finishUserPhotoSelection(safeIndex);
            }));
        } else {
            finishUserPhotoSelection(safeIndex);
        }
    }

    private void finishUserPhotoSelection(int safeIndex) {
        io.execute(() -> {
            ThemePalette.refreshFromCurrentSource(this);
            runOnUiThread(() -> {
                readTheme();
                applyWindowTheme();
                notifyWallpaperRefresh();
                UsageLogger.event(this, "wallpaper_select_user", String.valueOf(safeIndex));
                startActivity(new Intent(this, WallpaperAdjustActivity.class));
            });
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
        if (Prefs.SOURCE_ONLINE_PACK.equals(source)) {
            int i = Math.floorMod(prefs.getInt(Prefs.ONLINE_PACK_INDEX, 0), OnlinePackWallpapers.count());
            return OnlinePackWallpapers.NAMES[i] + " • online";
        }
        if (Prefs.SOURCE_USER.equals(source) && !selectedUris().isEmpty()) return "Minha foto • galeria pessoal";
        int i = Math.floorMod(prefs.getInt(Prefs.BUILTIN_INDEX, 0), BuiltInWallpapers.count());
        return BuiltInWallpapers.NAMES[i] + " • offline depth";
    }

    private void pickImages() {
        if (!VipManager.isVip(this) && selectedUris().size() >= 3) {
            UsageLogger.event(this, "vip_gate", "user_photos_limit");
            Toast.makeText(this, "O plano grátis permite até 3 fotos. VIP libera a galeria completa.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, VipActivity.class));
            return;
        }
        TextView warning = new TextView(this);
        warning.setText("ATENÇÃO: imagens escolhidas da galeria podem ter resolução, compressão e enquadramento diferentes. Recorte, zoom e profundidade automática podem reduzir a nitidez e afetar a experiência final do wallpaper.");
        warning.setTextColor(0xFFFF4D4D);
        warning.setTextSize(14);
        warning.setPadding(dp(8), dp(6), dp(8), dp(6));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Qualidade da imagem")
                .setView(warning)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Continuar para galeria", (d, which) -> openImagePicker())
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(UI_YELLOW);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(UI_MUTED);
        });
        dialog.show();
    }

    private void openImagePicker() {
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
        if (!VipManager.isVip(this) && set.size() > 3) {
            LinkedHashSet<String> limited = new LinkedHashSet<>();
            int count = 0;
            for (String value : set) {
                if (count++ >= 3) break;
                limited.add(value);
            }
            set = limited;
            Toast.makeText(this, "Plano grátis: mantidas as 3 primeiras fotos. VIP libera ilimitadas.", Toast.LENGTH_LONG).show();
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

    private void maybeAskUsageConsent() {
        if (prefs.getBoolean(Prefs.USAGE_CONSENT_ASKED, false)) return;
        new AlertDialog.Builder(this)
                .setTitle("Ajude a melhorar o iDepth 26")
                .setMessage("Podemos registrar eventos anônimos de uso, como abertura do app, aplicação de wallpaper e uso do editor. Não enviamos nome, CPF, telefone, e-mail ou conteúdo das suas fotos. Você pode recusar e continuar usando o app normalmente.")
                .setNegativeButton("Agora não", (d, w) -> prefs.edit()
                        .putBoolean(Prefs.USAGE_CONSENT_ASKED, true)
                        .putBoolean(Prefs.USAGE_CONSENT, false)
                        .apply())
                .setPositiveButton("Permitir", (d, w) -> {
                    prefs.edit()
                            .putBoolean(Prefs.USAGE_CONSENT_ASKED, true)
                            .putBoolean(Prefs.USAGE_CONSENT, true)
                            .apply();
                    UsageLogger.event(this, "analytics_consent", "granted");
                })
                .setCancelable(false)
                .show();
    }

    private void confirmAndSubmitUserWallpaper(Uri uri, Button submit) {
        Runnable upload = () -> {
            submit.setEnabled(false);
            Toast.makeText(this, "Enviando para curadoria...", Toast.LENGTH_SHORT).show();
            SubmissionClient.submit(this, uri, (ok, message) -> runOnUiThread(() -> {
                submit.setEnabled(true);
                UsageLogger.event(this, ok ? "user_submission_ok" : "user_submission_error", "");
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }));
        };

        if (prefs.getBoolean(Prefs.SUBMISSION_NOTICE_ACCEPTED, false)) {
            upload.run();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Enviar wallpaper para curadoria")
                .setMessage("Ao continuar, esta imagem será enviada para o servidor do iDepth 26 para análise da equipe ChilenoAPPS e poderá ser encaminhada por e-mail à curadoria. A imagem não será publicada automaticamente.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Concordo e enviar", (d, w) -> {
                    prefs.edit().putBoolean(Prefs.SUBMISSION_NOTICE_ACCEPTED, true).apply();
                    upload.run();
                })
                .show();
    }

    private void openWallpaperPreview() {
        UsageLogger.event(this, "preview_open", prefs.getString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN));
        startActivity(new Intent(this, WallpaperAdjustActivity.class));
    }

    private void handleAdminTap() {
        long now = System.currentTimeMillis();
        if (adminTapWindowStart == 0L || now - adminTapWindowStart > 5000L) {
            adminTapWindowStart = now;
            adminTapCount = 0;
        }
        adminTapCount++;
        if (adminTapCount >= 7) {
            adminTapCount = 0;
            adminTapWindowStart = 0L;
            UsageLogger.event(this, "admin_gesture", "");
            startActivity(new Intent(this, AdminActivity.class));
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
        root.setPadding(dp(18), dp(14), dp(18), dp(30));
        root.setBackgroundColor(UI_BG);
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
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            root.addView(muted(subtitle), marginTop(4));
        }
    }

    private void addSectionTitle(LinearLayout root, String title, String subtitle) {
        root.addView(text(title, 20, true), marginTop(24));
    }

    private TextView actionChip(String label, Runnable action) {
        TextView t = text(label, 13, true);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        t.setBackground(rounded(UI_SURFACE_ALT, dp(20), 1));
        t.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.rightMargin = dp(8);
        t.setLayoutParams(p);
        return t;
    }

    private LinearLayout settingsContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(4), dp(14), dp(14));
        return content;
    }

    private View settingsGroup(String icon, String title, View body, boolean expandedByDefault) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(UI_SURFACE, dp(22), 1));
        card.setClipToOutline(true);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(15), dp(16), dp(15));

        TextView iconView = text(icon, 20, true);
        iconView.setTextColor(UI_YELLOW);
        iconView.setGravity(Gravity.CENTER);
        header.addView(iconView, new LinearLayout.LayoutParams(dp(34), dp(34)));

        TextView label = text(title, 17, true);
        label.setPadding(dp(9), 0, 0, 0);
        header.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = text(expandedByDefault ? "⌃" : "⌄", 18, true);
        arrow.setTextColor(0xFFBEBEBE);
        header.addView(arrow);

        body.setVisibility(expandedByDefault ? View.VISIBLE : View.GONE);
        header.setOnClickListener(v -> {
            boolean show = body.getVisibility() != View.VISIBLE;
            body.setVisibility(show ? View.VISIBLE : View.GONE);
            arrow.setText(show ? "⌃" : "⌄");
        });

        card.addView(header);
        card.addView(body);
        return card;
    }

    private LinearLayout smallCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, 0, 0, dp(7));
        card.setBackground(rounded(UI_SURFACE, dp(22), 1));
        card.setClipToOutline(true);
        return card;
    }

    private LinearLayout largeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(0, 0, dp(10), 0);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(rounded(UI_SURFACE, dp(22), 1));
        card.setClipToOutline(true);
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
        t.setTextColor(UI_MUTED);
        return t;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.BLACK);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        b.setBackground(rounded(UI_YELLOW, dp(22), 0));
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setPadding(dp(14), dp(9), dp(14), dp(9));
        b.setBackground(rounded(UI_SURFACE_ALT, dp(20), 1));
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
        cb.setTextColor(0xFFF4F4F4);
        cb.setTextSize(14);
        cb.setChecked(checked);
        cb.setButtonTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{UI_YELLOW, 0xFF777777}));
        return cb;
    }

    private GradientDrawable rounded(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (stroke > 0) d.setStroke(dp(stroke), UI_BORDER);
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
                } else if (Prefs.SOURCE_ONLINE_PACK.equals(source)) {
                    int index = Math.floorMod(prefs.getInt(Prefs.ONLINE_PACK_INDEX, 0), OnlinePackWallpapers.count());
                    background = BitmapFactory.decodeResource(getResources(), OnlinePackWallpapers.BACKGROUNDS[index]);
                    String fgPath = prefs.getString(Prefs.ONLINE_PACK_FOREGROUND_PATH, "");
                    if (fgPath != null && !fgPath.isEmpty()) foreground = ThemePalette.decodeFileSampled(fgPath, 900);
                } else if (Prefs.SOURCE_USER.equals(source)) {
                    List<String> list = selectedUris();
                    if (!list.isEmpty()) {
                        int index = Math.floorMod(prefs.getInt(Prefs.CURRENT_INDEX, 0), list.size());
                        background = ThemePalette.decodeUriSampled(MainActivity.this, Uri.parse(list.get(index)), 900);
                        String fgPath = prefs.getString(Prefs.USER_FOREGROUND_PATH, "");
                        if (fgPath != null && !fgPath.isEmpty()) foreground = ThemePalette.decodeFileSampled(fgPath, 900);
                    }
                }
            } catch (Exception ignored) {}
            if (background == null) {
                int index = Math.floorMod(prefs.getInt(Prefs.BUILTIN_INDEX, 0), BuiltInWallpapers.count());
                background = BitmapFactory.decodeResource(getResources(), BuiltInWallpapers.BACKGROUNDS[index]);
                String fgPath = prefs.getString(Prefs.BUILTIN_FOREGROUND_PATH, "");
                if (fgPath != null && !fgPath.isEmpty()) foreground = ThemePalette.decodeFileSampled(fgPath, 900);
                else if (BuiltInWallpapers.FOREGROUNDS[index] != 0) foreground = BitmapFactory.decodeResource(getResources(), BuiltInWallpapers.FOREGROUNDS[index]);
            }
            if (foreground != null && !hasUsefulAlpha(foreground)) {
                try { foreground.recycle(); } catch (Exception ignored) {}
                foreground = null;
            }
            if (background != null && prefs.getBoolean(Prefs.THEME_AUTO, true)) {
                ThemePalette.applyFromBitmap(MainActivity.this, background);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.BLACK);
            if (background == null) return;
            drawCover(canvas, background, 1.04f);
            if (prefs.getBoolean(Prefs.CLOCK, true) && prefs.getBoolean(Prefs.CLOCK_BEHIND, true)) drawClock(canvas);
            if (foreground != null) drawCover(canvas, foreground, 1.04f);
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

        private boolean hasUsefulAlpha(Bitmap bitmap) {
            if (bitmap == null || !bitmap.hasAlpha()) return false;
            int w = bitmap.getWidth(), h = bitmap.getHeight();
            int sx = Math.max(1, w / 30), sy = Math.max(1, h / 40);
            int total = 0, transparent = 0;
            for (int yy = 0; yy < h; yy += sy) {
                for (int xx = 0; xx < w; xx += sx) {
                    total++;
                    if (Color.alpha(bitmap.getPixel(xx, yy)) < 245) transparent++;
                }
            }
            return total > 0 && transparent / (float) total >= 0.03f;
        }

        private void drawClock(Canvas canvas) {
            int size = prefs.getInt(Prefs.CLOCK_SIZE, 100);
            float timeSize = getWidth() * 0.22f * (size / 100f);
            float dateSize = Math.max(15f, timeSize * 0.17f);
            float x = getWidth() * (prefs.getInt(Prefs.CLOCK_X, 50) / 100f);
            float y = getHeight() * (prefs.getInt(Prefs.CLOCK_Y, 24) / 100f);
            String font = prefs.getString(Prefs.CLOCK_FONT, "condensed");
            int color = ThemePalette.clockColor(prefs, background, prefs.getInt(Prefs.CLOCK_X, 50), prefs.getInt(Prefs.CLOCK_Y, 24), size);
            clockPaint.setTextAlign(Paint.Align.CENTER);
            clockPaint.setTypeface(ClockStyles.typeface(font));
            clockPaint.setTextSize(timeSize);
            clockPaint.setColor(color);
            clockPaint.setAlpha(Math.round(255f * prefs.getInt(Prefs.CLOCK_ALPHA, 100) / 100f));
            float shadow = prefs.getInt(Prefs.CLOCK_SHADOW, 70) / 100f;
            clockPaint.setShadowLayer(3f + 17f * shadow, 0, 2f + 3f * shadow, 0x99000000);
            datePaint.setTextAlign(Paint.Align.CENTER);
            datePaint.setTypeface(ClockStyles.dateTypeface(font));
            datePaint.setTextSize(dateSize);
            datePaint.setColor(color);
            datePaint.setAlpha(Math.round(255f * prefs.getInt(Prefs.CLOCK_ALPHA, 100) / 100f));
            datePaint.setShadowLayer(2f + 9f * shadow, 0, 1f + 2f * shadow, 0x77000000);
            Date now = new Date();
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
            String date = new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(now).toUpperCase(Locale.getDefault());
            canvas.drawText(time, x, y + timeSize * 0.78f, clockPaint);
            if (prefs.getBoolean(Prefs.CLOCK_SHOW_DATE, true)) canvas.drawText(date, x, y - dateSize * 0.20f, datePaint);
        }
    }
}
