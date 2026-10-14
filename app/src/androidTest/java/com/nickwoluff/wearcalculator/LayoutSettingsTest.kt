package com.nickwoluff.wearcalculator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
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

/** 验证首次识别、手动选择、页面资源切换及实际提示绘制。 */
@RunWith(AndroidJUnit4::class)
class LayoutSettingsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = context.getSharedPreferences("screen_layout", Context.MODE_PRIVATE)
    private var saved: Boolean? = null
    private var savedAutoCheck = true
    private var scenario: ActivityScenario<out Activity>? = null

    @Before fun prepare() {
        savedAutoCheck = UpdatePreferences.autoCheck(context)
        UpdatePreferences.setAutoCheck(context, false)
        saved = if (preferences.contains("round")) preferences.getBoolean("round", false) else null
        preferences.edit().clear().commit()
    }

    @After fun cleanup() {
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .toList().forEach { it.finish() }
        }
        scenario?.close()
        val editor = preferences.edit().clear()
        saved?.let { editor.putBoolean("round", it) }
        editor.commit()
        UpdatePreferences.setAutoCheck(context, savedAutoCheck)
    }

    @Test fun firstLaunchDetectsPhysicalShapeAndKeepsSavedChoice() {
        val physicalRound = context.resources.configuration.isScreenRound
        assertEquals(physicalRound, LayoutPreferences.isRound(context))
        assertTrue(preferences.contains("round"))
        val differentShape = Configuration(context.resources.configuration).apply {
            screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                if (physicalRound) Configuration.SCREENLAYOUT_ROUND_NO else Configuration.SCREENLAYOUT_ROUND_YES
        }
        assertEquals(physicalRound, LayoutPreferences.isRound(context.createConfigurationContext(differentShape)))
        LayoutPreferences.setRound(context, !physicalRound)
        assertEquals(!physicalRound, LayoutPreferences.applyTo(context).resources.configuration.isScreenRound)
    }

    @Test fun selectingLayoutPersistsAndReturningKeepsCalculationAndSecondPage() {
        LayoutPreferences.setRound(context, false)
        val main = ActivityScenario.launch(MainActivity::class.java)
        scenario = main
        main.onActivity { activity ->
            for (id in intArrayOf(R.id.btn1, R.id.btnAdd, R.id.btn2, R.id.btnEq)) {
                activity.findViewById<Button>(id).performClick()
            }
            activity.findViewById<SwipeLayout>(R.id.buttonArea).setCurrentPage(1)
            activity.findViewById<Button>(R.id.btnSecond).performClick()
        }
        instrumentation.startActivitySync(Intent(context, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitSettings(false)
        compose.onNodeWithTag("layout_square").assertDoesNotExist()
        scrollTo("layout_picker").assertTextContains("方屏")
        saveScreenshot("settings_square")
        openLayoutPicker()
        compose.onNodeWithTag("layout_square").assertIsSelected()
        saveScreenshot("layout_dialog_square")
        compose.onNodeWithTag("layout_round").performClick()
        assertTrue("点击圆屏选项应保存选择", LayoutPreferences.isRound(context))
        val settings = awaitSettings(true)
        compose.onNodeWithTag("layout_round").assertDoesNotExist()
        scrollTo("layout_picker").assertTextContains("圆屏")
        saveScreenshot("settings_round")
        openLayoutPicker()
        compose.onNodeWithTag("layout_round").assertIsSelected()
        saveScreenshot("layout_dialog_round")
        // 再次选择当前布局也要收起弹窗，不重建页面。
        compose.onNodeWithTag("layout_round").performClick()
        compose.onNodeWithTag("layout_round").assertDoesNotExist()
        assertTrue(LayoutPreferences.isRound(context))
        instrumentation.runOnMainSync { settings.finish() }
        instrumentation.waitForIdleSync()
        compose.waitUntil(timeoutMillis = 10000) {
            var ready = false
            instrumentation.runOnMainSync {
                ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().any { it.resources.configuration.isScreenRound }
            }
            ready
        }
        main.onActivity { activity ->
            assertTrue(activity.resources.configuration.isScreenRound)
            assertEquals("3", activity.findViewById<TextView>(R.id.tvDisplay).text.toString())
            assertEquals("1+2=", activity.findViewById<TextView>(R.id.tvHistory).text.toString())
            assertEquals("sin⁻¹", activity.findViewById<Button>(R.id.btnSin).text.toString())
            assertEquals(1, activity.findViewById<SwipeLayout>(R.id.buttonArea).currentPage)
            // 圆屏的百分号属于第二页，验证真正选择了 layout-round 资源。
            assertTrue(activity.findViewById<Button>(R.id.btnPercent).parent.parent ===
                activity.findViewById<android.view.View>(R.id.scientificButtons))
        }
        main.close()
        scenario = ActivityScenario.launch(SettingsActivity::class.java)
        awaitSettings(true)
        openLayoutPicker()
        compose.onNodeWithTag("layout_round").assertIsSelected()
        compose.onNodeWithTag("layout_square").performClick()
        awaitSettings(false)
        assertFalse(LayoutPreferences.isRound(context))
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().single()
            assertFalse(activity.resources.configuration.isScreenRound)
            assertTrue(activity.findViewById<Button>(R.id.btnPercent).parent.parent ===
                activity.findViewById<android.view.View>(R.id.basicButtons))
        }
    }

    @Test fun squareNoticeHasHorizontalBaseline() { verifyNotice(false) }

    @Test fun roundNoticeFollowsBottomArc() { verifyNotice(true) }

    private fun scrollTo(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    private fun openLayoutPicker() {
        scrollTo("layout_picker").performClick()
        compose.onNodeWithTag("layout_dialog").assertExists()
        instrumentation.runOnMainSync {
            val settings = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<SettingsActivity>().single()
            assertFalse("弹窗打开时下滑不能退出设置页面", settings.canSwipeDownToExit())
        }
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun verifyNotice(round: Boolean) {
        LayoutPreferences.setRound(context, round)
        scenario = ActivityScenario.launch(SettingsActivity::class.java)
        val activity = awaitSettings(round)
        compose.onNodeWithTag("floating_time").assertExists()
        compose.onNodeWithTag("check_update").assertIsEnabled()
        SystemClock.sleep(700)
        val before = instrumentation.uiAutomation.takeScreenshot()
        compose.mainClock.autoAdvance = false
        instrumentation.runOnMainSync { activity.toastForTesting.show("已是最新版本") }
        // WearFiles 的动画由 Compose 驱动，固定在展开且文字淡入后的帧。
        compose.waitForIdle()
        val noticeBounds = compose.onNodeWithTag("notice").fetchSemanticsNode().boundsInRoot
        assertTrue("提示覆盖层应填满窗口：$noticeBounds", noticeBounds.height > 300 && noticeBounds.width > 300)
        // BoxWithConstraints 首次测量后会重启动画，逐帧同步布局和协程。
        repeat(32) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        instrumentation.waitForIdleSync()
        // Compose 使用硬件绘制，检测真实窗口合成结果，避免软件 Canvas 丢失图层。
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), if (round) "layout_round_notice.png" else "layout_square_notice.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(context.getExternalFilesDir(null), if (round) "layout_round_before.png" else "layout_square_before.png")
            .outputStream().use { before.compress(Bitmap.CompressFormat.PNG, 100, it) }
        run {
            val center = bitmap.width / 2
            val offset = (30 * activity.resources.displayMetrics.density).toInt()
            fun topOfBand(x: Int): Int = (bitmap.height / 2 until bitmap.height)
                .first { y ->
                    val old = before.getPixel(x, y)
                    val now = bitmap.getPixel(x, y)
                    kotlin.math.abs(Color.red(now) - Color.red(old)) > 20 &&
                        kotlin.math.abs(Color.green(now) - Color.green(old)) > 20 &&
                        kotlin.math.abs(Color.blue(now) - Color.blue(old)) > 20
                }
            val rise = topOfBand(center) - topOfBand(center - offset)
            if (round) {
                assertTrue("圆屏提示应沿底部弧线，中心比侧边低", rise > 2)
                val top = topOfBand(center)
                val density = activity.resources.displayMetrics.density
                val glyphRows = (top until (top + 31 * density).toInt().coerceAtMost(bitmap.height))
                    .filter { y -> (center - 12..center + 12).any { x ->
                        Color.red(bitmap.getPixel(x, y)) > 190 &&
                            Color.red(bitmap.getPixel(x, y)) - Color.red(before.getPixel(x, y)) > 100
                    } }
                assertTrue("圆屏提示应显示文字", glyphRows.isNotEmpty())
                assertTrue("文字应位于弧形底板中央，不能偏向内边缘",
                    kotlin.math.abs(glyphRows.average() - (top + 15.5 * density)) < 6 * density)
            } else assertTrue("方屏提示应为水平胶囊", kotlin.math.abs(rise) <= 1)
        }
        before.recycle()
        bitmap.recycle()
        compose.mainClock.autoAdvance = true
    }

    private fun awaitSettings(round: Boolean): SettingsActivity {
        var result: SettingsActivity? = null
        compose.waitUntil(timeoutMillis = 10000, conditionDescription = "等待设置页使用 round=$round 的布局") {
            instrumentation.runOnMainSync {
                result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<SettingsActivity>().firstOrNull {
                        it.resources.configuration.isScreenRound == round && !it.isDestroyed
                    }
            }
            result != null
        }
        // 窗口的原生进入动画不受 Compose 测试时钟控制，等待触摸坐标稳定。
        SystemClock.sleep(700)
        instrumentation.waitForIdleSync()
        return result!!
    }
}
