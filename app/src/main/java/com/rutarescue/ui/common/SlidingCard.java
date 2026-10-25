package com.rutarescue.ui.common;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

/**
 * A floating map card that slides down until only its centred "–" handle is left, and slides back
 * up (2026-10-20, staff map). It always moves with a short animation, never in one jump:
 *  - drag the handle down or up and the card follows the finger, then settles;
 *  - tap the handle to slide it the other way;
 *  - the screen calls collapse() when the map is zoomed or panned, and expand() when a point is picked.
 *
 * Encapsulation: the screen only says what should happen; this class owns the touch handling,
 * the animation and the "how far down" measurement.
 */
public final class SlidingCard {

    private static final long SLIDE_MS = 220;
    /** Space left under the "–" when the card is down (just enough that it does not touch the edge). */
    private static final float GAP_UNDER_HANDLE_DP = 8;

    private final ViewGroup card;
    private final View handle;
    /** What the card rests on when it is down (the bottom tabs), or null for the bottom of the screen. */
    private final View floor;
    private boolean collapsed;
    private boolean animating;

    /**
     * @param card   the card itself (its first child holds the content, starting with the handle)
     * @param handle the touch strip with the "–", at the top of the card
     * @param floor  the view the "–" should rest on when the card is down (the bottom tabs), or null
     */
    public SlidingCard(ViewGroup card, View handle, View floor) {
        this.card = card;
        this.handle = handle;
        this.floor = floor;
        setUpDrag();
        handle.setOnClickListener(v -> slide(!collapsed));
        // The content can grow or shrink while the card is down; keep only the handle showing.
        card.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (collapsed && !animating && card.getTranslationY() != collapsedOffset()) {
                card.setTranslationY(collapsedOffset());
            }
        });
    }

    /** Slides the card down to its handle (the map was zoomed or moved). */
    public void collapse() {
        if (!collapsed) slide(true);
    }

    /** Slides the card back up (a point was picked). */
    public void expand() {
        expand(null);
    }

    /**
     * Slides the card back up, then runs whenUp once the card is fully up and laid out with its new
     * content, so the screen can measure exactly how much of the map the card still covers.
     */
    public void expand(Runnable whenUp) {
        Runnable afterLayout = whenUp == null ? null : () -> card.post(whenUp);
        if (collapsed) {
            slide(false, afterLayout);
        } else if (afterLayout != null) {
            afterLayout.run();
        }
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    /**
     * How far the card moves down: until only the "–" (and a small gap under it) is above the floor,
     * so no empty strip of card is left between the "–" and the bottom tabs.
     */
    private float collapsedOffset() {
        int[] at = new int[2];
        card.getLocationInWindow(at);
        float restingTop = at[1] - card.getTranslationY();  // where the card's top is when it is up
        float floorTop;
        if (floor != null && floor.getVisibility() == View.VISIBLE && floor.getHeight() > 0) {
            floor.getLocationInWindow(at);
            floorTop = at[1];
        } else {
            View parent = (View) card.getParent();
            parent.getLocationInWindow(at);
            floorTop = at[1] + parent.getHeight();
        }
        return Math.max(0, floorTop - (restingTop + peek(card, handle)));
    }

    /**
     * Height of the part of a card that stays visible when it is slid down: from the card's top to just
     * under the "–" bar. Shared with the resident Safe Route card so every map card looks the same.
     */
    public static int peek(ViewGroup card, View handleStrip) {
        int bottom = handleStrip.getBottom();
        if (handleStrip instanceof ViewGroup && ((ViewGroup) handleStrip).getChildCount() > 0) {
            View bar = ((ViewGroup) handleStrip).getChildAt(0);
            int gap = Math.round(GAP_UNDER_HANDLE_DP * card.getResources().getDisplayMetrics().density);
            bottom = Math.min(bottom, handleStrip.getTop() + bar.getBottom() + gap);
        }
        return card.getChildAt(0).getTop() + bottom;
    }

    private void slide(boolean down) {
        slide(down, null);
    }

    /** @param afterSlide runs when the slide has finished (not when another slide interrupts it) */
    private void slide(boolean down, Runnable afterSlide) {
        collapsed = down;
        if (down) card.scrollTo(0, 0);
        animating = true;
        card.animate().translationY(down ? collapsedOffset() : 0f).setDuration(SLIDE_MS)
                .withEndAction(() -> {
                    animating = false;
                    if (afterSlide != null) afterSlide.run();
                }).start();
        fadeContent(down);
    }

    /**
     * While the card is down only the "–" handle should show: everything else in the card fades out
     * (otherwise the title peeks out above the navigation bar and shows through its glass).
     */
    private void fadeContent(boolean down) {
        ViewGroup content = (ViewGroup) card.getChildAt(0);
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (child != handle) child.animate().alpha(down ? 0f : 1f).setDuration(SLIDE_MS).start();
        }
    }

    @SuppressLint("ClickableViewAccessibility") // a tap still reaches the handle's click listener
    private void setUpDrag() {
        int touchSlop = ViewConfiguration.get(card.getContext()).getScaledTouchSlop();
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downY;
            private float startTranslation;
            private boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = event.getRawY();
                        startTranslation = card.getTranslationY();
                        dragging = false;
                        card.animate().cancel(); // a cancelled animation skips its end action
                        animating = false;
                        v.getParent().requestDisallowInterceptTouchEvent(true); // move the card, not its content
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dy = event.getRawY() - downY;
                        if (!dragging && Math.abs(dy) < touchSlop) return true;
                        dragging = true;
                        card.setTranslationY(Math.max(0f, Math.min(collapsedOffset(), startTranslation + dy)));
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (dragging) slide(event.getRawY() > downY); // settle in the direction of the drag
                        else v.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        slide(collapsed);
                        return true;
                    default:
                        return false;
                }
            }
        });
    }
}
