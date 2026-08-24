package com.chilenoapps.idepth26;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminActivity extends Activity {
    private static final int BG = 0xFF050505;
    private static final int SURFACE = 0xFF111111;
    private static final int SURFACE_ALT = 0xFF191919;
    private static final int YELLOW = 0xFFFFD400;
    private static final int MUTED = 0xFFA8A8A8;
    private static final int BORDER = 0xFF2C2C2C;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private String sessionToken = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        showLogin();
        UsageLogger.event(this, "admin_open", "");
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void showLogin() {
        root = baseRoot();

        TextView title = text("Acesso Administrativo", 28, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView shield = text("◆", 58, true);
        shield.setTextColor(YELLOW);
        shield.setGravity(Gravity.CENTER);
        root.addView(shield, top(16));

        EditText login = input("CPF");
        login.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(login, top(24));

        EditText password = input("Senha");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(password, top(10));

        Button enter = button("Entrar no Painel Admin", true);
        enter.setOnClickListener(v -> {
            enter.setEnabled(false);
            io.execute(() -> {
                try {
                    JSONObject result = AdminGateway.login(login.getText().toString(), password.getText().toString());
                    boolean ok = result.optBoolean("ok", false);
                    String token = result.optString("token", "");
                    runOnUiThread(() -> {
                        enter.setEnabled(true);
                        if (ok && !token.isEmpty()) {
                            sessionToken = token;
                            UsageLogger.event(this, "admin_login", "success");
                            loadDashboard();
                        } else {
                            Toast.makeText(this, result.optString("error", "Credenciais inválidas."), Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        enter.setEnabled(true);
                        Toast.makeText(this, "Falha no login: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
        root.addView(enter, top(18));

        TextView note = text("A credencial é validada no Supabase e não fica salva no APK.", 12, false);
        note.setTextColor(MUTED);
        note.setGravity(Gravity.CENTER);
        root.addView(note, top(14));

        setScroll(root);
    }

    private void loadDashboard() {
        root = baseRoot();
        root.addView(text("Painel Admin", 29, true));
        TextView loading = text("Carregando dados...", 14, false);
        loading.setTextColor(MUTED);
        root.addView(loading, top(8));
        setScroll(root);

        io.execute(() -> {
            try {
                JSONObject data = AdminGateway.dashboard(sessionToken);
                runOnUiThread(() -> {
                    if (!data.optBoolean("ok", false)) {
                        if (data.optInt("_http", 0) == 401) {
                            sessionToken = "";
                            Toast.makeText(this, "Sessão expirada. Entre novamente.", Toast.LENGTH_LONG).show();
                            showLogin();
                        } else {
                            Toast.makeText(this, data.optString("error", "Falha no painel."), Toast.LENGTH_LONG).show();
                        }
                        return;
                    }
                    renderDashboard(data);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Falha no painel: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void renderDashboard(JSONObject data) {
        root.removeAllViews();
        root.addView(text("iDepth 26 • Painel Admin", 27, true));

        TextView currentId = muted("Este aparelho: " + shortId(UsageLogger.installId(this)));
        root.addView(currentId, top(5));

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.VERTICAL);
        metrics.addView(metric("Usuários totais", data.optInt("users_total", 0)));
        metrics.addView(metric("Ativos 24h", data.optInt("users_24h", 0)));
        metrics.addView(metric("Wallpapers aplicados", data.optInt("wallpapers_applied", 0)));
        metrics.addView(metric("VIP via loja", data.optInt("vip_total", 0)));
        metrics.addView(metric("VIP liberado pelo admin", data.optInt("vip_admin_total", 0)));
        metrics.addView(metric("Envios aguardando", data.optInt("submissions_pending", 0)));
        root.addView(metrics, top(16));

        root.addView(section("Acesso VIP"));
        LinearLayout currentVip = card();
        currentVip.addView(text("VIP deste aparelho", 16, true));
        currentVip.addView(muted("Use para testes, suporte ou acesso promocional."), top(3));
        LinearLayout currentActions = new LinearLayout(this);
        currentActions.setOrientation(LinearLayout.HORIZONTAL);
        Button on = miniButton("Ativar VIP");
        on.setOnClickListener(v -> setVip(UsageLogger.installId(this), true, 0, true));
        currentActions.addView(on, weight());
        Button off = miniButton("Desativar");
        off.setOnClickListener(v -> setVip(UsageLogger.installId(this), false, 0, true));
        currentActions.addView(off, weight());
        currentVip.addView(currentActions, top(8));
        root.addView(currentVip, top(8));

        renderUsers(data.optJSONArray("users"));

        root.addView(section("Atividade recente"));
        root.addView(jsonList(data.optJSONArray("recent_events"), "event", "details"));

        root.addView(section("Wallpapers enviados por usuários"));
        renderSubmissions(data.optJSONArray("submissions"));

        root.addView(section("Personalizações recentes"));
        root.addView(jsonList(data.optJSONArray("personalizations"), "event", "details"));

        root.addView(section("Envios por e-mail"));
        root.addView(jsonList(data.optJSONArray("emails"), "status", "subject"));

        Button refresh = button("Atualizar painel", true);
        refresh.setOnClickListener(v -> loadDashboard());
        root.addView(refresh, top(20));

        Button exit = button("Sair", false);
        exit.setOnClickListener(v -> {
            sessionToken = "";
            showLogin();
        });
        root.addView(exit, top(10));
    }

    private void renderUsers(JSONArray users) {
        if (users == null || users.length() == 0) {
            root.addView(muted("Nenhum usuário registrado nos logs ainda."), top(8));
            return;
        }
        for (int i = 0; i < Math.min(users.length(), 30); i++) {
            JSONObject user = users.optJSONObject(i);
            if (user == null) continue;
            String installId = user.optString("install_id", "");
            boolean vip = user.optBoolean("vip_enabled", false);
            String version = user.optString("app_version", "");
            String lastSeen = prettyDate(user.optString("last_seen", ""));

            LinearLayout c = card();
            TextView title = text(shortId(installId) + (vip ? "   VIP ✓" : ""), 15, true);
            title.setTextColor(vip ? YELLOW : Color.WHITE);
            c.addView(title);
            c.addView(muted("Versão " + version + " • último uso " + lastSeen), top(3));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button enable = miniButton(vip ? "Renovar VIP" : "Ativar VIP");
            enable.setOnClickListener(v -> setVip(installId, true, 0, false));
            actions.addView(enable, weight());
            Button disable = miniButton("Desativar");
            disable.setOnClickListener(v -> setVip(installId, false, 0, false));
            actions.addView(disable, weight());
            c.addView(actions, top(8));
            root.addView(c, top(8));
        }
    }

    private void setVip(String installId, boolean enabled, int days, boolean currentDevice) {
        if (installId == null || installId.isEmpty()) return;
        Toast.makeText(this, "Atualizando VIP...", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                JSONObject result = AdminGateway.setVip(sessionToken, installId, enabled, days);
                runOnUiThread(() -> {
                    if (result.optBoolean("ok", false)) {
                        if (currentDevice) {
                            long expires = 0L;
                            VipManager.setAdminOverrideLocal(this, enabled, expires);
                        }
                        Toast.makeText(this, enabled ? "VIP ativado." : "VIP desativado.", Toast.LENGTH_SHORT).show();
                        loadDashboard();
                    } else {
                        Toast.makeText(this, result.optString("error", "Falha ao alterar VIP."), Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Falha VIP: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void renderSubmissions(JSONArray array) {
        if (array == null || array.length() == 0) {
            root.addView(muted("Nenhum envio de usuário."), top(8));
            return;
        }

        for (int i = 0; i < Math.min(array.length(), 20); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id", "");
            String status = item.optString("status", "pending");
            String path = item.optString("storage_path", "");
            String previewUrl = item.optString("preview_url", "");

            LinearLayout c = card();
            TextView title = text(status.toUpperCase() + "\n" + path, 13, true);
            title.setTextColor(Color.WHITE);
            c.addView(title);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER);

            if (!previewUrl.isEmpty()) {
                Button view = miniButton("Ver");
                view.setOnClickListener(v -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(previewUrl))); }
                    catch (Exception e) { Toast.makeText(this, "Não foi possível abrir a prévia.", Toast.LENGTH_SHORT).show(); }
                });
                actions.addView(view, weight());
            }

            if ("pending".equals(status)) {
                Button approve = miniButton("Aprovar");
                approve.setOnClickListener(v -> runSubmissionAction(id, "approve"));
                actions.addView(approve, weight());

                Button reject = miniButton("Rejeitar");
                reject.setOnClickListener(v -> runSubmissionAction(id, "reject"));
                actions.addView(reject, weight());
            }

            Button email = miniButton("E-mail");
            email.setOnClickListener(v -> runSubmissionAction(id, "email"));
            actions.addView(email, weight());

            c.addView(actions, top(8));
            root.addView(c, top(8));
        }
    }

    private void runSubmissionAction(String id, String action) {
        if (id == null || id.isEmpty()) return;
        Toast.makeText(this, "Processando...", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                JSONObject result = AdminGateway.submissionAction(sessionToken, id, action);
                runOnUiThread(() -> {
                    if (result.optBoolean("ok", false)) {
                        Toast.makeText(this, result.optString("message", "Concluído."), Toast.LENGTH_SHORT).show();
                        loadDashboard();
                    } else {
                        Toast.makeText(this, result.optString("error", "Falha na ação."), Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Falha: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private TextView metric(String label, int value) {
        TextView t = text(label + "\n" + value, 18, true);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        t.setBackground(round(SURFACE, 20, true));
        t.setLayoutParams(top(8));
        return t;
    }

    private TextView section(String title) {
        TextView t = text(title, 19, true);
        t.setTextColor(YELLOW);
        t.setLayoutParams(top(22));
        return t;
    }

    private TextView jsonList(JSONArray array, String primary, String secondary) {
        StringBuilder sb = new StringBuilder();
        if (array == null || array.length() == 0) sb.append("Nenhum registro.");
        else {
            for (int i = 0; i < Math.min(array.length(), 12); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                sb.append("• ").append(item.optString(primary, "-"));
                String extra = item.optString(secondary, "");
                if (!extra.isEmpty()) sb.append(" — ").append(extra);
                sb.append("\n");
            }
        }
        TextView t = text(sb.toString().trim(), 13, false);
        t.setTextColor(0xFFD4D4D4);
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        t.setBackground(round(SURFACE, 18, false));
        t.setLayoutParams(top(8));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(13), dp(14), dp(13));
        c.setBackground(round(SURFACE, 20, true));
        return c;
    }

    private LinearLayout baseRoot() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(20), dp(22), dp(20), dp(36));
        view.setBackgroundColor(BG);
        return view;
    }

    private void setScroll(LinearLayout view) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(view);
        setContentView(scroll);
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF777777);
        e.setTextColor(Color.WHITE);
        e.setSingleLine(true);
        e.setPadding(dp(15), dp(12), dp(15), dp(12));
        e.setBackground(round(SURFACE_ALT, 16, true));
        return e;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.BLACK : Color.WHITE);
        b.setTextSize(15);
        b.setBackground(round(primary ? YELLOW : SURFACE_ALT, 20, !primary));
        return b;
    }

    private Button miniButton(String label) {
        Button b = button(label, false);
        b.setTextSize(11);
        b.setPadding(dp(7), dp(6), dp(7), dp(6));
        return b;
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radiusDp, boolean stroke) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (stroke) d.setStroke(dp(1), BORDER);
        return d;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(2);
        p.rightMargin = dp(2);
        return p;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.WHITE);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView muted(String value) {
        TextView t = text(value, 13, false);
        t.setTextColor(MUTED);
        return t;
    }

    private LinearLayout.LayoutParams top(int value) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(value);
        return p;
    }

    private String shortId(String value) {
        if (value == null || value.isEmpty()) return "sem-id";
        return value.length() <= 12 ? value : "…" + value.substring(value.length() - 12);
    }

    private String prettyDate(String iso) {
        if (iso == null || iso.isEmpty()) return "-";
        try {
            Date d = Date.from(java.time.Instant.parse(iso));
            return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(d);
        } catch (Exception ignored) {
            return iso.length() > 16 ? iso.substring(0, 16).replace('T', ' ') : iso;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
