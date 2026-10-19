package com.rutarescue.ui.common;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.appcompat.widget.TooltipCompat;

import com.rutarescue.R;

/**
 * A small "i" tooltip: tapping the anchor shows a navy bubble with a short title and
 * explanation; tapping the bubble or anywhere else closes it.
 *
 * Added after Prof. Neypes' review so residents do not mix up the two kinds of flood
 * information: the coloured risk zones are PREPARED Project NOAH scenarios, while LGU
 * advisories are CURRENT updates (rain, alert levels, evacuation calls).
 *
 * The same text is also set as the standard Android tooltip (TooltipCompat), so a long-press
 * and TalkBack read it too.
 */
public final class InfoTooltip {

    /** Widest the bubble gets on a large phone (dp). */
    private static final int MAX_WIDTH_DP = 300;
    /** Space kept between the bubble and the screen edges (dp). */
    private static final int SCREEN_MARGIN_DP = 16;
    /** Gap between the anchor and the bubble (dp). */
    private static final int ANCHOR_GAP_DP = 4;

    private InfoTooltip() {
        // static helpers only
    }

    /** Makes a tap on the anchor open the tooltip. */
    public static void attach(View anchor, @StringRes int title, @StringRes int message) {
        Context context = anchor.getContext();
        String titleText = context.getString(title);
        String messageText = context.getString(message);
        TooltipCompat.setTooltipText(anchor, titleText + ". " + messageText);
        anchor.setOnClickListener(v -> show(v, titleText, messageText));
    }

    /** Shows the bubble under the anchor (above it when there is no room below). */
    public static PopupWindow show(View anchor, CharSequence title, CharSequence message) {
        Context context = anchor.getContext();
        View bubble = LayoutInflater.from(context).inflate(R.layout.view_info_tooltip, null);
        ((TextView) bubble.findViewById(R.id.tooltip_title)).setText(title);
        ((TextView) bubble.findViewById(R.id.tooltip_message)).setText(message);

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int width = Math.min(dp(context, MAX_WIDTH_DP), metrics.widthPixels - 2 * dp(context, SCREEN_MARGIN_DP));
        PopupWindow popup = new PopupWindow(bubble, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        // A (transparent) background lets taps outside the bubble close it on every Android version.
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(context.getResources().getDimension(R.dimen.elevation_float));
        bubble.setOnClickListener(v -> popup.dismiss());

        // An anchor on the right half of the screen gets a bubble that ends where the anchor ends.
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        boolean anchorOnRight = location[0] + anchor.getWidth() / 2 > metrics.widthPixels / 2;
        popup.showAsDropDown(anchor, 0, dp(context, ANCHOR_GAP_DP), anchorOnRight ? Gravity.END : Gravity.START);
        return popup;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
