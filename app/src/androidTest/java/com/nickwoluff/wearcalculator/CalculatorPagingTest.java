package com.nickwoluff.wearcalculator;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.*;

/** 在方屏和圆屏模拟器上复用，验证按钮页的布局、手势和百分比计算。 */
@RunWith(AndroidJUnit4.class)
public class CalculatorPagingTest {

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity activity;
    private SwipeLayout pager;
    private boolean savedAutoCheck;

    @Before
    public void launchCalculator() {
        savedAutoCheck = UpdatePreferences.autoCheck(instrumentation.getTargetContext());
        UpdatePreferences.setAutoCheck(instrumentation.getTargetContext(), false);
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity) instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();
        long deadline = SystemClock.uptimeMillis() + 8000;
        while (SystemClock.uptimeMillis() < deadline) {
            boolean[] focused = {false};
            instrumentation.runOnMainSync(() -> focused[0] = activity.hasWindowFocus());
            if (focused[0]) break;
            SystemClock.sleep(100);
        }
        SystemClock.sleep(700L);
        pager = activity.findViewById(R.id.buttonArea);
    }

    @After
    public void closeCalculator() {
        instrumentation.runOnMainSync(() -> activity.finish());
        UpdatePreferences.setAutoCheck(instrumentation.getTargetContext(), savedAutoCheck);
    }

    @Test
    public void bothPagesKeepIdenticalRowsAndButtonBounds() {
        instrumentation.runOnMainSync(() -> assertMatchingBounds(
                activity.findViewById(R.id.basicButtons), activity.findViewById(R.id.scientificButtons)));
    }

    private void assertMatchingBounds(View basic, View scientific) {
        assertEquals(basic.getLeft(), scientific.getLeft());
        assertEquals(basic.getTop(), scientific.getTop());
        assertEquals(basic.getWidth(), scientific.getWidth());
        assertEquals(basic.getHeight(), scientific.getHeight());
        if (basic instanceof ViewGroup && scientific instanceof ViewGroup) {
            ViewGroup basicGroup = (ViewGroup) basic;
            ViewGroup scientificGroup = (ViewGroup) scientific;
            assertEquals(basicGroup.getChildCount(), scientificGroup.getChildCount());
            for (int i = 0; i < basicGroup.getChildCount(); i++) {
                assertMatchingBounds(basicGroup.getChildAt(i), scientificGroup.getChildAt(i));
            }
        }
    }

    @Test
    public void draggingMovesOnlyButtonsAndCancelRestoresCurrentPage() {
        instrumentation.runOnMainSync(() -> {
            View display = activity.findViewById(R.id.hsvDisplay);
            Rect before = new Rect();
            display.getGlobalVisibleRect(before);
            float x = pager.getWidth() * 0.8f;
            float y = pager.getHeight() * 0.3f;
            long start = SystemClock.uptimeMillis();
            dispatchLocal(start, start, MotionEvent.ACTION_DOWN, x, y);
            dispatchLocal(start, start + 100, MotionEvent.ACTION_MOVE, pager.getWidth() * 0.6f, y);
            dispatchLocal(start, start + 200, MotionEvent.ACTION_MOVE, pager.getWidth() * 0.4f, y);
            assertTrue(activity.findViewById(R.id.basicButtons).getTranslationX() < 0);
            assertTrue(activity.findViewById(R.id.scientificButtons).getTranslationX() > 0);
            Rect after = new Rect();
            display.getGlobalVisibleRect(after);
            assertEquals(before, after);
            assertEquals(0f, display.getTranslationX(), 0f);
            dispatchLocal(start, start + 250, MotionEvent.ACTION_CANCEL, pager.getWidth() * 0.4f, y);
        });
        waitForPageAnimation();
        assertEquals(0, pager.getCurrentPage());
        onView(withId(R.id.tvDisplay)).check(matches(withText("0")));
    }

    @Test
    public void swipesUpdateIndicatorAndSecondModeIsReversible() {
        swipe(false, false);
        assertEquals(1, pager.getCurrentPage());
        instrumentation.runOnMainSync(() -> assertEquals(
                activity.getString(R.string.page_indicator_description, 2),
                activity.findViewById(R.id.pageIndicator).getContentDescription()));
        onView(withId(R.id.btnSecond)).perform(click());
        onView(withId(R.id.btnSin)).check(matches(withText("sin⁻¹")));
        onView(withId(R.id.btnCos)).check(matches(withText("cos⁻¹")));
        onView(withId(R.id.btnTan)).check(matches(withText("tan⁻¹")));
        onView(withId(R.id.btnSecond)).perform(click());
        onView(withId(R.id.btnSin)).check(matches(withText("sin")));
        swipe(true, false);
        assertEquals(0, pager.getCurrentPage());
    }

    @Test
    public void rightSwipeFromLeftEdgeOnButtonsDoesNotCloseActivity() {
        swipe(false, false);
        swipe(true, true);
        assertEquals(0, pager.getCurrentPage());
        swipe(true, true);
        instrumentation.runOnMainSync(() -> {
            assertFalse(activity.isFinishing());
            assertTrue(activity.hasWindowFocus());
        });
    }

    @Test
    public void percentButtonIsOnRequestedPageAndCalculatesPercentage() {
        onView(withId(R.id.btn5)).perform(click());
        onView(withId(R.id.btn0)).perform(click());
        boolean round = activity.getResources().getConfiguration().isScreenRound();
        if (round) swipe(false, false);
        onView(withId(R.id.btnPercent)).perform(click());
        onView(withId(round ? R.id.btnScientificEq : R.id.btnEq)).perform(click());
        onView(withId(R.id.tvDisplay)).check(matches(withText("0.5")));
    }

    private void swipe(boolean right, boolean fromEdge) {
        int[] location = new int[2];
        instrumentation.runOnMainSync(() -> pager.getLocationOnScreen(location));
        float y = location[1] + pager.getHeight() * 0.35f;
        float edgeX = 1f;
        if (activity.getResources().getConfiguration().isScreenRound()) {
            float radius = activity.getResources().getDisplayMetrics().widthPixels / 2f;
            float offset = y - radius;
            edgeX = radius - (float) Math.sqrt(radius * radius - offset * offset) + 2f;
        }
        float startX = fromEdge ? edgeX : location[0] + pager.getWidth() * (right ? 0.15f : 0.85f);
        float endX = location[0] + pager.getWidth() * (right ? 0.85f : 0.15f);
        long start = SystemClock.uptimeMillis();
        sendPointer(start, MotionEvent.ACTION_DOWN, startX, y);
        for (int i = 1; i <= 12; i++) {
            SystemClock.sleep(16L);
            sendPointer(start, MotionEvent.ACTION_MOVE, startX + (endX - startX) * i / 12f, y);
        }
        sendPointer(start, MotionEvent.ACTION_UP, endX, y);
        waitForPageAnimation();
    }

    private void sendPointer(long start, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0);
        instrumentation.sendPointerSync(event);
        event.recycle();
    }

    private void dispatchLocal(long start, long time, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(start, time, action, x, y, 0);
        pager.dispatchTouchEvent(event);
        event.recycle();
    }

    private void waitForPageAnimation() {
        SystemClock.sleep(400L);
        instrumentation.waitForIdleSync();
    }
}
