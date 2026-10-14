package com.nickwoluff.wearcalculator;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Transformation;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NavigationHistoryTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private Activity activity;
    private Activity child;
    private String savedHistory;
    private boolean savedAutoCheck;

    @Before public void saveHistory() {
        savedAutoCheck = UpdatePreferences.autoCheck(context);
        UpdatePreferences.setAutoCheck(context, false);
        savedHistory = context.getSharedPreferences("calculator_history", 0).getString("entries", "[]");
        context.getSharedPreferences("calculator_history", 0).edit().clear().commit();
    }

    @After public void cleanup() {
        instrumentation.runOnMainSync(() -> {
            if (child != null) child.finish();
            if (activity != null) activity.finish();
        });
        context.getSharedPreferences("calculator_history", 0).edit().putString("entries", savedHistory).commit();
        UpdatePreferences.setAutoCheck(context, savedAutoCheck);
    }

    private void launch(Class<? extends Activity> target) {
        Intent intent = new Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();
        long deadline = SystemClock.uptimeMillis() + 8000;
        while (SystemClock.uptimeMillis() < deadline) {
            boolean[] focused = {false};
            instrumentation.runOnMainSync(() -> focused[0] = activity.hasWindowFocus());
            if (focused[0]) break;
            SystemClock.sleep(100);
        }
        SystemClock.sleep(700);
    }

    @Test public void storesOnlyTenNewestRecordsAndRecoversCorruptStorage() {
        for (int i = 0; i < 12; i++) HistoryStore.add(context, i + "+1", String.valueOf(i + 1));
        List<HistoryStore.Entry> entries = HistoryStore.read(context);
        assertEquals(10, entries.size());
        assertEquals("2+1", entries.get(0).equation);
        assertEquals("12", entries.get(9).result);
        context.getSharedPreferences("calculator_history", 0).edit().putString("entries", "invalid").commit();
        HistoryStore.add(context, "1+1", "2");
        assertEquals(1, HistoryStore.read(context).size());
    }

    @Test public void historyStartsAtNewestAndRowsScrollIndependently() {
        for (int i = 0; i < 10; i++) {
            HistoryStore.add(context, "123456789012345678901234567890+" + i,
                    "987654321098765432109876543210" + i);
        }
        launch(HistoryActivity.class);
        instrumentation.runOnMainSync(() -> {
            ViewGroup entries = activity.findViewById(R.id.historyEntries);
            assertEquals(19, entries.getChildCount());
            View newest = entries.getChildAt(18);
            assertTrue(((TextView) newest.findViewById(R.id.historyEquation)).getText().toString().endsWith("+9="));
            HorizontalScrollView equation = newest.findViewById(R.id.historyEquationScroll);
            HorizontalScrollView result = newest.findViewById(R.id.historyResultScroll);
            int resultX = result.getScrollX();
            assertTrue(equation.getScrollX() > 0);
            equation.scrollTo(0, 0);
            assertEquals(resultX, result.getScrollX());
            ScrollView scroll = activity.findViewById(R.id.historyScroll);
            assertTrue(scroll.getScrollY() > 0);
            scroll.scrollBy(0, -1);
            assertEquals(0f, activity.findViewById(R.id.curvedTime).getAlpha(), 0.01f);
            scroll.scrollTo(0, 0);
            assertEquals(1f, activity.findViewById(R.id.curvedTime).getAlpha(), 0.01f);
            scroll.scrollTo(0, 100);
            assertEquals(0f, activity.findViewById(R.id.curvedTime).getAlpha(), 0.01f);
        });
        SystemClock.sleep(2400);
        instrumentation.runOnMainSync(() -> assertEquals(1f,
                activity.findViewById(R.id.curvedTime).getAlpha(), 0.01f));
    }

    @Test public void pageTransitionsEnterFromGestureDirectionAndReturnInReverse() {
        for (int id : new int[]{R.anim.settings_enter, R.anim.history_enter,
                R.anim.settings_exit, R.anim.history_exit}) {
            Animation animation = AnimationUtils.loadAnimation(context, id);
            animation.initialize(200, 200, 200, 200);
            animation.setStartTime(0);
            Transformation transform = new Transformation();
            animation.getTransformation(0, transform);
            float[] matrix = new float[9];
            transform.getMatrix().getValues(matrix);
            assertEquals(id == R.anim.settings_enter ? 200f : id == R.anim.history_enter ? -200f : 0f,
                    matrix[5], 0.01f);
            animation.getTransformation(650, transform);
            transform.getMatrix().getValues(matrix);
            assertEquals(id == R.anim.settings_exit ? 200f : id == R.anim.history_exit ? -200f : 0f,
                    matrix[5], 0.01f);
        }
    }

    @Test public void bottomSwipeOpensSettingsAndTimeLayerPassesThroughTouches() {
        launch(MainActivity.class);
        instrumentation.runOnMainSync(() -> {
            FloatingTimeView time = activity.findViewById(R.id.curvedTime);
            assertFalse(time.getTimeText().isEmpty());
            assertSame(activity.findViewById(R.id.buttonArea).getRootView(), time.getRootView());
            activity.findViewById(R.id.btn1).performClick();
            activity.findViewById(R.id.btnAdd).performClick();
            activity.findViewById(R.id.btn1).performClick();
            activity.findViewById(R.id.btnEq).performClick();
            assertEquals("2", ((TextView) activity.findViewById(R.id.tvDisplay)).getText().toString());
            assertEquals(1, HistoryStore.read(context).size());
        });
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(SettingsActivity.class.getName(), null, false);
        View root = activity.getWindow().getDecorView();
        swipe(root.getWidth() / 2f, root.getHeight() - 8f, root.getHeight() - 130f);
        child = instrumentation.waitForMonitorWithTimeout(monitor, 3000);
        instrumentation.removeMonitor(monitor);
        assertNotNull(child);
        instrumentation.runOnMainSync(() -> assertNotNull(((SettingsActivity) child).getUiForTesting().getView()));
    }

    @Test public void displaySwipeOpensHistoryWithoutChangingExpression() {
        launch(MainActivity.class);
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(HistoryActivity.class.getName(), null, false);
        View display = activity.findViewById(R.id.displayArea);
        int[] location = new int[2];
        instrumentation.runOnMainSync(() -> display.getLocationOnScreen(location));
        float y = location[1] + display.getHeight() / 2f;
        swipe(display.getWidth() / 2f, y, y + 100);
        child = instrumentation.waitForMonitorWithTimeout(monitor, 3000);
        instrumentation.removeMonitor(monitor);
        assertNotNull(child);
        instrumentation.runOnMainSync(() -> assertEquals("0",
                ((TextView) activity.findViewById(R.id.tvDisplay)).getText().toString()));
    }

    @Test public void scientificButtonsCalculateFunctionsPowersAndFactorials() {
        launch(MainActivity.class);
        instrumentation.runOnMainSync(() -> {
            assertCalculation("8", R.id.btn2, R.id.btnPower, R.id.btn3);
            assertCalculation("3", R.id.btnSqrt, R.id.btn9, R.id.btnRightParen);
            assertCalculation("2", R.id.btnLg, R.id.btn1, R.id.btn0, R.id.btn0, R.id.btnRightParen);
            assertCalculation("1", R.id.btnLn, R.id.btnE, R.id.btnRightParen);
            assertCalculation("120", R.id.btn5, R.id.btnFactorial);
            assertCalculation("0", R.id.btnSin, R.id.btn0, R.id.btnRightParen);
            activity.findViewById(R.id.btnSecond).performClick();
            assertCalculation("0", R.id.btnSin, R.id.btn0, R.id.btnRightParen);
            assertCalculation("Error", R.id.btnLn, R.id.btn0, R.id.btnRightParen);
            assertEquals(7, HistoryStore.read(context).size());
        });
    }

    @Test public void historySwipesUpToExitOnlyAtNewestRecords() {
        for (int i = 0; i < 10; i++) HistoryStore.add(context, i + "+1", String.valueOf(i + 1));
        launch(HistoryActivity.class);
        ScrollView scroll = activity.findViewById(R.id.historyScroll);
        instrumentation.runOnMainSync(() -> scroll.scrollTo(0, 0));
        View root = activity.getWindow().getDecorView();
        swipe(root.getWidth() / 2f, root.getHeight() * 0.75f, root.getHeight() * 0.4f);
        instrumentation.runOnMainSync(() -> {
            assertFalse(activity.isFinishing());
            assertTrue(scroll.getScrollY() > 0);
            scroll.setSmoothScrollingEnabled(false);
        });
        SystemClock.sleep(700);
        instrumentation.runOnMainSync(() -> scroll.fullScroll(View.FOCUS_DOWN));
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> assertFalse(scroll.canScrollVertically(1)));
        swipe(root.getWidth() / 2f, root.getHeight() * 0.75f, root.getHeight() * 0.4f);
        instrumentation.runOnMainSync(() -> assertTrue(activity.isFinishing()));
    }

    @Test public void settingsSwipesDownToExitAndShortDragDoesNotExit() {
        boolean savedRound = LayoutPreferences.isRound(context);
        try {
            for (boolean round : new boolean[]{false, true}) {
                LayoutPreferences.setRound(context, round);
                launch(SettingsActivity.class);
                View root = activity.getWindow().getDecorView();
                float y = root.getHeight() * 0.4f;
                swipe(root.getWidth() / 2f, y, y + 30);
                instrumentation.runOnMainSync(() -> assertFalse(activity.isFinishing()));
                // 显示更新结果，确保折叠后的设置列表仍有可滚动内容。
                instrumentation.runOnMainSync(() -> ((SettingsActivity) activity).getUiForTesting().showRelease("v1.2.0"));
                instrumentation.waitForIdleSync();
                // 先向列表内部滚动，再下滑恢复顶部；这一步不能关闭页面。
                swipe(root.getWidth() / 2f, root.getHeight() * 0.75f, y);
                SystemClock.sleep(700);
                instrumentation.runOnMainSync(() -> {
                    assertFalse(activity.isFinishing());
                    assertFalse(((SettingsActivity) activity).canSwipeDownToExit());
                });
                swipe(root.getWidth() / 2f, y, root.getHeight() * 0.75f);
                SystemClock.sleep(700);
                instrumentation.runOnMainSync(() -> assertFalse(activity.isFinishing()));
                // 列表项数量可变化，按实际滚动状态恢复顶部。
                boolean[] atStart = {false};
                for (int attempt = 0; attempt < 8; attempt++) {
                    instrumentation.runOnMainSync(() -> atStart[0] =
                            ((SettingsActivity) activity).canSwipeDownToExit());
                    if (atStart[0]) break;
                    swipe(root.getWidth() / 2f, y, root.getHeight() * 0.75f);
                    SystemClock.sleep(700);
                    instrumentation.runOnMainSync(() -> assertFalse(activity.isFinishing()));
                }
                instrumentation.runOnMainSync(() ->
                        assertTrue(((SettingsActivity) activity).canSwipeDownToExit()));
                // 回到列表顶部后继续下滑退出。
                swipe(root.getWidth() / 2f, y, root.getHeight() * 0.9f);
                SystemClock.sleep(700);
                instrumentation.runOnMainSync(() -> assertTrue(activity.isFinishing()));
            }
        } finally {
            LayoutPreferences.setRound(context, savedRound);
        }
    }

    private void assertCalculation(String expected, int... keys) {
        activity.findViewById(R.id.btnAc).performClick();
        for (int id : keys) activity.findViewById(id).performClick();
        activity.findViewById(R.id.btnScientificEq).performClick();
        assertEquals(expected, ((TextView) activity.findViewById(R.id.tvDisplay)).getText().toString());
    }

    private void swipe(float x, float fromY, float toY) {
        long start = SystemClock.uptimeMillis();
        send(start, MotionEvent.ACTION_DOWN, x, fromY);
        for (int i = 1; i <= 12; i++) {
            SystemClock.sleep(20);
            send(start, MotionEvent.ACTION_MOVE, x, fromY + (toY - fromY) * i / 12);
        }
        send(start, MotionEvent.ACTION_UP, x, toY);
        instrumentation.waitForIdleSync();
    }

    private void send(long start, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0);
        instrumentation.sendPointerSync(event);
        event.recycle();
    }
}
