package com.rutarescue.ui.common.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorRes;
import androidx.annotation.FontRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.rutarescue.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parent class of the report charts (ColumnChartView, BarListView, DonutChartView).
 *
 * Inheritance: every chart holds a list of ChartEntry values, shows "No data yet" when the
 * list is empty or all zero, and reads its text from the app's fonts and text colours. The
 * subclasses only decide how the entries are drawn (abstract drawChart) and how tall they are.
 * Text always uses the text colours; only the marks (bars, slices) use the entry colour.
 */
public abstract class ChartView extends View {

    protected final float dp;
    /** Small labels under/next to the marks (Karla 11 sp, secondary text colour). */
    protected final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Values and the main text (Karla SemiBold 12 sp, primary text colour). */
    protected final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Recessive grid lines and empty bar tracks. */
    protected final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Reused for every coloured mark. */
    protected final Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final RectF box = new RectF();
    private final Path path = new Path();

    private List<ChartEntry> entries = new ArrayList<>();
    private String emptyText = "No data yet";

    protected ChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        labelPaint.setTextSize(11 * sp);
        labelPaint.setColor(color(R.color.rr_text_secondary));
        labelPaint.setTypeface(font(R.font.karla_regular));
        valuePaint.setTextSize(12 * sp);
        valuePaint.setColor(color(R.color.rr_text));
        valuePaint.setTypeface(font(R.font.karla_semibold));
        trackPaint.setColor(color(R.color.rr_surface_secondary));
        trackPaint.setStrokeWidth(dp);
    }

    /** Replaces the chart data and redraws; the content description reads the same numbers aloud. */
    public void setEntries(List<ChartEntry> newEntries) {
        entries = newEntries == null ? new ArrayList<>() : new ArrayList<>(newEntries);
        StringBuilder spoken = new StringBuilder();
        for (ChartEntry entry : entries) {
            if (spoken.length() > 0) spoken.append(", ");
            spoken.append(entry.getLabel()).append(' ').append(entry.getValueText());
        }
        setContentDescription(hasData() ? spoken.toString() : emptyText);
        onEntriesChanged();
        requestLayout();
        invalidate();
    }

    public void setEmptyText(String text) {
        emptyText = text;
        invalidate();
    }

    protected List<ChartEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /** True when at least one entry is above zero. */
    public boolean hasData() {
        for (ChartEntry entry : entries) {
            if (entry.getValue() > 0) return true;
        }
        return false;
    }

    /** The largest value, used to scale the bars (never 0, so nothing divides by zero). */
    protected double maxValue() {
        double max = 0;
        for (ChartEntry entry : entries) max = Math.max(max, entry.getValue());
        return max <= 0 ? 1 : max;
    }

    /** Subclasses that keep extra state (for example a selected column) reset it here. */
    protected void onEntriesChanged() {
    }

    /** Draws the marks; only called when there is something to draw. */
    protected abstract void drawChart(Canvas canvas);

    @Override
    protected final void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (hasData()) {
            drawChart(canvas);
        } else {
            float y = getHeight() / 2f - (labelPaint.ascent() + labelPaint.descent()) / 2f;
            canvas.drawText(emptyText, (getWidth() - labelPaint.measureText(emptyText)) / 2f, y, labelPaint);
        }
    }

    /**
     * A bar with 4 dp rounded corners only at its data end, so it stays anchored to the baseline.
     * @param vertical true for a column (rounded top), false for a horizontal bar (rounded right)
     */
    protected void drawBar(Canvas canvas, RectF bar, boolean vertical, Paint paint) {
        float r = Math.min(4 * dp, Math.min(bar.width(), bar.height()) / 2f);
        float[] radii = vertical
                ? new float[]{r, r, r, r, 0, 0, 0, 0}
                : new float[]{0, 0, r, r, r, r, 0, 0};
        path.reset();
        path.addRoundRect(bar, radii, Path.Direction.CW);
        canvas.drawPath(path, paint);
    }

    /** Shortens a label with "…" so it fits the given width. */
    protected String fit(String text, Paint paint, float width) {
        if (paint.measureText(text) <= width) return text;
        String shortened = text;
        while (shortened.length() > 1 && paint.measureText(shortened + "…") > width) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened.strip() + "…";
    }

    protected int color(@ColorRes int res) {
        return ContextCompat.getColor(getContext(), res);
    }

    private Typeface font(@FontRes int res) {
        try {
            Typeface typeface = ResourcesCompat.getFont(getContext(), res);
            return typeface == null ? Typeface.DEFAULT : typeface;
        } catch (RuntimeException notAvailable) {
            return Typeface.DEFAULT; // layout preview
        }
    }
}
