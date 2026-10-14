package com.nickwoluff.wearcalculator

import android.app.Activity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag

enum class NoticeKind { INFO, SUCCESS, ERROR }
data class WatchNotice(val id: Int, val message: String, val kind: NoticeKind)

/** 为 Java 设置页承载从 WearFiles 原样移植的提示组件。 */
class ArcToastView(activity: Activity) : FrameLayout(activity) {
    private val host = activity.window.decorView as ViewGroup
    private var notice by mutableStateOf<WatchNotice?>(null)
    private var nextId = 0

    init {
        tag = "arc_toast"
        isClickable = false
        isFocusable = false
        // 无障碍播报由 WearFiles 组件的 liveRegion 处理。
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                notice?.let { current ->
                    key(current.id) {
                        Box(Modifier.fillMaxSize().testTag("notice")) {
                            WatchFloatingNotice(current) {
                                if (notice?.id == current.id) dismiss()
                            }
                        }
                    }
                }
            }
        }, LayoutParams(-1, -1))
    }

    fun show(value: String?) {
        dismiss()
        if (value.isNullOrEmpty()) return
        contentDescription = value
        notice = WatchNotice(++nextId, value, NoticeKind.INFO)
        host.addView(this, LayoutParams(-1, -1))
    }

    fun dismiss() {
        notice = null
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun dispatchTouchEvent(event: MotionEvent) = false
}
