package com.chilenoapps.idepth26;

final class AppConfig {
    static final String SUPABASE_FUNCTIONS =
            "https://eyxwgmcxullybhsqboqh.supabase.co/functions/v1/";

    static final String USAGE_LOG_URL = SUPABASE_FUNCTIONS + "usage-log";
    static final String ADMIN_AUTH_URL = SUPABASE_FUNCTIONS + "admin-auth";
    static final String ADMIN_DASHBOARD_URL = SUPABASE_FUNCTIONS + "admin-dashboard";
    static final String USER_SUBMISSION_URL = SUPABASE_FUNCTIONS + "user-wallpaper-submit";
    static final String VIP_STATUS_URL = SUPABASE_FUNCTIONS + "vip-status";

    static final String VIP_MONTHLY_PRODUCT = "idepth_vip_monthly";
    static final String VIP_ANNUAL_PRODUCT = "idepth_vip_annual";

    static final String VERSION_NAME = "1.7.0";

    private AppConfig() {}
}
