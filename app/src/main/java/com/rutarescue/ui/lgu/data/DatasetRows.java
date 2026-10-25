package com.rutarescue.ui.lgu.data;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rutarescue.R;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.hazard.DatasetType;
import com.rutarescue.model.hazard.DatasetVersion;
import com.rutarescue.util.Formats;

import java.util.ArrayList;
import java.util.List;

/**
 * The dataset rows (Figma "LGU / Data & Centers"): the prepared datasets bundled in the app
 * (NOAH flood layers, bridges and tunnels) and the city's own dataset versions (center imports).
 * Since 2026-10-25 they are shown in the LGU's Settings instead of the Data tab.
 */
public final class DatasetRows {

    private DatasetRows() {
    }

    /** Replaces the list's rows; a tap shows the dataset's details. */
    public static void render(Context context, LinearLayout list, List<DatasetVersion> prepared,
                              List<DatasetVersion> versions, MetroManilaCity city) {
        list.removeAllViews();
        List<DatasetVersion> all = new ArrayList<>(prepared);
        for (DatasetVersion version : versions) {
            if (version.getCity() == null || version.getCity() == city) all.add(version);
        }
        LayoutInflater inflater = LayoutInflater.from(context);
        for (DatasetVersion version : all) {
            // Figma dataset row: coloured icon tile, version title, "Published • Updated 9:42 AM"
            View row = inflater.inflate(R.layout.item_data_row, list, false);
            int icon;
            int tint;
            if (version.getType() == DatasetType.FLOOD_HAZARD) {
                icon = R.drawable.ic_layers_two;
                tint = R.color.rr_red;
            } else if (version.getType() == DatasetType.ROAD_STRUCTURES) {
                icon = R.drawable.ic_road_network;
                tint = R.color.rr_cyan_icon;
            } else {
                icon = R.drawable.ic_center;
                tint = R.color.rr_teal_green;
            }
            ImageView tile = row.findViewById(R.id.data_row_icon);
            tile.setImageResource(icon);
            tile.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, tint)));
            ((TextView) row.findViewById(R.id.data_row_title)).setText(version.getVersionLabel());
            ((TextView) row.findViewById(R.id.data_row_meta)).setText(version.getStatus().getLabel()
                    + (version.getPublishedAt() > 0
                    ? " • Updated " + Formats.relative(version.getPublishedAt(), System.currentTimeMillis()) : ""));
            row.setOnClickListener(v -> new MaterialAlertDialogBuilder(context)
                    .setTitle(version.getVersionLabel())
                    .setMessage(version.getType().getLabel() + "\nStatus: " + version.getStatus().getLabel()
                            + (version.getSource().isEmpty() ? "" : "\nSource: " + version.getSource())
                            + (version.getNotes().isEmpty() ? "" : "\n" + version.getNotes()))
                    .setPositiveButton(android.R.string.ok, null)
                    .show());
            list.addView(row);
        }
    }
}
