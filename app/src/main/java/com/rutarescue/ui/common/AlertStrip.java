package com.rutarescue.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rutarescue.R;
import com.rutarescue.model.advisory.AlertLevel;
import com.rutarescue.model.advisory.CityAlert;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.util.Formats;

import java.util.function.BiConsumer;

/**
 * Fills view_alert_strip.xml (2026-10-22): "Alert level 2 • Manila" in the level's colour, the
 * LGU's note or who set it and when, and - for the LGU only - "Change", which opens the level
 * picker. Shared by the three dashboards so the city's level looks the same for everyone.
 */
public final class AlertStrip {

    private AlertStrip() {
    }

    /**
     * A level older than 7 days is shown as "No alert level set" again (2026-10-25, CityAlert.isCurrent),
     * on every account's screen.
     *
     * @param onChange null for residents and moderators; for the LGU, runs with the chosen level and note
     */
    public static void bind(View strip, MetroManilaCity city, CityAlert alert, BiConsumer<AlertLevel, String> onChange) {
        Context context = strip.getContext();
        MetroManilaCity place = city != null ? city : alert == null ? null : alert.getCity();
        CityAlert shown = alert != null && alert.isCurrent(System.currentTimeMillis()) ? alert
                : place == null ? null : CityAlert.none(place);
        TextView level = strip.findViewById(R.id.alert_level);
        TextView note = strip.findViewById(R.id.alert_note);
        if (shown == null) {
            strip.setVisibility(View.GONE);
            return;
        }
        strip.setVisibility(View.VISIBLE);
        int color = ContextCompat.getColor(context, colorOf(shown.getLevel()));
        level.setText(shown.headline());
        level.setTextColor(color);
        ((ImageView) strip.findViewById(R.id.alert_icon)).setImageTintList(ColorStateList.valueOf(color));
        if (!shown.isSet()) {
            note.setText("No alert level set by the " + shown.getCity().getDisplayName() + " LGU yet");
        } else {
            String when = shown.getSetByName() + " • " + Formats.relative(shown.getSetAt(), System.currentTimeMillis());
            note.setText(shown.getNote().isEmpty() ? "Set by " + when : shown.getNote() + "\n" + when);
        }
        View change = strip.findViewById(R.id.alert_change);
        change.setVisibility(onChange == null ? View.GONE : View.VISIBLE);
        if (onChange != null) change.setOnClickListener(v -> pick(context, shown, onChange));
    }

    /** Same colours as the advisory cards: red for level 3, orange for 1 and 2, navy for "Advisory". */
    public static int colorOf(AlertLevel level) {
        return level == AlertLevel.ALERT_3 ? R.color.rr_red
                : level == AlertLevel.ADVISORY ? R.color.rr_navy_light : R.color.rr_orange;
    }

    /** LGU: choose the level, then an optional note for residents. */
    private static void pick(Context context, CityAlert current, BiConsumer<AlertLevel, String> onChange) {
        AlertLevel[] levels = AlertLevel.values();
        String[] labels = new String[levels.length];
        int checked = 0;
        for (int i = 0; i < levels.length; i++) {
            labels[i] = levels[i].getLabel();
            if (levels[i] == current.getLevel()) checked = i;
        }
        final int[] chosen = {checked};
        new MaterialAlertDialogBuilder(context)
                .setTitle("Alert level for " + current.getCity().getDisplayName())
                .setSingleChoiceItems(labels, checked, (dialog, which) -> chosen[0] = which)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.next, (dialog, which) -> Dialogs.input(context,
                        levels[chosen[0]].getLabel(), "Short note for residents (optional, up to 200 characters)",
                        "", note -> onChange.accept(levels[chosen[0]], note)))
                .show();
    }
}
