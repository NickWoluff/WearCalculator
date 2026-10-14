package com.nickwoluff.wearcalculator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** 随翻页进度连续改变大小与强调色的双页圆点指示器。 */
public class PageIndicatorView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final int activeColor;
    private final int inactiveColor;
    private final boolean compact;
    private float pagePosition;

    public PageIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        compact = !getResources().getConfiguration().isScreenRound();
        boolean classic = AppAppearance.read(context).getAccent() == AppAppearanceKt.CLASSIC_ACCENT;
        activeColor = classic ? context.getColor(R.color.page_indicator_active) : AppAppearance.primary(context);
        inactiveColor = classic ? context.getColor(R.color.page_indicator_inactive) : AppAppearance.outlineVariant(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setCurrentPage(0);
    }

    public void setCurrentPage(int page) {
        setContentDescription(getResources().getString(R.string.page_indicator_description,
                Math.max(0, Math.min(1, page)) + 1));
    }

    public void setPagePosition(float position) {
        pagePosition = Math.max(0f, Math.min(1f, position));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float centerY = getHeight() / 2f;
        float spacing = (compact ? 8f : 12f) * density;
        float firstX = getWidth() / 2f - spacing / 2f;
        for (int i = 0; i < 2; i++) {
            float emphasis = 1f - Math.abs(pagePosition - i);
            paint.setColor(androidx.core.graphics.ColorUtils.blendARGB(inactiveColor, activeColor, emphasis));
            float radius = compact ? 1.5f + 0.5f * emphasis : 2f + emphasis;
            canvas.drawCircle(firstX + i * spacing, centerY, radius * density, paint);
        }
    }
}
