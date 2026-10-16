package com.nickwoluff.wearcalculator

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.Window
import android.os.Build
import android.widget.Button
import android.widget.TextView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.DialogWindowProvider
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material3.AlertDialogContent
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import com.google.android.material.color.utilities.*

enum class PaletteStyle(val label: String) {
    TONAL_SPOT("Tonal Spot"), VIBRANT("Vibrant"), EXPRESSIVE("Expressive"),
    NEUTRAL("Neutral"), MONOCHROME("Monochrome"), FIDELITY("Fidelity")
}

data class Accent(val label: String, val argb: Int)
const val CLASSIC_ACCENT = 0xff4b586b.toInt()
val accents = listOf(Accent("经典", CLASSIC_ACCENT),
    Accent("紫色", 0xff6750a4.toInt()), Accent("蓝色", 0xff0061a4.toInt()),
    Accent("青色", 0xff006a6a.toInt()), Accent("绿色", 0xff386a20.toInt()),
    Accent("橙色", 0xffa04100.toInt()), Accent("粉色", 0xff984061.toInt()))
data class Appearance(val accent: Int = CLASSIC_ACCENT, val palette: PaletteStyle = PaletteStyle.TONAL_SPOT)

/** 与嗷呜文件相同，使用官方颜色工具从强调色生成完整暗色主题。 */
object AppAppearance {
    private fun preferences(context: Context) = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    @JvmStatic fun read(context: Context): Appearance {
        val prefs = preferences(context)
        return Appearance(prefs.getInt("accent", CLASSIC_ACCENT),
            PaletteStyle.entries.firstOrNull { it.name == prefs.getString("palette", "") } ?: PaletteStyle.TONAL_SPOT)
    }

    fun write(context: Context, appearance: Appearance) {
        preferences(context).edit().putInt("accent", appearance.accent)
            .putString("palette", appearance.palette.name).apply()
    }

    @Composable fun observe(): Appearance {
        val context = LocalContext.current
        val prefs = remember(context) { preferences(context) }
        var value by remember(context) { mutableStateOf(read(context)) }
        DisposableEffect(prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> value = read(context) }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return value
    }

    // Material 1.13.0 将颜色工具标记为库内部 API，集中在此适配固定依赖。
    @SuppressLint("RestrictedApi")
    fun colors(appearance: Appearance): ColorScheme {
        val seed = Hct.fromInt(appearance.accent)
        val scheme: DynamicScheme = when (appearance.palette) {
            PaletteStyle.TONAL_SPOT -> SchemeTonalSpot(seed, true, 0.0)
            PaletteStyle.VIBRANT -> SchemeVibrant(seed, true, 0.0)
            PaletteStyle.EXPRESSIVE -> SchemeExpressive(seed, true, 0.0)
            PaletteStyle.NEUTRAL -> SchemeNeutral(seed, true, 0.0)
            PaletteStyle.MONOCHROME -> SchemeMonochrome(seed, true, 0.0)
            PaletteStyle.FIDELITY -> SchemeFidelity(seed, true, 0.0)
        }
        return ColorScheme(
            primary = Color(scheme.primary), primaryDim = Color(scheme.primaryPalette.tone(70)),
            primaryContainer = Color(scheme.primaryContainer), onPrimary = Color(scheme.onPrimary),
            onPrimaryContainer = Color(scheme.onPrimaryContainer),
            secondary = Color(scheme.secondary), secondaryDim = Color(scheme.secondaryPalette.tone(70)),
            secondaryContainer = Color(scheme.secondaryContainer), onSecondary = Color(scheme.onSecondary),
            onSecondaryContainer = Color(scheme.onSecondaryContainer),
            tertiary = Color(scheme.tertiary), tertiaryDim = Color(scheme.tertiaryPalette.tone(70)),
            tertiaryContainer = Color(scheme.tertiaryContainer), onTertiary = Color(scheme.onTertiary),
            onTertiaryContainer = Color(scheme.onTertiaryContainer),
            surfaceContainerLow = Color(scheme.surfaceContainerLow), surfaceContainer = Color(scheme.surfaceContainer),
            surfaceContainerHigh = Color(scheme.surfaceContainerHigh), onSurface = Color(scheme.onSurface),
            onSurfaceVariant = Color(scheme.onSurfaceVariant), outline = Color(scheme.outline),
            outlineVariant = Color(scheme.outlineVariant), background = Color.Black,
            onBackground = Color(scheme.onBackground), error = Color(scheme.error),
            errorDim = Color(scheme.errorPalette.tone(70)), errorContainer = Color(scheme.errorContainer),
            onError = Color(scheme.onError), onErrorContainer = Color(scheme.onErrorContainer))
    }

    @JvmStatic fun primary(context: Context) = colors(read(context)).primary.toArgb()
    @JvmStatic fun outlineVariant(context: Context) = colors(read(context)).outlineVariant.toArgb()

    @JvmStatic fun applyToViews(root: View) {
        val appearance = read(root.context)
        val scheme = colors(appearance)
        fun apply(view: View) {
            if (view is Button) {
                // 经典模式保留 XML 中原先逐类手配的背景，包括 2nd 的选中态。
                if (appearance.accent == CLASSIC_ACCENT) {
                    view.backgroundTintList = null
                    view.setTextColor(android.graphics.Color.WHITE)
                    return
                }
                val (container, content) = when (view.id) {
                    R.id.btnEq, R.id.btnScientificEq -> scheme.primary to scheme.onPrimary
                    R.id.btnAc, R.id.btnDel, R.id.btnScientificDel ->
                        scheme.primaryContainer to scheme.onPrimaryContainer
                    R.id.btnSin, R.id.btnCos, R.id.btnTan -> scheme.tertiaryContainer to scheme.onTertiaryContainer
                    R.id.btnLog -> scheme.secondaryContainer to scheme.onSecondaryContainer
                    R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5,
                    R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnDot -> scheme.surfaceContainer to scheme.onSurface
                    else -> scheme.primaryContainer to scheme.onPrimaryContainer
                }
                view.backgroundTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
                    intArrayOf(scheme.primary.toArgb(), container.toArgb()))
                view.setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
                    intArrayOf(scheme.onPrimary.toArgb(), content.toArgb())))
            } else if (view is TextView) {
                view.setTextColor(when (view.id) {
                    R.id.tvHistory, R.id.historyEquation -> scheme.onSurfaceVariant
                    else -> scheme.onBackground
                }.toArgb())
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) apply(view.getChildAt(i))
        }
        apply(root)
    }
}

@Composable fun CalculatorTheme(content: @Composable () -> Unit) {
    val appearance = AppAppearance.observe()
    val colors = remember(appearance) { AppAppearance.colors(appearance) }
    MaterialTheme(colorScheme = colors, motionScheme = MotionScheme.expressive()) {
        CalculatorHapticFeedbackProvider(content)
    }
}

@Composable private fun CalculatorHapticFeedbackProvider(content: @Composable () -> Unit) {
    val view = LocalView.current
    val activity = remember(view) {
        generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<WearActivity>().firstOrNull()
    }
    val dialogWindow = (view.parent as? DialogWindowProvider)?.window
    DisposableEffect(view, dialogWindow, activity) {
        // Wear 列表会绕过 LocalHapticFeedback，直接请求承载 View 的震动。
        // 只关闭 Compose 内容 View，保留 Activity decorView 的全局反馈。
        val wasEnabled = view.isHapticFeedbackEnabled
        view.isHapticFeedbackEnabled = false
        val originalCallback = dialogWindow?.callback
        val callback = if (originalCallback != null && activity != null) {
            object : Window.Callback by originalCallback {
                override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
                    activity.handleRotaryHaptics(event)
                    return originalCallback.dispatchGenericMotionEvent(event)
                }
            }
        } else null
        if (callback != null) dialogWindow?.callback = callback
        onDispose {
            view.isHapticFeedbackEnabled = wasEnabled
            if (callback != null && dialogWindow?.callback === callback) {
                dialogWindow.callback = originalCallback
            }
        }
    }
    val feedback = remember {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
        }
    }
    CompositionLocalProvider(LocalHapticFeedback provides feedback, content = content)
}

@Composable internal fun CalculatorAlertDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    text: @Composable (() -> Unit)? = null,
    content: (ScalingLazyListScope.() -> Unit)? = null,
) {
    // 沿用官方布局和动效；反馈设置覆盖整个弹窗，避免标题滚出屏幕后失效。
    Dialog(visible = visible, onDismissRequest = onDismissRequest) {
        CalculatorHapticFeedbackProvider {
            AlertDialogContent(modifier = modifier, title = title, text = text, content = content)
        }
    }
}

object CalculatorHaptics {
    const val FEEDBACK_CONSTANT = 18
    const val ROTARY_THRESHOLD = 0.4f

    @JvmStatic fun perform(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(FEEDBACK_CONSTANT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}
