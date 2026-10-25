package com.rutarescue.ui.common;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.ScrollView;

import androidx.core.widget.NestedScrollView;

import com.google.android.material.textfield.TextInputLayout;

/**
 * Keeps the text box being typed in visible above the keyboard (2026-10-21). On small phones or with
 * large text the keyboard covered the lower boxes (e.g. E-mail on the sign-in screen), because a
 * scroll view does not scroll by itself when only its bottom padding grows for the keyboard.
 */
final class KeyboardScroll {

    /** Space left between the box (with its helper text) and the keyboard. */
    private static final int GAP_DP = 24;

    private KeyboardScroll() {
    }

    /** Time for the keyboard to finish opening or switching layout (e.g. to the password keyboard). */
    private static final long SETTLE_MS = 300;

    /**
     * Reveals the focused box now and once more when the keyboard has settled: moving from a text box
     * to a password box swaps the keyboard for a taller one, which changes the space left above it.
     */
    static void revealSoon(View root) {
        root.post(() -> revealFocused(root));
        root.postDelayed(() -> revealFocused(root), SETTLE_MS);
    }

    /** Scrolls the nearest scroll view so the focused text box is fully inside its visible area. */
    static void revealFocused(View root) {
        View focused = root.findFocus();
        if (focused == null) return;
        // Show the whole field: its label, box and helper / error line (also when its eye icon has focus).
        View target = focused instanceof EditText ? focused : null;
        for (ViewParent p = focused.getParent(); p instanceof View; p = p.getParent()) {
            if (p instanceof TextInputLayout) {
                target = (View) p;
                break;
            }
        }
        if (target == null) return;
        View scroller = null;
        for (ViewParent p = target.getParent(); p instanceof View; p = p.getParent()) {
            if (p instanceof ScrollView || p instanceof NestedScrollView) {
                scroller = (View) p;
                break;
            }
        }
        if (scroller == null || !(scroller.getParent() instanceof View)) return;

        Rect box = new Rect();
        target.getDrawingRect(box);
        ((ViewGroup) scroller).offsetDescendantRectToMyCoords(target, box);
        int gap = Math.round(GAP_DP * root.getResources().getDisplayMetrics().density);
        int visibleTop = scroller.getScrollY() + scroller.getPaddingTop();
        int visibleBottom = scroller.getScrollY() + scroller.getHeight() - scroller.getPaddingBottom();
        int delta = 0;
        if (box.bottom + gap > visibleBottom) {
            delta = box.bottom + gap - visibleBottom;
        } else if (box.top - gap < visibleTop) {
            delta = box.top - gap - visibleTop;
        }
        if (delta == 0) return;
        if (scroller instanceof ScrollView) ((ScrollView) scroller).smoothScrollBy(0, delta);
        else ((NestedScrollView) scroller).smoothScrollBy(0, delta);
    }
}
