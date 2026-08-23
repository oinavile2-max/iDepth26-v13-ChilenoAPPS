package com.chilenoapps.idepth26;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_IMAGES = 1001;
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private SharedPreferences prefs;
    private FrameLayout content;
    private List<RemoteWallpaper> remote = new ArrayList<>();
    private String page = "home";
    private String categoryPage = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        remote = RemoteCatalog.loadCached(this);
        setContentView(buildShell());
        showHome();
        refreshCatalog(false);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(5, 7, 12));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(6), dp(6), dp(6), dp(8));
        nav.setBackgroundColor(Color.rgb(12, 15, 24));

        nav.addView(navButton("Início", () -> showHome()), weight());
        nav.addView(navButton("Categorias", () -> showCategories()), weight());
        nav.addView(navButton("Favoritos", () -> showFavorites()), weight());
        nav.addView(navButton("Ajustes", () -> showSettings()), weight());
        root.addView(nav);
        return root;
    }

    private Button navButton(String label, Runnable action) {
        Button b = button(label);
        b.setTextSize(12);
        b.setMinHeight(0);
        b.setPadding(dp(2), dp(8), dp(2), dp(8));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void showHome() {
        page = "home";
        categoryPage = null;
        LinearLayout root = pageRoot();
        addHeader(root, "iDepth 26 Wallpapers", "Profundidade, parallax e novos wallpapers sem atualizar o APK.");

        TextView source = text(currentSourceText(), 14, true);
        source.setTextColor(Color.rgb(130, 190, 255));
        root.addView(source, marginTop(12));

        root.addView(section(BuiltInWallpapers.count() + " WALLPAPERS OFFLINE"), marginTop(24));
        TextView offlineHint = muted("Funcionam sem internet e possuem camadas preparadas para profundidade.");
        root.addView(offlineHint, marginTop(5));
        root.addView(buildBuiltinGallery(), marginTop(10));

        root.addView(section("NOVOS ONLINE"), marginTop(26));
        if (remote.isEmpty()) {
            TextView no = muted("Conectando ao catálogo remoto...");
            root.addView(no, marginTop(8));
        } else {
            List<RemoteWallpaper> news = new ArrayList<>();
            for (RemoteWallpaper w : remote) if (w.isNew) news.add(w);
            if (news.isEmpty()) news.addAll(remote.subList(0, Math.min(8, remote.size())));
            root.addView(buildRemoteGallery(news), marginTop(10));
        }

        Button refresh = button("Atualizar catálogo agora");
        refresh.setOnClickListener(v -> refreshCatalog(true));
        root.addView(refresh, marginTop(12));

        root.addView(section("APLICAR"), marginTop(26));
        Button adjust = button("Ajustar enquadramento");
        adjust.setOnClickListener(v -> startActivity(new Intent(this, WallpaperAdjustActivity.class)));
        root.addView(adjust, marginTop(8));
        Button apply = button("Pré-visualizar e aplicar wallpaper");
        apply.setOnClickListener(v -> openWallpaperPreview());
        root.addView(apply, marginTop(8));
        setPage(root);
    }

    private void showCategories() {
        page = "categories";
        categoryPage = null;
        LinearLayout root = pageRoot();
        addHeader(root, "Categorias", "As categorias online são atualizadas pelo catálogo remoto.");

        addCategoryCard(root, "Offline Depth", BuiltInWallpapers.count(), null);

        Map<String, List<RemoteWallpaper>> grouped = groupByCategory();
        if (grouped.isEmpty()) {
            root.addView(muted("Nenhuma categoria online carregada. Toque em Início e atualize o catálogo."), marginTop(14));
        } else {
            for (Map.Entry<String, List<RemoteWallpaper>> e : grouped.entrySet()) {
                RemoteWallpaper cover = e.getValue().isEmpty() ? null : e.getValue().get(0);
                addCategoryCard(root, e.getKey(), e.getValue().size(), cover);
            }
        }
        setPage(root);
    }

    private void showCategory(String category) {
        page = "category";
        categoryPage = category;
        LinearLayout root = pageRoot();
        Button back = button("← Voltar para categorias");
        back.setOnClickListener(v -> showCategories());
        root.addView(back);
        addHeader(root, category, "Escolha um wallpaper para baixar e usar.");

        if ("Offline Depth".equals(category)) {
            root.addView(buildBuiltinGallery(), marginTop(12));
        } else {
            List<RemoteWallpaper> items = new ArrayList<>();
            for (RemoteWallpaper w : remote) if (category.equals(w.category)) items.add(w);
            for (RemoteWallpaper w : items) root.addView(buildRemoteListCard(w), marginTop(10));
        }
        setPage(root);
    }

    private void showFavorites() {
        page = "favorites";
        categoryPage = null;
        LinearLayout root = pageRoot();
        addHeader(root, "Favoritos", "Seus wallpapers marcados ficam reunidos aqui.");
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
        if (!any) root.addView(muted("Você ainda não marcou nenhum wallpaper como favorito."), marginTop(14));
        setPage(root);
    }

    private void showSettings() {
        page = "settings";
        categoryPage = null;
        LinearLayout root = pageRoot();
        addHeader(root, "Ajustes", "Controle profundidade, relógio, suas fotos e atualizações.");

        root.addView(section("EFEITO DE PROFUNDIDADE"), marginTop(18));
        root.addView(text("Intensidade 3D / movimento", 15, false), marginTop(10));
        SeekBar depth = new SeekBar(this);
        depth.setMax(100);
        depth.setProgress(prefs.getInt(Prefs.DEPTH, 70));
        depth.setOnSeekBarChangeListener(simpleSeek(progress -> {
            prefs.edit().putInt(Prefs.DEPTH, progress).apply();
            notifyWallpaperRefresh();
        }));
        root.addView(depth);

        root.addView(text("Zoom de proteção das bordas", 15, false), marginTop(10));
        SeekBar zoom = new SeekBar(this);
        zoom.setMax(100);
        zoom.setProgress(prefs.getInt(Prefs.ZOOM, 38));
        zoom.setOnSeekBarChangeListener(simpleSeek(progress -> {
            prefs.edit().putInt(Prefs.ZOOM, progress).apply();
            notifyWallpaperRefresh();
        }));
        root.addView(zoom);

        root.addView(section("RELÓGIO EM CAMADAS"), marginTop(22));
        CheckBox clock = checkbox("Mostrar relógio no wallpaper", prefs.getBoolean(Prefs.CLOCK, true));
        clock.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(Prefs.CLOCK, checked).apply();
            notifyWallpaperRefresh();
        });
        root.addView(clock, marginTop(8));

        CheckBox behind = checkbox("Relógio atrás do primeiro plano", prefs.getBoolean(Prefs.CLOCK_BEHIND, true));
        behind.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(Prefs.CLOCK_BEHIND, checked).apply();
            notifyWallpaperRefresh();
        });
        root.addView(behind, marginTop(4));

        root.addView(text("Profundidade / movimento do relógio", 15, false), marginTop(10));
        SeekBar clockDepth = new SeekBar(this);
        clockDepth.setMax(100);
        clockDepth.setProgress(prefs.getInt(Prefs.CLOCK_DEPTH, 70));
        clockDepth.setOnSeekBarChangeListener(simpleSeek(progress -> {
            prefs.edit().putInt(Prefs.CLOCK_DEPTH, progress).apply();
            notifyWallpaperRefresh();
        }));
        root.addView(clockDepth);

        root.addView(section("SUAS FOTOS"), marginTop(22));
        root.addView(muted(selectedUris().size() + " imagem(ns) importada(s)"), marginTop(8));
        Button pick = button("Importar fotos do celular");
        pick.setOnClickListener(v -> pickImages());
        root.addView(pick, marginTop(8));
        Button useMine = button("Usar minhas fotos importadas");
        useMine.setOnClickListener(v -> useUserPhotos());
        root.addView(useMine, marginTop(8));

        root.addView(section("TROCA AUTOMÁTICA"), marginTop(22));
        CheckBox auto = checkbox("Alternar automaticamente wallpapers offline / fotos", prefs.getBoolean(Prefs.AUTO, false));
        auto.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(Prefs.AUTO, checked).apply();
            scheduleAutoChange(checked);
        });
        root.addView(auto, marginTop(8));
        root.addView(muted("Padrão: 60 minutos. Wallpapers online selecionados não são trocados até serem baixados."), marginTop(4));

        root.addView(section("AJUSTE E APLICAÇÃO"), marginTop(22));
        Button adjust = button("Ajustar wallpaper com arrastar e pinçar");
        adjust.setOnClickListener(v -> startActivity(new Intent(this, WallpaperAdjustActivity.class)));
        root.addView(adjust, marginTop(8));
        Button apply = button("Aplicar Live Wallpaper");
        apply.setOnClickListener(v -> openWallpaperPreview());
        root.addView(apply, marginTop(8));

        root.addView(section("ATUALIZAÇÕES"), marginTop(22));
        Button catalog = button("Buscar novos wallpapers");
        catalog.setOnClickListener(v -> refreshCatalog(true));
        root.addView(catalog, marginTop(8));
        Button telegram = button("Entrar no canal @iDepth26Wallpapers");
        telegram.setOnClickListener(v -> openTelegram());
        root.addView(telegram, marginTop(8));

        Button about = button("Sobre o app");
        about.setOnClickListener(v -> showAbout());
        root.addView(about, marginTop(20));
        setPage(root);
    }

    private View buildBuiltinGallery() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(row);
        for (int i = 0; i < BuiltInWallpapers.count(); i++) {
            final int index = i;
            LinearLayout card = smallCard();
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(124), ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.rightMargin = dp(10);
            row.addView(card, cp);
            ImageView image = new ImageView(this);
            image.setImageResource(BuiltInWallpapers.THUMBNAILS[index]);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(198)));
            TextView name = text(BuiltInWallpapers.NAMES[index], 12, false);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(1);
            card.addView(name, marginTop(5));
            Button fav = miniButton(isFavorite("builtin:" + index) ? "♥" : "♡");
            fav.setOnClickListener(v -> {
                toggleFavorite("builtin:" + index);
                fav.setText(isFavorite("builtin:" + index) ? "♥" : "♡");
            });
            card.addView(fav);
            View.OnClickListener choose = v -> selectBuiltin(index);
            image.setOnClickListener(choose);
            name.setOnClickListener(choose);
        }
        return hsv;
    }

    private View buildRemoteGallery(List<RemoteWallpaper> items) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(row);
        for (RemoteWallpaper item : items) {
            LinearLayout card = smallCard();
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(132), ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.rightMargin = dp(10);
            row.addView(card, cp);
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.rgb(24, 28, 40));
            card.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));
            loadThumbnail(image, item);
            TextView name = text(item.name, 12, false);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            card.addView(name, marginTop(5));
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button use = miniButton("Usar");
            use.setOnClickListener(v -> selectRemote(item));
            actions.addView(use, weight());
            Button fav = miniButton(isFavorite("remote:" + item.id) ? "♥" : "♡");
            fav.setOnClickListener(v -> {
                toggleFavorite("remote:" + item.id);
                fav.setText(isFavorite("remote:" + item.id) ? "♥" : "♡");
            });
            actions.addView(fav, weight());
            card.addView(actions);
            image.setOnClickListener(v -> selectRemote(item));
        }
        return hsv;
    }

    private View buildRemoteListCard(RemoteWallpaper item) {
        LinearLayout card = largeCard();
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(24, 28, 40));
        card.addView(image, new LinearLayout.LayoutParams(dp(108), dp(170)));
        loadThumbnail(image, item);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), dp(6), 0, dp(4));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        card.addView(info, ip);
        info.addView(text(item.name, 16, true));
        TextView cat = muted(item.category + (item.isNew ? " • NOVO" : ""));
        info.addView(cat, marginTop(4));
        Button use = button("Baixar e usar");
        use.setOnClickListener(v -> selectRemote(item));
        info.addView(use, marginTop(10));
        Button fav = button(isFavorite("remote:" + item.id) ? "♥ Remover dos favoritos" : "♡ Favoritar");
        fav.setOnClickListener(v -> {
            toggleFavorite("remote:" + item.id);
            fav.setText(isFavorite("remote:" + item.id) ? "♥ Remover dos favoritos" : "♡ Favoritar");
        });
        info.addView(fav, marginTop(6));
        return card;
    }

    private View buildBuiltinListCard(int index) {
        LinearLayout card = largeCard();
        ImageView image = new ImageView(this);
        image.setImageResource(BuiltInWallpapers.THUMBNAILS[index]);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(image, new LinearLayout.LayoutParams(dp(108), dp(170)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), dp(6), 0, dp(4));
        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        info.addView(text(BuiltInWallpapers.NAMES[index], 16, true));
        info.addView(muted("Offline Depth • camadas locais"), marginTop(4));
        Button use = button("Usar");
        use.setOnClickListener(v -> selectBuiltin(index));
        info.addView(use, marginTop(10));
        return card;
    }

    private void addCategoryCard(LinearLayout root, String category, int count, RemoteWallpaper cover) {
        LinearLayout card = largeCard();
        if (cover != null) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.rgb(24, 28, 40));
            card.addView(image, new LinearLayout.LayoutParams(dp(92), dp(122)));
            loadThumbnail(image, cover);
        } else {
            ImageView image = new ImageView(this);
            image.setImageResource(BuiltInWallpapers.THUMBNAILS[0]);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(image, new LinearLayout.LayoutParams(dp(92), dp(122)));
        }
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14), dp(14), 0, dp(8));
        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        info.addView(text(category, 18, true));
        info.addView(muted(count + " wallpaper(s)"), marginTop(5));
        Button open = button("Abrir categoria");
        open.setOnClickListener(v -> showCategory(category));
        info.addView(open, marginTop(8));
        card.setOnClickListener(v -> showCategory(category));
        root.addView(card, marginTop(12));
    }

    private Map<String, List<RemoteWallpaper>> groupByCategory() {
        List<RemoteWallpaper> sorted = new ArrayList<>(remote);
        Collections.sort(sorted, Comparator.comparing(w -> w.category));
        Map<String, List<RemoteWallpaper>> grouped = new LinkedHashMap<>();
        for (RemoteWallpaper w : sorted) grouped.computeIfAbsent(w.category, k -> new ArrayList<>()).add(w);
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
        else if ("favorites".equals(page)) showFavorites();
        else showSettings();
    }

    private void loadThumbnail(ImageView image, RemoteWallpaper item) {
        io.execute(() -> {
            Bitmap bitmap = RemoteAssetStore.loadThumbnail(this, item);
            if (bitmap != null) runOnUiThread(() -> {
                if (!isFinishing() && image.getWindowToken() != null) image.setImageBitmap(bitmap);
                else image.setImageBitmap(bitmap);
            });
        });
    }

    private void selectRemote(RemoteWallpaper item) {
        Toast.makeText(this, "Baixando " + item.name + "...", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                RemoteAssetStore.selectAndDownload(this, item);
                runOnUiThread(() -> {
                    notifyWallpaperRefresh();
                    Toast.makeText(this, item.name + " pronto para usar.", Toast.LENGTH_SHORT).show();
                    showHome();
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
                .apply();
        notifyWallpaperRefresh();
        Toast.makeText(this, "Selecionado: " + BuiltInWallpapers.NAMES[index], Toast.LENGTH_SHORT).show();
    }

    private String currentSourceText() {
        String source = prefs.getString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN);
        if (Prefs.SOURCE_REMOTE.equals(source)) {
            String name = prefs.getString(Prefs.REMOTE_NAME, "Wallpaper online");
            return "Tema atual: " + name + " • online salvo";
        }
        if (Prefs.SOURCE_USER.equals(source) && !selectedUris().isEmpty()) return "Fonte atual: suas fotos";
        int i = Math.floorMod(prefs.getInt(Prefs.BUILTIN_INDEX, 0), BuiltInWallpapers.count());
        return "Tema atual: " + BuiltInWallpapers.NAMES[i] + " • offline";
    }

    private Set<String> favorites() {
        Set<String> set = prefs.getStringSet(Prefs.FAVORITES, new LinkedHashSet<>());
        return new LinkedHashSet<>(set == null ? new LinkedHashSet<>() : set);
    }

    private boolean isFavorite(String key) { return favorites().contains(key); }

    private void toggleFavorite(String key) {
        Set<String> set = favorites();
        if (!set.add(key)) set.remove(key);
        prefs.edit().putStringSet(Prefs.FAVORITES, set).apply();
    }

    private void pickImages() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_IMAGES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGES || resultCode != RESULT_OK || data == null) return;
        Set<String> uris = new LinkedHashSet<>(selectedUris());
        if (data.getData() != null) addUri(data.getData(), uris);
        ClipData clip = data.getClipData();
        if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) addUri(clip.getItemAt(i).getUri(), uris);
        prefs.edit().putStringSet(Prefs.IMAGE_URIS, uris).putString(Prefs.SOURCE, Prefs.SOURCE_USER).apply();
        notifyWallpaperRefresh();
        Toast.makeText(this, "Fotos importadas e selecionadas.", Toast.LENGTH_SHORT).show();
        showSettings();
    }

    private void addUri(Uri uri, Set<String> uris) {
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) {}
        uris.add(uri.toString());
    }

    private List<String> selectedUris() {
        Set<String> set = prefs.getStringSet(Prefs.IMAGE_URIS, new LinkedHashSet<>());
        return new ArrayList<>(set == null ? new LinkedHashSet<>() : set);
    }

    private void useUserPhotos() {
        if (selectedUris().isEmpty()) {
            Toast.makeText(this, "Importe pelo menos uma foto.", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putString(Prefs.SOURCE, Prefs.SOURCE_USER).apply();
        notifyWallpaperRefresh();
        Toast.makeText(this, "Suas fotos estão ativas.", Toast.LENGTH_SHORT).show();
    }

    private void openTelegram() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/iDepth26Wallpapers"))); }
        catch (Exception e) { Toast.makeText(this, "Não foi possível abrir o Telegram.", Toast.LENGTH_SHORT).show(); }
    }

    private void openWallpaperPreview() {
        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new android.content.ComponentName(this, DepthWallpaperService.class));
        try { startActivity(intent); }
        catch (Exception e) { startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)); }
    }

    private void notifyWallpaperRefresh() {
        sendBroadcast(new Intent(DepthWallpaperService.ACTION_REFRESH).setPackage(getPackageName()));
    }

    private void scheduleAutoChange(boolean enabled) {
        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(this, AutoChangeReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, 99, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (!enabled) { alarm.cancel(pi); return; }
        long minutes = prefs.getLong(Prefs.AUTO_MINUTES, 60L);
        long interval = Math.max(15L, minutes) * 60_000L;
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval, interval, pi);
    }

    private void showAbout() {
        String msg = "Ass.: " + getString(R.string.developer_name)
                + "\nVersão: 1.4.0"
                + "\n\n" + BuiltInWallpapers.count() + " wallpapers offline, catálogo remoto, categorias, favoritos, canal Telegram, cache offline, ajuste por arrastar/pinçar, parallax e relógio em profundidade."
                + "\n\nProduto independente; não é afiliado nem oficial da Apple.";
        new android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name)).setMessage(msg).setPositiveButton("OK", null).show();
    }

    private LinearLayout pageRoot() {
        int pad = dp(18);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(5, 7, 12));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setTag(scroll);
        return root;
    }

    private void setPage(LinearLayout root) {
        View tagged = (View) root.getTag();
        content.removeAllViews();
        content.addView(tagged == null ? root : tagged, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void addHeader(LinearLayout root, String titleText, String subtitleText) {
        TextView title = text(titleText, 29, true);
        root.addView(title);
        TextView subtitle = muted(subtitleText);
        subtitle.setTextSize(14);
        root.addView(subtitle, marginTop(5));
    }

    private LinearLayout smallCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(4), dp(4), dp(4), dp(6));
        card.setBackground(cardBackground());
        return card;
    }

    private LinearLayout largeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(cardBackground());
        return card;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(16, 20, 31));
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1), Color.rgb(45, 50, 70));
        return g;
    }

    private TextView section(String s) {
        TextView v = text(s, 13, true);
        v.setTextColor(Color.rgb(149, 137, 255));
        return v;
    }

    private TextView muted(String s) {
        TextView v = text(s, 13, false);
        v.setTextColor(Color.rgb(150, 156, 172));
        return v;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(Color.WHITE);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(14);
        return b;
    }

    private Button miniButton(String s) {
        Button b = button(s);
        b.setTextSize(12);
        b.setMinHeight(0);
        b.setPadding(dp(4), dp(3), dp(4), dp(3));
        return b;
    }

    private CheckBox checkbox(String s, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextColor(Color.WHITE);
        c.setTextSize(15);
        c.setChecked(checked);
        return c;
    }

    private SeekBar.OnSeekBarChangeListener simpleSeek(IntConsumer consumer) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) consumer.accept(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }

    private LinearLayout.LayoutParams marginTop(int value) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(value);
        return p;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private interface IntConsumer { void accept(int value); }
}
