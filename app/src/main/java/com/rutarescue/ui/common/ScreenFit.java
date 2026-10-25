package com.rutarescue.ui.common;

import android.view.View;
import android.view.ViewGroup;

/**
 * Keeps every screen fitted to the phone it runs on (2026-10-21), so nothing overlaps on small
 * phones, big phones or with large text.
 *
 * Instead of fixed distances (74 dp under the header, 94 dp above the bottom tabs, 90 dp under a
 * map title) the real sizes are measured on screen and applied:
 *  - a view tagged {@link #BELOW_HEADER} starts right under the tab's header (top padding);
 *  - a view tagged {@link #ABOVE_NAV} ends above the bottom tabs (bottom padding);
 *  - a view tagged {@link #ABOVE_NAV_MARGIN} floats above the bottom tabs (bottom margin);
 *  - {@link #keepBelow} keeps a floating button or banner under the views above it.
 * Views get these tags in the styles (RR.TabScroll, RR.ScreenColumn) or in the layout XML.
 * Every method only changes a value when it is different, so calling them on each layout is cheap.
 */
public final class ScreenFit {

    public static final String BELOW_HEADER = "fit:below_header";
    public static final String ABOVE_NAV = "fit:above_nav";
    public static final String ABOVE_NAV_MARGIN = "fit:above_nav_margin";

    private ScreenFit() {
    }

    /**
     * Applies the measured header and bottom-tab sizes to one tab.
     *
     * @param tabRoot  the tab's root view
     * @param navSpace how much the bottom tabs cover the tab, plus a gap (MainShellActivity measures it)
     */
    public static void fitTab(View tabRoot, int navSpace) {
        if (tabRoot == null) return;
        View header = tabRoot.findViewById(com.rutarescue.R.id.header);
        int headerBottom = header != null && header.getVisibility() == View.VISIBLE && header.getHeight() > 0
                ? header.getBottom() : -1;
        apply(tabRoot, headerBottom, navSpace);
    }

    private static void apply(View view, int headerBottom, int navSpace) {
        Object tag = view.getTag();
        if (BELOW_HEADER.equals(tag) && headerBottom >= 0) {
            setPadding(view, view.getPaddingLeft(), headerBottom, view.getPaddingRight(), view.getPaddingBottom());
        } else if (ABOVE_NAV.equals(tag)) {
            setPadding(view, view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), navSpace);
        } else if (ABOVE_NAV_MARGIN.equals(tag) && view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            if (params.bottomMargin != navSpace) {
                params.bottomMargin = navSpace;
                view.setLayoutParams(params);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) apply(group.getChildAt(i), headerBottom, navSpace);
        }
    }

    /**
     * Keeps a floating view (in a FrameLayout) just under the lowest of the given views that is showing,
     * and again whenever any of them changes size - e.g. the Locate button under a map title that grew
     * with large text. Views that are hidden are ignored.
     */
    public static void keepBelow(View follower, int gapDp, View... anchors) {
        Runnable update = () -> placeBelow(follower, gapDp, anchors);
        for (View anchor : anchors) {
            if (anchor != null) anchor.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> update.run());
        }
        follower.post(update);
    }

    /** One placement pass for {@link #keepBelow} (also usable directly, e.g. when the anchor changes). */
    public static void placeBelow(View follower, int gapDp, View... anchors) {
        if (!(follower.getParent() instanceof View) || !(follower.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        View parent = (View) follower.getParent();
        int[] at = new int[2];
        int lowest = Integer.MIN_VALUE;
        for (View anchor : anchors) {
            if (anchor == null || !anchor.isShown() || anchor.getHeight() == 0) continue;
            anchor.getLocationInWindow(at);
            lowest = Math.max(lowest, at[1] + anchor.getHeight());
        }
        if (lowest == Integer.MIN_VALUE) return;
        parent.getLocationInWindow(at);
        int gap = Math.round(gapDp * follower.getResources().getDisplayMetrics().density);
        int top = Math.max(0, lowest - at[1] - parent.getPaddingTop() + gap);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) follower.getLayoutParams();
        if (params.topMargin != top) {
            params.topMargin = top;
            follower.setLayoutParams(params);
        }
    }

    private static void setPadding(View view, int left, int top, int right, int bottom) {
        if (view.getPaddingLeft() != left || view.getPaddingTop() != top || view.getPaddingRight() != right
                || view.getPaddingBottom() != bottom) {
            view.setPadding(left, top, right, bottom);
        }
    }
}
