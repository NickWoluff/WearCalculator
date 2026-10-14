package com.nickwoluff.wearcalculator;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** 首次启动记录设备形状，之后使用用户保存的布局。 */
final class LayoutPreferences {
    private static final String FILE = "screen_layout";
    private static final String ROUND = "round";

    private LayoutPreferences() { }

    static boolean isRound(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        if (!preferences.contains(ROUND)) {
            boolean detected = context.getResources().getConfiguration().isScreenRound();
            preferences.edit().putBoolean(ROUND, detected).apply();
            return detected;
        }
        return preferences.getBoolean(ROUND, false);
    }

    static void setRound(Context context, boolean round) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(ROUND, round).apply();
    }

    static Context applyTo(Context context) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.screenLayout = (configuration.screenLayout & ~Configuration.SCREENLAYOUT_ROUND_MASK)
                | (isRound(context) ? Configuration.SCREENLAYOUT_ROUND_YES : Configuration.SCREENLAYOUT_ROUND_NO);
        return context.createConfigurationContext(configuration);
    }
}
