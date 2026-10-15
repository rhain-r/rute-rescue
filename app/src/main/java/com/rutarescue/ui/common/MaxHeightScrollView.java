package com.rutarescue.ui.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.widget.NestedScrollView;

/**
 * A scroll view that grows with its content up to a maximum height, then scrolls inside.
 *
 * Used for the floating map cards (Figma "Safe Route Bottom Sheet" and "Hazard Layer Bottom
 * Sheet"): the card keeps all four rounded corners visible above the navigation bar, as drawn in
 * Figma, while longer content (route alternatives, explanations) scrolls inside the card.
 *
 * The limit is the smaller of android:maxHeight and {@link #MAX_SHARE_OF_PARENT} of the space the
 * parent offers, so the map stays visible on small phones too.
 * Inheritance: extends NestedScrollView and only changes how its height is measured.
 */
public class MaxHeightScrollView extends NestedScrollView {

    private static final float MAX_SHARE_OF_PARENT = 0.55f;

    private int maxHeight = Integer.MAX_VALUE;

    public MaxHeightScrollView(Context context) {
        this(context, null);
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (attrs != null) {
            TypedArray values = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.maxHeight});
            try {
                maxHeight = values.getDimensionPixelSize(0, Integer.MAX_VALUE);
            } finally {
                values.recycle();
            }
        }
    }

    public void setMaxHeight(int maxHeightPx) {
        this.maxHeight = maxHeightPx;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int limit = maxHeight;
        int offered = View.MeasureSpec.getSize(heightMeasureSpec);
        if (View.MeasureSpec.getMode(heightMeasureSpec) != View.MeasureSpec.UNSPECIFIED && offered > 0) {
            limit = Math.min(limit, (int) (offered * MAX_SHARE_OF_PARENT));
        }
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST));
    }
}
