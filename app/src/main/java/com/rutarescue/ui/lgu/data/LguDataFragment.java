package com.rutarescue.ui.lgu.data;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.rutarescue.R;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.hazard.DatasetType;
import com.rutarescue.model.hazard.DatasetVersion;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.ui.about.AboutActivity;
import com.rutarescue.ui.common.BaseFragment;
import com.rutarescue.ui.resident.home.InboxActivity;
import com.rutarescue.util.Formats;

import java.util.ArrayList;
import java.util.List;

/**
 * Data & Centers (Figma "LGU / Data & Centers", DOCX section 5): prepared and LGU dataset
 * versions, and the jurisdiction's evacuation centers (drafts included).
 */
public class LguDataFragment extends BaseFragment {

    private LguProfile lgu;
    private List<DatasetVersion> versions = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_lgu_data, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        lgu = container().session().requireLgu();
        ((TextView) view.findViewById(R.id.header_eyebrow)).setText(R.string.eyebrow_lgu);
        view.findViewById(R.id.header_bell).setOnClickListener(v -> startActivity(new Intent(requireContext(), InboxActivity.class)));
        view.findViewById(R.id.header_logo).setOnClickListener(v -> startActivity(new Intent(requireContext(), AboutActivity.class)));
        view.findViewById(R.id.data_upload).setOnClickListener(v -> startActivity(new Intent(requireContext(), DatasetImportActivity.class)));
        view.findViewById(R.id.data_manage).setOnClickListener(v -> showManageOptions());

        renderDatasets();
        track(container().datasets().listenVersions(ui(list -> {
            versions = list;
            renderDatasets();
        })));
        track(container().centers().listenForManager(lgu, ui(this::onCenters)));
    }

    private void renderDatasets() {
        View view = getView();
        if (view == null) return;
        LinearLayout list = view.findViewById(R.id.data_datasets);
        list.removeAllViews();
        List<DatasetVersion> all = new ArrayList<>(container().datasets().preparedDatasets());
        for (DatasetVersion version : versions) {
            if (version.getCity() == null || version.getCity() == lgu.getJurisdiction()) all.add(version);
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
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
            tile.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), tint)));
            ((TextView) row.findViewById(R.id.data_row_title)).setText(version.getVersionLabel());
            // Figma meta line: "Published • Updated 9:42 AM" (full details open on tap)
            ((TextView) row.findViewById(R.id.data_row_meta)).setText(version.getStatus().getLabel()
                    + (version.getPublishedAt() > 0
                    ? " • Updated " + Formats.relative(version.getPublishedAt(), System.currentTimeMillis()) : ""));
            row.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(version.getVersionLabel())
                    .setMessage(version.getType().getLabel() + "\nStatus: " + version.getStatus().getLabel()
                            + (version.getSource().isEmpty() ? "" : "\nSource: " + version.getSource())
                            + (version.getNotes().isEmpty() ? "" : "\n" + version.getNotes()))
                    .setPositiveButton(android.R.string.ok, null)
                    .show());
            list.addView(row);
        }
    }

    /** "Manage": add a center by hand, or import / verify the center list. */
    private void showManageOptions() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.manage_centers)
                .setItems(new CharSequence[]{getString(R.string.manage_add_center), getString(R.string.manage_import_centers)},
                        (dialog, which) -> startActivity(new Intent(requireContext(),
                                which == 0 ? CenterEditorActivity.class : DatasetImportActivity.class)))
                .show();
    }

    private void onCenters(SourcedData<List<EvacuationCenter>> data) {
        View view = requireView();
        ((TextView) view.findViewById(R.id.data_last_sync)).setText("Last sync: "
                + Formats.relative(data.getRetrievedAt(), System.currentTimeMillis()) + " (" + data.getFreshness().getLabel() + ")");
        LinearLayout list = view.findViewById(R.id.data_centers);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int needReview = 0;
        for (EvacuationCenter center : data.getData()) {
            boolean review = !center.getVerification().isVerified();
            if (review || !center.isPublished()) needReview++;
            // Figma center row: status and update time in the meta line, icon colour = state
            View row = inflater.inflate(R.layout.item_data_row, list, false);
            ImageView tile = row.findViewById(R.id.data_row_icon);
            tile.setImageResource(R.drawable.ic_center);
            int tint = review ? R.color.rr_orange : center.isPublished() ? R.color.rr_teal_green : R.color.rr_navy_light;
            tile.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), tint)));
            ((TextView) row.findViewById(R.id.data_row_title)).setText(center.getName());
            long updated = center.getAuditInfo() == null ? 0 : center.getAuditInfo().getUpdatedAt();
            String state = review ? "Needs review" : center.isPublished() ? "Published" : "Hidden";
            String headcount = center.getCapacity().isSet() ? center.getCapacity().getCurrentOccupancy() + "/"
                    + center.getCapacity().getTotalPersons() + " people • " : "";
            ((TextView) row.findViewById(R.id.data_row_meta)).setText(center.getStatus().getLabel() + " • " + headcount
                    + state + " • Updated " + Formats.relative(updated, System.currentTimeMillis()));
            // Phase 1: a center row opens "Center capacity"; details are under its More menu.
            row.setOnClickListener(v -> startActivity(new Intent(requireContext(), CenterCapacityActivity.class)
                    .putExtra(CenterCapacityActivity.EXTRA_ID, center.getId())));
            list.addView(row);
        }
        if (data.getData().isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No centers yet. Use Manage to add a center or import the legacy list.");
            empty.setTextAppearance(R.style.TextAppearance_RR_Caption);
            list.addView(empty);
        }
        ((TextView) view.findViewById(R.id.data_need_review)).setText(needReview == 0 ? "" : needReview + " need review");
    }
}
