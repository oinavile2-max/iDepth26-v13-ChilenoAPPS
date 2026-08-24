package com.chilenoapps.idepth26;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class VipManager implements PurchasesUpdatedListener, BillingClientStateListener {
    interface Listener {
        void onReady(List<ProductDetails> products);
        void onMessage(String message);
        void onVipChanged(boolean active);
    }

    interface AdminVipCallback {
        void onResult(boolean changed);
    }

    private static final String VIP_ACTIVE_LEGACY = "vip_active";
    private static final String VIP_BILLING_ACTIVE = "vip_billing_active";
    private static final String VIP_PRODUCT = "vip_product";
    private static final String VIP_ADMIN_ACTIVE = "vip_admin_active";
    private static final String VIP_ADMIN_EXPIRES = "vip_admin_expires";
    private static final String VIP_ADMIN_LAST_SYNC = "vip_admin_last_sync";
    private static final ExecutorService ADMIN_IO = Executors.newSingleThreadExecutor();

    private final Context context;
    private final Listener listener;
    private final BillingClient billingClient;
    private List<ProductDetails> products = new ArrayList<>();

    VipManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        migrateLegacyState(this.context);
        billingClient = BillingClient.newBuilder(this.context)
                .setListener(this)
                .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder()
                                .enableOneTimeProducts()
                                .build())
                .enableAutoServiceReconnection()
                .build();
    }

    void start() {
        refreshAdminVip(context, changed -> {
            if (changed && listener != null) listener.onVipChanged(isVip(context));
        });
        if (billingClient.isReady()) {
            queryProducts();
            restorePurchases();
        } else {
            billingClient.startConnection(this);
        }
    }

    void stop() {
        try { billingClient.endConnection(); } catch (Exception ignored) {}
    }

    static boolean isVip(Context context) {
        migrateLegacyState(context);
        SharedPreferences p = Prefs.get(context);
        boolean billing = p.getBoolean(VIP_BILLING_ACTIVE, false);
        boolean admin = p.getBoolean(VIP_ADMIN_ACTIVE, false);
        long expires = p.getLong(VIP_ADMIN_EXPIRES, 0L);
        if (admin && expires > 0L && System.currentTimeMillis() >= expires) {
            p.edit().putBoolean(VIP_ADMIN_ACTIVE, false).apply();
            admin = false;
        }
        return billing || admin;
    }

    static boolean isAdminVip(Context context) {
        SharedPreferences p = Prefs.get(context);
        boolean admin = p.getBoolean(VIP_ADMIN_ACTIVE, false);
        long expires = p.getLong(VIP_ADMIN_EXPIRES, 0L);
        return admin && (expires <= 0L || System.currentTimeMillis() < expires);
    }

    static String vipSource(Context context) {
        SharedPreferences p = Prefs.get(context);
        if (isAdminVip(context)) return "ADMIN";
        if (p.getBoolean(VIP_BILLING_ACTIVE, false)) return "GOOGLE PLAY";
        return "FREE";
    }

    static void refreshAdminVip(Context context, AdminVipCallback callback) {
        Context app = context.getApplicationContext();
        ADMIN_IO.execute(() -> {
            boolean changed = false;
            try {
                String installId = UsageLogger.installId(app);
                JSONObject body = new JSONObject().put("install_id", installId);
                JSONObject result = postJson(AppConfig.VIP_STATUS_URL, body);
                if (result.optBoolean("ok", false)) {
                    boolean active = result.optBoolean("admin_vip", false);
                    long expires = parseExpires(result.optString("expires_at", ""));
                    SharedPreferences p = Prefs.get(app);
                    boolean before = isAdminVip(app);
                    p.edit()
                            .putBoolean(VIP_ADMIN_ACTIVE, active)
                            .putLong(VIP_ADMIN_EXPIRES, expires)
                            .putLong(VIP_ADMIN_LAST_SYNC, System.currentTimeMillis())
                            .apply();
                    changed = before != active;
                }
            } catch (Exception ignored) {}
            if (callback != null) callback.onResult(changed);
        });
    }

    static void setAdminOverrideLocal(Context context, boolean enabled, long expiresAtMillis) {
        Prefs.get(context).edit()
                .putBoolean(VIP_ADMIN_ACTIVE, enabled)
                .putLong(VIP_ADMIN_EXPIRES, enabled ? Math.max(0L, expiresAtMillis) : 0L)
                .putLong(VIP_ADMIN_LAST_SYNC, System.currentTimeMillis())
                .apply();
    }

    private static void migrateLegacyState(Context context) {
        SharedPreferences p = Prefs.get(context.getApplicationContext());
        if (!p.contains(VIP_BILLING_ACTIVE) && p.contains(VIP_ACTIVE_LEGACY)) {
            p.edit().putBoolean(VIP_BILLING_ACTIVE, p.getBoolean(VIP_ACTIVE_LEGACY, false)).apply();
        }
    }

    private static JSONObject postJson(String endpoint, JSONObject body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(9000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
        }
        conn.disconnect();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
        return new JSONObject(sb.toString());
    }

    private static long parseExpires(String iso) {
        if (iso == null || iso.trim().isEmpty() || "null".equalsIgnoreCase(iso)) return 0L;
        try {
            return java.time.Instant.parse(iso).toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    @Override
    public void onBillingSetupFinished(BillingResult billingResult) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            queryProducts();
            restorePurchases();
        } else if (listener != null) {
            listener.onMessage("Google Play indisponível: " + billingResult.getDebugMessage());
        }
    }

    @Override
    public void onBillingServiceDisconnected() {
        if (listener != null) listener.onMessage("Conexão com Google Play temporariamente indisponível.");
    }

    private void queryProducts() {
        ArrayList<QueryProductDetailsParams.Product> list = new ArrayList<>();
        list.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(AppConfig.VIP_MONTHLY_PRODUCT)
                .setProductType(BillingClient.ProductType.SUBS)
                .build());
        list.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(AppConfig.VIP_ANNUAL_PRODUCT)
                .setProductType(BillingClient.ProductType.SUBS)
                .build());

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(list)
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, result) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                products = new ArrayList<>(result.getProductDetailsList());
                if (listener != null) listener.onReady(Collections.unmodifiableList(products));
            } else if (listener != null) {
                listener.onMessage("Não foi possível carregar os planos.");
            }
        });
    }

    void launch(Activity activity, String productId) {
        ProductDetails selected = null;
        for (ProductDetails product : products) {
            if (productId.equals(product.getProductId())) {
                selected = product;
                break;
            }
        }
        if (selected == null) {
            if (listener != null) listener.onMessage("Plano ainda não disponível na Google Play.");
            return;
        }

        BillingFlowParams.ProductDetailsParams.Builder details =
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(selected);

        List<ProductDetails.SubscriptionOfferDetails> offers = selected.getSubscriptionOfferDetails();
        if (offers != null && !offers.isEmpty()) {
            ProductDetails.SubscriptionOfferDetails chosen = chooseOffer(offers, productId);
            details.setOfferToken(chosen.getOfferToken());
        }

        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(details.build()))
                .build();

        BillingResult result = billingClient.launchBillingFlow(activity, params);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK && listener != null) {
            listener.onMessage(result.getDebugMessage());
        }
    }

    private ProductDetails.SubscriptionOfferDetails chooseOffer(
            List<ProductDetails.SubscriptionOfferDetails> offers, String productId) {
        if (AppConfig.VIP_ANNUAL_PRODUCT.equals(productId)) {
            for (ProductDetails.SubscriptionOfferDetails offer : offers) {
                List<ProductDetails.PricingPhase> phases = offer.getPricingPhases().getPricingPhaseList();
                for (ProductDetails.PricingPhase phase : phases) {
                    if (phase.getPriceAmountMicros() == 0L) return offer;
                }
            }
        }
        return offers.get(0);
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) handlePurchase(purchase);
        } else if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.USER_CANCELED
                && listener != null) {
            listener.onMessage(billingResult.getDebugMessage());
        }
    }

    private void restorePurchases() {
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build();

        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            boolean active = false;
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                for (Purchase purchase : purchases) {
                    if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                        active = true;
                        handlePurchase(purchase);
                    }
                }
            }
            if (!active) setBillingVip(false, "");
        });
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return;
        String productId = purchase.getProducts().isEmpty() ? "" : purchase.getProducts().get(0);

        if (!purchase.isAcknowledged()) {
            AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build();
            billingClient.acknowledgePurchase(params, result -> {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    setBillingVip(true, productId);
                    UsageLogger.event(context, "vip_purchase", productId);
                }
            });
        } else {
            setBillingVip(true, productId);
        }
    }

    private void setBillingVip(boolean active, String productId) {
        SharedPreferences.Editor edit = Prefs.get(context).edit()
                .putBoolean(VIP_BILLING_ACTIVE, active)
                .putBoolean(VIP_ACTIVE_LEGACY, active)
                .putString(VIP_PRODUCT, productId == null ? "" : productId);
        edit.apply();
        if (listener != null) listener.onVipChanged(isVip(context));
    }
}
