package com.nickwoluff.wearcalculator;

import android.content.Context;

/** 高精度结果显示设置。 */
final class PrecisionPreferences {
    private PrecisionPreferences() { }

    static boolean enabled(Context context) {
        return context.getSharedPreferences("calculator_preferences", Context.MODE_PRIVATE)
                .getBoolean("high_precision", false);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences("calculator_preferences", Context.MODE_PRIVATE).edit()
                .putBoolean("high_precision", enabled).apply();
    }
}
