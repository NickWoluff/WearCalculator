package com.nickwoluff.wearcalculator

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 从设置入口操作真实单选弹窗，验证保存、主题传播和计算状态恢复。 */
@RunWith(AndroidJUnit4::class)
class ThemeSettingsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val layout = context.getSharedPreferences("screen_layout", Context.MODE_PRIVATE)
    private var savedRound: Boolean? = null
    private var savedAutoCheck = true
    private lateinit var savedAppearance: Appearance
    private var main: ActivityScenario<MainActivity>? = null

    @Before fun prepare() {
        savedAutoCheck = UpdatePreferences.autoCheck(context)
        UpdatePreferences.setAutoCheck(context, false)
        savedRound = if (layout.contains("round")) layout.getBoolean("round", false) else null
        savedAppearance = AppAppearance.read(context)
        AppAppearance.write(context, Appearance())
    }

    @After fun cleanup() {
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .toList().forEach { it.finish() }
        }
        main?.close()
        AppAppearance.write(context, savedAppearance)
        val editor = layout.edit().clear()
        savedRound?.let { editor.putBoolean("round", it) }
        editor.commit()
        UpdatePreferences.setAutoCheck(context, savedAutoCheck)
    }

    @Test fun squareThemeSelectionPersistsAndPreservesCalculation() { verifyFlow(false) }
    @Test fun roundThemeSelectionPersistsAndPreservesCalculation() { verifyFlow(true) }

    @Test fun classicIsFirstAndDefaultWhileSavedAccentIsRespected() {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        prefs.edit().remove("accent").commit()
        assertEquals("经典", accents.first().label)
        assertEquals(CLASSIC_ACCENT, Appearance().accent)
        assertEquals(CLASSIC_ACCENT, AppAppearance.read(context).accent)
        AppAppearance.write(context, Appearance(accents[1].argb))
        assertEquals(accents[1].argb, AppAppearance.read(context).accent)
    }

    @Test fun classicAccentRestoresOriginalColorsOnBothLayouts() {
        for (round in listOf(false, true)) {
            AppAppearance.write(context, Appearance(accents[1].argb))
            LayoutPreferences.setRound(context, round)
            val theme = ActivityScenario.launch(ThemeActivity::class.java)
            settle()
            scrollTheme("accent_picker").performClick()
            settle()
            scrollDialog("accent", "accent_0").assertTextContains("经典").performClick()
            settle()
            scrollTheme("accent_picker").assertTextContains("经典")
            assertEquals(CLASSIC_ACCENT, AppAppearance.read(context).accent)
            theme.close()
            main = ActivityScenario.launch(MainActivity::class.java)
            main!!.onActivity { activity ->
                fun backgroundColor(id: Int): Int {
                    val button = activity.findViewById<Button>(id)
                    val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
                    button.background.setBounds(0, 0, 40, 40)
                    button.background.draw(Canvas(bitmap))
                    return bitmap.getPixel(20, 20).also { bitmap.recycle() }
                }
                assertEquals(Color.parseColor("#2D2F33"), backgroundColor(R.id.btn1))
                assertEquals(Color.parseColor("#4B586B"), backgroundColor(R.id.btnAdd))
                assertEquals(Color.parseColor("#824C51"), backgroundColor(R.id.btnAc))
                assertEquals(Color.parseColor("#824C51"), backgroundColor(R.id.btnScientificDel))
                assertEquals(Color.parseColor("#254A48"), backgroundColor(R.id.btnSin))
                assertEquals(Color.parseColor("#41405B"), backgroundColor(R.id.btnLog))
                activity.findViewById<Button>(R.id.btnSecond).performClick()
                assertEquals(Color.parseColor("#426E6A"), backgroundColor(R.id.btnSecond))
                assertEquals(Color.parseColor("#B0B0B0"), indicatorColor(activity, 0f, 0))
                assertEquals(Color.parseColor("#555555"), indicatorColor(activity, 0f, 1))
                assertEquals(Color.parseColor("#B0B0B0"), indicatorColor(activity, 1f, 1))
                assertEquals(Color.parseColor("#555555"), indicatorColor(activity, 1f, 0))
                for (page in 0..1) {
                    val color = indicatorColor(activity, 0.5f, page)
                    assertEquals(Color.red(color), Color.green(color))
                    assertEquals(Color.green(color), Color.blue(color))
                }
            }
            main!!.close()
        }
    }

    private fun verifyFlow(round: Boolean) {
        AppAppearance.write(context, Appearance(accents[1].argb))
        LayoutPreferences.setRound(context, round)
        var previousColor = 0
        var previousClearColor = 0
        main = ActivityScenario.launch(MainActivity::class.java)
        main!!.onActivity { activity ->
            previousColor = activity.findViewById<Button>(R.id.btnEq).backgroundTintList!!.defaultColor
            previousClearColor = activity.findViewById<Button>(R.id.btnAc).backgroundTintList!!.defaultColor
            for (id in intArrayOf(R.id.btn1, R.id.btnAdd, R.id.btn2, R.id.btnEq)) {
                activity.findViewById<Button>(id).performClick()
            }
            activity.findViewById<SwipeLayout>(R.id.buttonArea).setCurrentPage(1)
            activity.findViewById<Button>(R.id.btnSecond).performClick()
            activity.startActivity(Intent(activity, SettingsActivity::class.java))
        }
        settle()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("theme_settings"))
        compose.onNodeWithTag("theme_settings").performClick()
        settle()
        scrollTheme("accent_picker").assertTextContains("紫色").performClick()
        settle()
        // 最后一个选项需要滚动，验证完整弹窗列表可达。
        scrollDialog("accent", "accent_6").performClick()
        settle()
        compose.onNodeWithTag("accent_6").assertDoesNotExist()
        scrollTheme("accent_picker").assertTextContains("粉色")
        scrollTheme("palette_picker").assertTextContains("Tonal Spot").performClick()
        settle()
        scrollDialog("palette", "palette_FIDELITY").performClick()
        settle()
        compose.onNodeWithTag("palette_FIDELITY").assertDoesNotExist()
        scrollTheme("palette_picker").assertTextContains("Fidelity")
        assertEquals(Appearance(accents[6].argb, PaletteStyle.FIDELITY), AppAppearance.read(context))
        saveScreenshot(if (round) "theme_round.png" else "theme_square.png")
        finishResumed(ThemeActivity::class.java)
        settle()
        finishResumed(SettingsActivity::class.java)
        settle()
        main!!.onActivity { activity ->
            assertEquals("3", activity.findViewById<TextView>(R.id.tvDisplay).text.toString())
            assertEquals("1+2=", activity.findViewById<TextView>(R.id.tvHistory).text.toString())
            assertEquals("sin⁻¹", activity.findViewById<Button>(R.id.btnSin).text.toString())
            assertEquals(1, activity.findViewById<SwipeLayout>(R.id.buttonArea).currentPage)
            assertNotEquals(previousColor, activity.findViewById<Button>(R.id.btnEq).backgroundTintList!!.defaultColor)
            val operatorColor = activity.findViewById<Button>(R.id.btnAdd).backgroundTintList!!.defaultColor
            for (id in intArrayOf(R.id.btnAc, R.id.btnDel, R.id.btnScientificAc, R.id.btnScientificDel)) {
                assertEquals(operatorColor, activity.findViewById<Button>(id).backgroundTintList!!.defaultColor)
            }
            assertNotEquals(previousClearColor, operatorColor)
            assertEquals(AppAppearance.primary(activity), indicatorColor(activity, 1f, 1))
            activity.startActivity(Intent(activity, ThemeActivity::class.java))
        }
        settle()
        scrollTheme("accent_picker").assertTextContains("粉色").performClick()
        settle()
        scrollDialog("accent", "accent_6").assertIsSelected()
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        settle()
        scrollTheme("palette_picker").assertTextContains("Fidelity").performClick()
        settle()
        scrollDialog("palette", "palette_FIDELITY").assertIsSelected()
    }

    private fun indicatorColor(activity: MainActivity, position: Float, page: Int): Int {
        val indicator = activity.findViewById<PageIndicatorView>(R.id.pageIndicator)
        indicator.setPagePosition(position)
        val bitmap = Bitmap.createBitmap(indicator.width, indicator.height, Bitmap.Config.ARGB_8888)
        indicator.draw(Canvas(bitmap))
        val spacing = (if (activity.resources.configuration.isScreenRound) 12f else 8f) *
            activity.resources.displayMetrics.density
        val x = (indicator.width / 2f + (page - 0.5f) * spacing).toInt()
        return bitmap.getPixel(x, indicator.height / 2).also { bitmap.recycle() }
    }

    private fun scrollTheme(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("theme_list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    private fun scrollDialog(dialog: String, tag: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("${dialog}_dialog")))
            .performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    private fun finishResumed(type: Class<*>) {
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .single { type.isInstance(it) }.finish()
        }
    }

    private fun settle() {
        SystemClock.sleep(800)
        instrumentation.waitForIdleSync()
        compose.waitForIdle()
    }

    private fun saveScreenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
