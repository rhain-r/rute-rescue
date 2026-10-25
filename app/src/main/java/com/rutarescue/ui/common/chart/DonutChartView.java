package com.rutarescue.ui.common.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * A thin ring split into parts of a whole ("Ligtas applications by status"), with the total in
 * the middle and a legend on the right (colour dot, label, count and share). Parts are separated
 * by a 2 dp gap, and every part is named in the legend, so colour is never the only clue.
 */
public class DonutChartView extends ChartView {

    private static final float SIZE_DP = 132;
    private static final float RING_DP = 16;
    private static final float GAP_DP = 2;
    private static final float LEGEND_ROW_DP = 26;

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint totalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String totalCaption = "total";

    public DonutChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(RING_DP * dp);
        totalPaint.set(valuePaint);
        totalPaint.setTextSize(valuePaint.getTextSize() * 1.8f);
    }

    /** The word under the total in the middle of the ring ("applications"). */
    public void setTotalCaption(String caption) {
        totalCaption = caption;
        invalidate();
    }

    /**
     * True when the key does not fit beside the ring (small phone or large text): the key then goes
     * under the ring, so no label is ever cut off.
     */
    private boolean stacked(int width) {
        float widest = 0;
        for (ChartEntry entry : getEntries()) {
            widest = Math.max(widest, labelPaint.measureText(entry.getLabel()) + valuePaint.measureText(entry.getValueText()));
        }
        float needed = SIZE_DP * dp + 20 * dp + 16 * dp + 12 * dp + widest;
        return width > 0 && needed > width;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float legend = getEntries().size() * LEGEND_ROW_DP;
        int height = Math.round((stacked(width) ? SIZE_DP + 12 + legend : Math.max(SIZE_DP, legend)) * dp);
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void drawChart(Canvas canvas) {
        List<ChartEntry> entries = getEntries();
        double total = 0;
        for (ChartEntry entry : entries) total += entry.getValue();
        boolean stacked = stacked(getWidth());

        // The ring, starting at 12 o'clock and going clockwise (centred on top when the key is below).
        float size = Math.min(SIZE_DP * dp, stacked ? SIZE_DP * dp : getHeight());
        float inset = RING_DP * dp / 2f;
        float left = stacked ? (getWidth() - size) / 2f : 0;
        float top = stacked ? 0 : (getHeight() - size) / 2f;
        box.set(left + inset, top + inset, left + size - inset, top + size - inset);
        float radius = box.width() / 2f;
        float gapDegrees = (float) Math.toDegrees(GAP_DP * dp / radius);
        int parts = 0;
        for (ChartEntry entry : entries) if (entry.getValue() > 0) parts++;
        float start = -90;
        for (ChartEntry entry : entries) {
            if (entry.getValue() <= 0) continue;
            float sweep = (float) (360 * entry.getValue() / total);
            float drawn = parts > 1 ? Math.max(0.5f, sweep - gapDegrees) : 360;
            ringPaint.setColor(color(entry.getColor()));
            canvas.drawArc(box, start + (parts > 1 ? gapDegrees / 2f : 0), drawn, false, ringPaint);
            start += sweep;
        }

        // The total in the middle.
        String totalText = String.valueOf(Math.round(total));
        float centerX = box.centerX();
        float centerY = box.centerY();
        canvas.drawText(totalText, centerX - totalPaint.measureText(totalText) / 2f, centerY + 2 * dp, totalPaint);
        canvas.drawText(totalCaption, centerX - labelPaint.measureText(totalCaption) / 2f,
                centerY + 2 * dp + labelPaint.getTextSize() + 2 * dp, labelPaint);

        // Key: dot, label, then "count · share" at the right edge - beside the ring, or below it.
        float legendLeft = stacked ? 0 : size + 20 * dp;
        float rowHeight = LEGEND_ROW_DP * dp;
        float legendTop = stacked ? size + 12 * dp : (getHeight() - entries.size() * rowHeight) / 2f;
        for (int i = 0; i < entries.size(); i++) {
            ChartEntry entry = entries.get(i);
            float rowCenter = legendTop + i * rowHeight + rowHeight / 2f;
            markPaint.setColor(color(entry.getColor()));
            canvas.drawCircle(legendLeft + 5 * dp, rowCenter, 5 * dp, markPaint);
            String value = entry.getValueText();
            float valueWidth = valuePaint.measureText(value);
            float textBaseline = rowCenter - (valuePaint.ascent() + valuePaint.descent()) / 2f;
            canvas.drawText(value, getWidth() - valueWidth, textBaseline, valuePaint);
            float labelSpace = getWidth() - valueWidth - legendLeft - 26 * dp;
            canvas.drawText(fit(entry.getLabel(), labelPaint, labelSpace), legendLeft + 16 * dp, textBaseline, labelPaint);
        }
    }
}
