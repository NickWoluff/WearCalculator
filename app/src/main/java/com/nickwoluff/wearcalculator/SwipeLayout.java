package com.nickwoluff.wearcalculator;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import java.util.Collections;

/** 仅在按钮区域内拖动翻页，保留按钮点击和顶部显示区的独立滚动。 */
public class SwipeLayout extends FrameLayout {

    private final int touchSlop;
    private final int minimumFlingVelocity;
    private View basicPage;
    private View scientificPage;
    private PageIndicatorView indicator;
    private float downX;
    private float downY;
    private float pagePosition;
    private int currentPage;
    private boolean dragging;
    private VelocityTracker velocityTracker;
    private ValueAnimator animator;

    public SwipeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        ViewConfiguration configuration = ViewConfiguration.get(context);
        touchSlop = configuration.getScaledPagingTouchSlop();
        minimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        setClickable(true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        basicPage = findViewById(R.id.basicButtons);
        scientificPage = findViewById(R.id.scientificButtons);
        indicator = findViewById(R.id.pageIndicator);
        updatePagePosition(currentPage);
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int page) {
        if (animator != null) animator.cancel();
        currentPage = Math.max(0, Math.min(1, page));
        updatePagePosition(currentPage);
        if (indicator != null) indicator.setCurrentPage(currentPage);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updatePagePosition(currentPage);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 系统手势排除矩形只覆盖按钮区，顶部区域仍由系统正常处理。
            setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, width, height)));
        }
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        // Wear 的滑动关闭容器会查询此方法；页首右滑也应留在按钮区。
        return direction != 0;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            dragging = false;
            requestDisallowInterceptTouchEvent(true);
            if (velocityTracker != null) velocityTracker.recycle();
            velocityTracker = VelocityTracker.obtain();
        }
        if (velocityTracker != null) velocityTracker.addMovement(event);
        boolean handled = super.dispatchTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            requestDisallowInterceptTouchEvent(false);
            if (velocityTracker != null) velocityTracker.recycle();
            velocityTracker = null;
        }
        return handled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (animator != null && animator.isRunning()) return true;
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                dragging = true;
                return true;
            }
        }
        return dragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (animator != null && animator.isRunning()) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) dragging = true;
                if (dragging && getWidth() > 0) {
                    updatePagePosition(currentPage - (event.getX() - downX) / getWidth());
                }
                break;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    float distance = event.getX() - downX;
                    float velocity = 0f;
                    if (velocityTracker != null) {
                        velocityTracker.computeCurrentVelocity(1000);
                        velocity = velocityTracker.getXVelocity();
                    }
                    int targetPage = Math.round(pagePosition);
                    if (Math.abs(distance) > touchSlop && Math.abs(velocity) > minimumFlingVelocity) {
                        targetPage = velocity < 0 ? currentPage + 1 : currentPage - 1;
                    }
                    settleToPage(Math.max(0, Math.min(1, targetPage)));
                } else {
                    performClick();
                }
                dragging = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) settleToPage(currentPage);
                dragging = false;
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void settleToPage(int targetPage) {
        animator = ValueAnimator.ofFloat(pagePosition, targetPage);
        animator.setDuration(280L);
        animator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        animator.addUpdateListener(animation -> {
            updatePagePosition((float) animation.getAnimatedValue());
            if (animation.getAnimatedFraction() == 1f) {
                currentPage = targetPage;
                indicator.setCurrentPage(targetPage);
            }
        });
        animator.start();
    }

    private void updatePagePosition(float position) {
        pagePosition = Math.max(0f, Math.min(1f, position));
        if (basicPage == null || scientificPage == null) return;
        basicPage.setTranslationX(-pagePosition * getWidth());
        scientificPage.setTranslationX((1f - pagePosition) * getWidth());
        // INVISIBLE 仍参与测量，避免第二页第一次切入时尺寸为零。
        basicPage.setVisibility(pagePosition < 1f ? VISIBLE : INVISIBLE);
        scientificPage.setVisibility(pagePosition > 0f ? VISIBLE : INVISIBLE);
        if (indicator != null) indicator.setPagePosition(pagePosition);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        if (velocityTracker != null) velocityTracker.recycle();
        velocityTracker = null;
        super.onDetachedFromWindow();
    }
}
