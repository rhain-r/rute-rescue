package com.rutarescue.ui.common.chart;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.rutarescue.R;

import java.util.List;

/**
 * Vertical columns over time ("Activity • last 14 days"). Thin columns with rounded tops sit on
 * one baseline with two faint grid lines; the day number is under each column. The busiest day is
 * labelled with its value; tapping a column selects it (darker colour, value on top) and tells the
 * screen, which writes the full date and count under the chart.
 */
public class ColumnChartView extends ChartView {

    /** Told which column the user tapped. */
    public interface OnColumnSelected {
        void onColumnSelected(int index, ChartEntry entry);
    }

    private static final float HEIGHT_DP = 168;
    private static final float TOP_LABEL_DP = 18;
    private static final float BOTTOM_LABEL_DP = 20;

    private int selected = -1;
    private OnColumnSelected listener;

    public ColumnChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setOnColumnSelected(OnColumnSelected newListener) {
        listener = newListener;
    }

    @Override
    protected void onEntriesChanged() {
        selected = -1;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(Math.round(HEIGHT_DP * dp), heightMeasureSpec));
    }

    @Override
    protected void drawChart(Canvas canvas) {
        List<ChartEntry> entries = getEntries();
        double max = maxValue();
        float plotTop = TOP_LABEL_DP * dp;
        float plotBottom = getHeight() - BOTTOM_LABEL_DP * dp;
        float plotHeight = plotBottom - plotTop;

        // Faint grid: baseline, half and top, with the top value at the left edge.
        for (float fraction : new float[]{0f, 0.5f, 1f}) {
            float y = plotBottom - plotHeight * fraction;
            canvas.drawLine(0, y, getWidth(), y, trackPaint);
        }
        canvas.drawText(format(max), 0, plotTop - 4 * dp, labelPaint);

        float slot = getWidth() / (float) entries.size();
        float columnWidth = Math.min(18 * dp, slot * 0.6f);
        boolean everyOtherLabel = slot < 18 * dp;
        int labelled = selected >= 0 ? selected : busiest(entries);
        for (int i = 0; i < entries.size(); i++) {
            ChartEntry entry = entries.get(i);
            float centerX = slot * i + slot / 2f;
            if (entry.getValue() > 0) {
                float top = plotBottom - (float) (plotHeight * entry.getValue() / max);
                box.set(centerX - columnWidth / 2f, Math.min(top, plotBottom - 2 * dp), centerX + columnWidth / 2f, plotBottom);
                markPaint.setColor(color(i == selected ? R.color.rr_navy : entry.getColor()));
                drawBar(canvas, box, true, markPaint);
            }
            if (i == labelled && entry.getValue() > 0) {
                String value = entry.getValueText();
                float y = plotBottom - (float) (plotHeight * entry.getValue() / max) - 4 * dp;
                float x = Math.max(0, Math.min(getWidth() - valuePaint.measureText(value), centerX - valuePaint.measureText(value) / 2f));
                canvas.drawText(value, x, Math.max(valuePaint.getTextSize(), y), valuePaint);
            }
            if (!everyOtherLabel || (entries.size() - 1 - i) % 2 == 0) {
                String label = entry.getLabel();
                canvas.drawText(label, centerX - labelPaint.measureText(label) / 2f,
                        plotBottom + 14 * dp, labelPaint);
            }
        }
    }

    private static int busiest(List<ChartEntry> entries) {
        int best = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (best < 0 || entries.get(i).getValue() >= entries.get(best).getValue()) best = i;
        }
        return best;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }

    @SuppressLint("ClickableViewAccessibility") // performClick() is called on ACTION_UP
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!hasData()) return false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (event.getAction() == MotionEvent.ACTION_UP) {
            List<ChartEntry> entries = getEntries();
            int index = (int) (event.getX() / (getWidth() / (float) entries.size()));
            index = Math.max(0, Math.min(entries.size() - 1, index));
            selected = index;
            invalidate();
            if (listener != null) listener.onColumnSelected(index, entries.get(index));
            performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
