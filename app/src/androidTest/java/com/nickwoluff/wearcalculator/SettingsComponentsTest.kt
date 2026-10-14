package com.nickwoluff.wearcalculator

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class SettingsComponentsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: SettingsActivity
    private var savedAutoCheck = true

    @Before fun launch() {
        savedAutoCheck = UpdatePreferences.autoCheck(instrumentation.targetContext)
        UpdatePreferences.setAutoCheck(instrumentation.targetContext, true)
        val intent = Intent(instrumentation.targetContext, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity = instrumentation.startActivitySync(intent) as SettingsActivity
        instrumentation.waitForIdleSync()
    }

    @After fun finish() {
        instrumentation.runOnMainSync { activity.finish() }
        instrumentation.waitForIdleSync()
        UpdatePreferences.setAutoCheck(instrumentation.targetContext, savedAutoCheck)
    }

    @Test fun autoUpdateSwitchPersistsAndKeepsManualCheckEnabled() {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("auto_check_update"))
        compose.onNodeWithTag("auto_check_update").assertIsOn().performClick().assertIsOff()
        assertFalse(UpdatePreferences.autoCheck(instrumentation.targetContext))
        val previous = activity
        instrumentation.runOnMainSync { activity.recreate() }
        compose.waitUntil(timeoutMillis = 10000) {
            var ready = false
            instrumentation.runOnMainSync {
                val resumed = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<SettingsActivity>().firstOrNull { it !== previous }
                if (resumed != null) { activity = resumed; ready = true }
            }
            ready
        }
        SystemClock.sleep(700)
        compose.waitForIdle()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("auto_check_update"))
        compose.onNodeWithTag("auto_check_update").assertIsOff().assertTextContains("仅手动检查")
        compose.onNodeWithTag("auto_check_update").performClick().assertIsOn()
        assertTrue(UpdatePreferences.autoCheck(instrumentation.targetContext))
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("check_update"))
        compose.onNodeWithTag("check_update").assertIsEnabled()
    }

    @Test fun copyrightIsVisibleAtTheEndOfSettings() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = if (currentYear > 2026) "2026-$currentYear" else "2026"
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_copyright"))
        compose.onNodeWithTag("settings_copyright")
            .assertIsDisplayed().assertTextEquals("© $years Nick Woluff. All Rights Reserved.")
        compose.onNodeWithTag("layout_picker").assertExists()
    }

    @Test fun officialButtonShowsCheckingStateAndReleaseAction() {
        val ui = activity.uiForTesting
        compose.onNodeWithTag("floating_time").assertExists()
        compose.onNodeWithTag("check_update").assertIsEnabled().assertTextContains("检查更新")
        instrumentation.runOnMainSync { ui.updateChecking(true) }
        compose.onNodeWithTag("check_update").assertIsNotEnabled().assertTextContains("正在检查…")
        instrumentation.runOnMainSync {
            ui.updateChecking(false)
            ui.showRelease("v1.2.0")
        }
        compose.onNodeWithTag("check_update").assertIsEnabled()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("open_release"))
        compose.onNodeWithTag("open_release").assertIsEnabled()
            .assertTextContains("前往 Release")
    }

    @Test fun arcNoticeAllowsUnderlyingButtonTouchAndRemovesItself() {
        val checks = AtomicInteger()
        instrumentation.runOnMainSync {
            val ui = SettingsUiController(activity, "1.1.1", Runnable { checks.incrementAndGet() }, Runnable {})
            activity.setContentView(ui.view)
        }
        val center = compose.onNodeWithTag("check_update").fetchSemanticsNode().boundsInRoot.center
        instrumentation.runOnMainSync { activity.toastForTesting.show("已是最新版本") }
        SystemClock.sleep(320)
        instrumentation.runOnMainSync {
            val toast = activity.window.decorView.findViewWithTag<ArcToastView>("arc_toast")
            assertNotNull(toast)
            assertEquals("已是最新版本", toast.contentDescription)
            assertFalse(toast.isClickable)
            assertFalse(toast.isFocusable)
            assertTrue(activity.hasWindowFocus())
            val location = IntArray(2)
            activity.findViewById<android.view.View>(android.R.id.content).getLocationOnScreen(location)
            val time = SystemClock.uptimeMillis()
            for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action,
                    center.x + location[0], center.y + location[1], 0)
                activity.dispatchTouchEvent(event)
                event.recycle()
            }
        }
        compose.waitForIdle()
        assertEquals(1, checks.get())
        val deadline = SystemClock.uptimeMillis() + 5000
        var removed = false
        while (!removed && SystemClock.uptimeMillis() < deadline) {
            // delay 也使用测试虚拟时钟，显式推进保持期与淡出动画。
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
            instrumentation.runOnMainSync {
                removed = activity.window.decorView.findViewWithTag<ArcToastView>("arc_toast") == null
            }
            if (!removed) SystemClock.sleep(100)
        }
        assertTrue("弧形提示应该自动移除", removed)
    }

    @Test fun newNoticeReplacesPreviousAndFinishingActivityCleansOverlay() {
        instrumentation.runOnMainSync {
            activity.toastForTesting.show("正在检查更新，请稍后再试")
            activity.toastForTesting.show("已是最新版本")
            val toast = activity.window.decorView.findViewWithTag<ArcToastView>("arc_toast")
            assertSame(activity.toastForTesting, toast)
            assertEquals("已是最新版本", toast.contentDescription)
            activity.finish()
        }
        instrumentation.waitForIdleSync()
        // 等待实际关闭，避免窗口动画或设备负载超过固定延时。
        compose.waitUntil(timeoutMillis = 10000) {
            var destroyed = false
            instrumentation.runOnMainSync { destroyed = activity.isDestroyed }
            destroyed
        }
        instrumentation.runOnMainSync { assertNull(activity.toastForTesting.parent) }
    }
}
