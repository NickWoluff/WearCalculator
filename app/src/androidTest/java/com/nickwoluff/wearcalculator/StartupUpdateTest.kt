package com.nickwoluff.wearcalculator

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 使用确定的检查结果验证启动策略，不依赖 GitHub 网络或实际 Release 版本。 */
@RunWith(AndroidJUnit4::class)
class StartupUpdateTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var savedAutoCheck = true

    @Before fun prepare() {
        savedAutoCheck = UpdatePreferences.autoCheck(context)
        UpdatePreferences.setAutoCheck(context, true)
    }

    @After fun cleanup() { UpdatePreferences.setAutoCheck(context, savedAutoCheck) }

    @Test fun preferenceDefaultsToEnabledAndDisabledStartupSendsNoRequest() {
        val prefs = context.getSharedPreferences("update_preferences", 0)
        val saved = prefs.all
        try {
            prefs.edit().clear().commit()
            assertTrue(UpdatePreferences.autoCheck(context))
            UpdatePreferences.setAutoCheck(context, false)
            val request = FakeRequest()
            val model = StartupUpdateViewModel(request, Runnable {})
            instrumentation.runOnMainSync { model.checkOnce(context, "1.1.1") }
            assertEquals(0, request.count)
        } finally {
            val editor = prefs.edit().clear()
            saved["auto_check"]?.let { editor.putBoolean("auto_check", it as Boolean) }
            editor.commit()
        }
    }

    @Test fun startupChecksOnceAndIgnoresLatestFailuresAndMalformedVersions() {
        for (result in listOf("v1.1.1", "v1.0.0", "invalid", null)) {
            val request = FakeRequest()
            val model = StartupUpdateViewModel(request, Runnable {})
            instrumentation.runOnMainSync {
                model.checkOnce(context, "1.1.1")
                model.checkOnce(context, "1.1.1")
                if (result == null) request.callback!!.onError(503)
                else request.callback!!.onResult(result, "https://github.com/")
            }
            assertEquals(1, request.count)
            assertNull(model.availableVersion.value)
        }
    }

    @Test fun disablingWhileCheckingSuppressesUpdateAndClearingCancelsRequest() {
        val request = FakeRequest()
        var closed = false
        val model = StartupUpdateViewModel(request, Runnable { closed = true })
        instrumentation.runOnMainSync {
            model.checkOnce(context, "1.1.1")
            UpdatePreferences.setAutoCheck(context, false)
            request.callback!!.onResult("v2.0.0", "https://github.com/")
            assertNull(model.availableVersion.value)
            UpdatePreferences.setAutoCheck(context, true)
            val store = ViewModelStore()
            store.put("startup", model)
            store.clear()
            request.callback!!.onResult("v2.0.0", "https://github.com/")
        }
        assertTrue(closed)
        assertNull(model.availableVersion.value)
    }

    @Test fun newerReleaseShowsWearDialogAndCanBeDismissed() {
        val request = FakeRequest()
        val model = StartupUpdateViewModel(request, Runnable {})
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContentView(StartupUpdateUiController(activity, model).view)
                model.checkOnce(context, "1.1.1")
                request.callback!!.onResult("v2.0.0", "https://github.com/")
            }
            compose.onNodeWithTag("startup_update_dialog").assertExists()
            compose.onNodeWithText("发现新版本 v2.0.0").assertExists()
            compose.onNodeWithTag("startup_open_release").assertHasClickAction()
            instrumentation.runOnMainSync { model.dismiss() }
            compose.onNodeWithTag("startup_open_release").assertDoesNotExist()
        }
    }

    private class FakeRequest : StartupUpdateViewModel.Request {
        var count = 0
        var callback: UpdateChecker.Callback? = null
        override fun check(callback: UpdateChecker.Callback) { count++; this.callback = callback }
    }
}
