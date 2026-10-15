package com.rutarescue.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.rutarescue.R;
import com.rutarescue.model.center.OccupancyLevel;

/**
 * The Phase 1 capacity gauge: a 12 dp rounded bar filled up to the headcount, green under 70%,
 * amber from 70% to 90% and red above 90%, with white marks at 70% and 90%.
 *
 * Inheritance: a plain View that only draws itself (no children, no touch handling).
 */
public class CapacityGaugeView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float fraction;

    public CapacityGaugeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        track.setColor(ContextCompat.getColor(context, R.color.rr_surface_secondary));
        tick.setColor(ContextCompat.getColor(context, R.color.white));
        tickEdge.setColor(ContextCompat.getColor(context, R.color.rr_divider));
        setLevel(0, OccupancyLevel.GREEN);
    }

    /** @param newFraction headcount divided by capacity (values above 1 fill the whole bar). */
    public void setLevel(double newFraction, OccupancyLevel level) {
        fraction = (float) Math.max(0, Math.min(1, newFraction));
        int color;
        switch (level) {
            case RED: color = R.color.hazard_high; break;
            case AMBER: color = R.color.rr_gauge_amber; break;
            default: color = R.color.rr_green;
        }
        fill.setColor(ContextCompat.getColor(getContext(), color));
        setContentDescription(Math.round(newFraction * 100) + " percent full");
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float barHeight = 12 * density;
        float top = (getHeight() - barHeight) / 2f;
        float radius = barHeight / 2f;
        box.set(0, top, getWidth(), top + barHeight);
        canvas.drawRoundRect(box, radius, radius, track);
        if (fraction > 0) {
            box.set(0, top, Math.max(barHeight, getWidth() * fraction), top + barHeight);
            canvas.drawRoundRect(box, radius, radius, fill);
        }
        // White marks at the 70% and 90% thresholds, 2 dp wide and 18 dp tall, with a thin edge.
        float markHeight = 18 * density;
        float markTop = (getHeight() - markHeight) / 2f;
        for (double threshold : new double[]{OccupancyLevel.AMBER_FROM, OccupancyLevel.RED_ABOVE}) {
            float x = (float) (getWidth() * threshold);
            box.set(x - 1.5f * density, markTop - 0.5f * density, x + 1.5f * density, markTop + markHeight + 0.5f * density);
            canvas.drawRoundRect(box, density, density, tickEdge);
            box.set(x - density, markTop, x + density, markTop + markHeight);
            canvas.drawRoundRect(box, density, density, tick);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = Math.round(20 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }
}
