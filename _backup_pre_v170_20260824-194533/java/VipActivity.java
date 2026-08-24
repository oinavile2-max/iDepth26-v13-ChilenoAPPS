package com.chilenoapps.idepth26;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.billingclient.api.ProductDetails;

import java.util.List;

public class VipActivity extends Activity implements VipManager.Listener {
    private VipManager billing;
    private Button monthly;
    private Button annual;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(36));
        root.setBackgroundColor(0xFF050505);

        TextView title = text("iDepth VIP 👑", 30, true);
        root.addView(title);

        TextView trial = text("7 dias grátis no plano anual", 16, true);
        trial.setTextColor(0xFFFFD400);
        root.addView(trial, top(10));

        status = text(VipManager.isVip(this) ? "VIP ativo • " + VipManager.vipSource(this) : "Escolha seu plano", 13, false);
        status.setTextColor(0xFFA8A8A8);
        root.addView(status, top(6));

        monthly = planButton("Mensal\nR$ 9,90 / mês");
        monthly.setOnClickListener(v -> billing.launch(this, AppConfig.VIP_MONTHLY_PRODUCT));
        root.addView(monthly, top(22));

        annual = planButton("ANUAL — MELHOR VALOR\nR$ 59,90 / ano\n≈ R$ 5,00 / mês");
        annual.setOnClickListener(v -> billing.launch(this, AppConfig.VIP_ANNUAL_PRODUCT));
        root.addView(annual, top(10));

        String[] benefits = {
                "✓ Sem anúncios",
                "✓ Wallpapers VIP",
                "✓ Profundidade real",
                "✓ Relógio atrás do assunto",
                "✓ Todas as fontes",
                "✓ Preview em tempo real",
                "✓ Paleta dinâmica"
        };
        for (String benefit : benefits) {
            TextView t = text(benefit, 16, false);
            t.setTextColor(0xFFF0F0F0);
            root.addView(t, top(12));
        }

        Button free = planButton("Continuar grátis");
        free.setOnClickListener(v -> finish());
        root.addView(free, top(28));

        scroll.addView(root);
        setContentView(scroll);

        UsageLogger.event(this, "vip_screen", "");
        billing = new VipManager(this, this);
        billing.start();
    }

    @Override
    protected void onDestroy() {
        if (billing != null) billing.stop();
        super.onDestroy();
    }

    @Override
    public void onReady(List<ProductDetails> products) {
        runOnUiThread(() -> {
            for (ProductDetails p : products) {
                String price = bestPrice(p);
                if (price.isEmpty()) continue;
                if (AppConfig.VIP_MONTHLY_PRODUCT.equals(p.getProductId())) {
                    monthly.setText("Mensal\n" + price + " / mês");
                } else if (AppConfig.VIP_ANNUAL_PRODUCT.equals(p.getProductId())) {
                    annual.setText("ANUAL — MELHOR VALOR\n" + price + " / ano\n7 dias grátis quando elegível");
                }
            }
        });
    }

    private String bestPrice(ProductDetails product) {
        List<ProductDetails.SubscriptionOfferDetails> offers = product.getSubscriptionOfferDetails();
        if (offers == null || offers.isEmpty()) return "";
        for (ProductDetails.SubscriptionOfferDetails offer : offers) {
            List<ProductDetails.PricingPhase> phases = offer.getPricingPhases().getPricingPhaseList();
            for (int i = phases.size() - 1; i >= 0; i--) {
                ProductDetails.PricingPhase phase = phases.get(i);
                if (phase.getPriceAmountMicros() > 0) return phase.getFormattedPrice();
            }
        }
        return "";
    }

    @Override public void onMessage(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    @Override public void onVipChanged(boolean active) {
        runOnUiThread(() -> status.setText(active ? "VIP ativo ✓ • " + VipManager.vipSource(this) : "Escolha seu plano"));
    }

    private Button planButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(18), dp(14), dp(18), dp(14));
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(0xFF151515);
        d.setCornerRadius(dp(22));
        d.setStroke(dp(1), 0xFFFFD400);
        b.setBackground(d);
        return b;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.WHITE);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams top(int value) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(value);
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
