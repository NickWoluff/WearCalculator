package com.nickwoluff.wearcalculator;

import android.content.Context;

/** 与 Credex 一致，默认开启，修改后持久保存。 */
final class UpdatePreferences {
    private UpdatePreferences() { }

    static boolean autoCheck(Context context) {
        return context.getSharedPreferences("update_preferences", Context.MODE_PRIVATE)
                .getBoolean("auto_check", true);
    }

    static void setAutoCheck(Context context, boolean enabled) {
        context.getSharedPreferences("update_preferences", Context.MODE_PRIVATE).edit()
                .putBoolean("auto_check", enabled).apply();
    }
}
