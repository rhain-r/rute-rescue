package com.rutarescue.ui.common.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Horizontal bars, one row per entry ("Changes by kind", "Center occupancy"): the label on the
 * left and the value on the right above a thin bar on a light track. Every row shows its number,
 * so the colour never has to carry the meaning alone.
 */
public class BarListView extends ChartView {

    private static final float ROW_DP = 44;
    private static final float BAR_DP = 8;
    private static final float LAST_ROW_TRIM_DP = 12;

    /** 0 = scale to the largest entry; otherwise the value that fills a whole bar (e.g. 1.0 = 100%). */
    private double fixedMax;

    public BarListView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /** Makes a whole bar mean this value (occupancy: 1.0 = full), instead of the largest entry. */
    public void setFixedMax(double max) {
        fixedMax = max;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int rows = Math.max(1, getEntries().size());
        // The last row needs no space under its bar.
        int height = hasData() ? Math.round((rows * ROW_DP - LAST_ROW_TRIM_DP) * dp) : Math.round(48 * dp);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void drawChart(Canvas canvas) {
        List<ChartEntry> entries = getEntries();
        double max = fixedMax > 0 ? fixedMax : maxValue();
        float width = getWidth();
        float barHeight = BAR_DP * dp;
        for (int i = 0; i < entries.size(); i++) {
            ChartEntry entry = entries.get(i);
            float rowTop = i * ROW_DP * dp;
            float textY = rowTop + valuePaint.getTextSize();

            String value = entry.getValueText();
            float valueWidth = valuePaint.measureText(value);
            canvas.drawText(value, width - valueWidth, textY, valuePaint);
            canvas.drawText(fit(entry.getLabel(), labelPaint, width - valueWidth - 12 * dp), 0, textY, labelPaint);

            float barTop = textY + 7 * dp;
            box.set(0, barTop, width, barTop + barHeight);
            drawBar(canvas, box, false, trackPaint); // the track is anchored at the left like the bar
            double fraction = Math.min(1, entry.getValue() / max);
            if (fraction > 0) {
                box.set(0, barTop, Math.max(barHeight, (float) (width * fraction)), barTop + barHeight);
                markPaint.setColor(color(entry.getColor()));
                drawBar(canvas, box, false, markPaint);
            }
        }
    }
}
