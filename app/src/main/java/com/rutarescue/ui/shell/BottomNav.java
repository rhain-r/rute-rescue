package com.rutarescue.ui.shell;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.rutarescue.R;

/**
 * The Figma bottom navigation ("RR / Resident Bottom Nav" and "RR / LGU Bottom Nav"):
 * four items; the active one sits on a navy 20 dp tile with a white icon and label, the
 * others use a #98A2B3 icon and a #667085 label. Clicks are forwarded to a listener (event handling).
 */
final class BottomNav {

    interface Listener {
        void onTabSelected(int index);
    }

    private static final int[] ITEM_IDS = {R.id.nav_item_0, R.id.nav_item_1, R.id.nav_item_2, R.id.nav_item_3};
    private static final int[] ICON_IDS = {R.id.nav_icon_0, R.id.nav_icon_1, R.id.nav_icon_2, R.id.nav_icon_3};
    private static final int[] LABEL_IDS = {R.id.nav_label_0, R.id.nav_label_1, R.id.nav_label_2, R.id.nav_label_3};

    private final View[] items = new View[4];
    private final ImageView[] icons = new ImageView[4];
    private final TextView[] labels = new TextView[4];

    BottomNav(View root, int[] iconRes, int[] labelRes, Listener listener) {
        for (int i = 0; i < 4; i++) {
            final int index = i;
            items[i] = root.findViewById(ITEM_IDS[i]);
            icons[i] = root.findViewById(ICON_IDS[i]);
            labels[i] = root.findViewById(LABEL_IDS[i]);
            icons[i].setImageResource(iconRes[i]);
            labels[i].setText(labelRes[i]);
            items[i].setContentDescription(root.getContext().getString(labelRes[i]));
            items[i].setOnClickListener(v -> listener.onTabSelected(index));
        }
    }

    void select(int index) {
        for (int i = 0; i < 4; i++) {
            boolean active = i == index;
            items[i].setBackgroundResource(active ? R.drawable.bg_nav_active : 0);
            int iconColor = ContextCompat.getColor(items[i].getContext(), active ? R.color.white : R.color.rr_text_subtle);
            int labelColor = ContextCompat.getColor(items[i].getContext(), active ? R.color.white : R.color.rr_text_secondary);
            icons[i].setImageTintList(ColorStateList.valueOf(iconColor));
            labels[i].setTextColor(labelColor);
            items[i].setSelected(active);
        }
    }
}
