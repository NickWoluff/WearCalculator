package com.nickwoluff.wearcalculator

import android.content.Context
import android.text.format.DateFormat
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import java.util.Locale

/** XML 页面的官方 TimeText 覆盖层，触摸穿透，不参与内容高度测量。 */
class FloatingTimeView(context: Context) : FrameLayout(context) {
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        isFocusable = false
        addView(ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { CalculatorTheme { FloatingTime() } }
        }, LayoutParams(-1, -1))
    }

    fun getTimeText(): String {
        val skeleton = if (DateFormat.is24HourFormat(context)) "HHmm" else "hmm"
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton).replace("a", "").trim()
        return DateFormat.format(pattern, System.currentTimeMillis()).toString()
    }

    override fun dispatchTouchEvent(event: MotionEvent) = false
}
