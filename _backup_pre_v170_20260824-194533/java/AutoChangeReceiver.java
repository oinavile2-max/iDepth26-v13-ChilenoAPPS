package com.chilenoapps.idepth26;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AutoChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = Prefs.get(context);
        if (!prefs.getBoolean(Prefs.AUTO, false)) return;

        String source = prefs.getString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN);
        if (Prefs.SOURCE_USER.equals(source)) {
            Set<String> set = prefs.getStringSet(Prefs.IMAGE_URIS, new LinkedHashSet<>());
            if (set == null || set.size() < 2) return;
            List<String> uris = new ArrayList<>(set);
            int index = (prefs.getInt(Prefs.CURRENT_INDEX, 0) + 1) % uris.size();
            prefs.edit().putInt(Prefs.CURRENT_INDEX, index).apply();
        } else {
            int index = (prefs.getInt(Prefs.BUILTIN_INDEX, 0) + 1) % BuiltInWallpapers.count();
            prefs.edit()
                    .putString(Prefs.SOURCE, Prefs.SOURCE_BUILTIN)
                    .putInt(Prefs.BUILTIN_INDEX, index)
                    .apply();
        }

        context.sendBroadcast(new Intent(DepthWallpaperService.ACTION_REFRESH)
                .setPackage(context.getPackageName()));
    }
}
