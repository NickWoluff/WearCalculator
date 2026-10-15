package com.nickwoluff.wearcalculator;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import androidx.activity.ComponentActivity;

/** 所有页面共享独立于内容测量的时间覆盖层，触摸穿透到底下的内容。 */
public abstract class WearActivity extends ComponentActivity {
    private boolean roundLayout;
    private Appearance appearance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FloatingTimeView time;
    private float exitDownX;
    private float exitDownY;
    private boolean exitEligible;
    private boolean exitDragging;
    private boolean exitDirectionLocked;
    private float rotaryAccumulator;
    private final Runnable showTime = () -> {
        if (time != null) time.animate().alpha(1f).translationY(0).setDuration(250).start();
    };

    @Override
    protected void attachBaseContext(Context base) {
        Context configured = LayoutPreferences.applyTo(base);
        roundLayout = configured.getResources().getConfiguration().isScreenRound();
        super.attachBaseContext(configured);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        appearance = AppAppearance.read(this);
        if (Build.VERSION.SDK_INT >= 34 && !(this instanceof MainActivity)) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, enterAnimation(), R.anim.page_hold);
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.page_hold, exitAnimation());
        }
    }

    private int enterAnimation() {
        return this instanceof HistoryActivity ? R.anim.history_enter : R.anim.settings_enter;
    }

    private int exitAnimation() {
        return this instanceof HistoryActivity ? R.anim.history_exit : R.anim.settings_exit;
    }

    @Override
    public void finish() {
        super.finish();
        if (Build.VERSION.SDK_INT < 34 && !(this instanceof MainActivity)) {
            overridePendingTransition(R.anim.page_hold, exitAnimation());
        }
    }

    @Override
    public void setContentView(int layoutResId) {
        FrameLayout frame = new FrameLayout(this);
        getLayoutInflater().inflate(layoutResId, frame, true);
        time = new FloatingTimeView(this);
        time.setId(R.id.curvedTime);
        frame.addView(time, new FrameLayout.LayoutParams(-1, -1));
        super.setContentView(frame);
        ScrollView scroll = findViewById(R.id.historyScroll);
        if (scroll != null) {
            // 对应 ScreenScaffold 的 scrollAway：滚动时向上移出并淡出时间。
            scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
                time.animate().cancel();
                float progress = Math.min(1f, y / dp(32));
                time.setAlpha(1f - progress);
                time.setTranslationY(-dp(20) * progress);
                handler.removeCallbacks(showTime);
                handler.postDelayed(showTime, 2000);
            });
            scroll.setOnGenericMotionListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_SCROLL
                        && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                    v.scrollBy(0, Math.round(-event.getAxisValue(MotionEvent.AXIS_SCROLL) * dp(48)));
                    return true;
                }
                return false;
            });
            scroll.setFocusable(true);
            scroll.requestFocus();
        }
    }

    protected float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL
                && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            int axis = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? MotionEvent.AXIS_SCROLL : MotionEvent.AXIS_VSCROLL;
            float delta = -event.getAxisValue(axis);
            if (delta != 0f) {
                rotaryAccumulator += delta;
                if (Math.abs(rotaryAccumulator) >= CalculatorHaptics.ROTARY_THRESHOLD) {
                    View decor = getWindow().getDecorView();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        decor.performHapticFeedback(18);
                    } else {
                        decor.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                    rotaryAccumulator = 0f;
                }
            }
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!(this instanceof HistoryActivity) && !(this instanceof SettingsActivity)) {
            return super.dispatchTouchEvent(event);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            exitDownX = event.getX();
            exitDownY = event.getY();
            exitDragging = false;
            exitDirectionLocked = false;
            // 历史初始位于最新记录（底部），设置初始位于列表顶部。
            // 只有继续向边界外滑动才退出，保留查看旧记录和设置项的滚动。
            ScrollView history = findViewById(R.id.historyScroll);
            exitEligible = this instanceof HistoryActivity
                    ? history != null && !history.canScrollVertically(1)
                    : ((SettingsActivity) this).canSwipeDownToExit();
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && exitEligible) {
            float dx = event.getX() - exitDownX;
            float dy = event.getY() - exitDownY;
            int slop = ViewConfiguration.get(this).getScaledTouchSlop();
            if (!exitDirectionLocked && Math.max(Math.abs(dx), Math.abs(dy)) > slop) {
                exitDirectionLocked = true;
                boolean expectedDirection = this instanceof HistoryActivity ? dy < 0 : dy > 0;
                exitDragging = expectedDirection && Math.abs(dy) > Math.abs(dx) * 1.2f;
                if (exitDragging) {
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                }
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            exitEligible = false;
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP && exitDragging) {
            float distance = this instanceof HistoryActivity
                    ? exitDownY - event.getY() : event.getY() - exitDownY;
            exitDragging = false;
            exitEligible = false;
            if (distance >= dp(48)) finish();
            return true;
        } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            exitDragging = false;
            exitEligible = false;
        }
        return exitDragging || super.dispatchTouchEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 设置页修改布局后，返回的页面也重新加载对应的 XML 与时间形状。
        if (roundLayout != LayoutPreferences.isRound(this)
                || !appearance.equals(AppAppearance.read(this))) {
            recreate();
            return;
        }
        handler.post(showTime);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacksAndMessages(null);
        if (time != null) time.animate().cancel();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (time != null) time.animate().cancel();
        super.onDestroy();
    }
}
