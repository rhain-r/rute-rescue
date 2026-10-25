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
import com.rutarescue.model.center.CenterState;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseFragment;
import com.rutarescue.ui.resident.home.InboxActivity;
import com.rutarescue.util.Formats;

import java.util.ArrayList;
import java.util.List;

/**
 * Data & Centers (Figma "LGU / Data & Centers", DOCX section 5): the jurisdiction's evacuation
 * centers (drafts included). Since 2026-10-22 only the LGU has this tab: it adds or imports
 * centers ("Manage") and publishes them; moderators update the headcount of the center they run.
 * Since 2026-10-25 the dataset versions are in Settings (DatasetRows), not here.
 */
public class LguDataFragment extends BaseFragment {

    private StaffProfile staff;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_lgu_data, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        staff = container().session().requireStaff();
        ((TextView) view.findViewById(R.id.header_eyebrow)).setText(R.string.eyebrow_lgu);
        view.findViewById(R.id.header_bell).setOnClickListener(v -> startActivity(new Intent(requireContext(), InboxActivity.class)));
        view.findViewById(R.id.data_manage).setOnClickListener(v -> showManageOptions());
        if (!staff.canCreateCenters()) {
            // 2026-10-22: only the LGU adds and imports centers (moderators no longer have this tab).
            view.findViewById(R.id.data_manage).setVisibility(View.GONE);
        }

        track(container().centers().listenForManager(staff, ui(this::onCenters)));
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
        // 2026-10-20: centers that still need work come first, and each row shows its state as a chip.
        List<EvacuationCenter> centers = new ArrayList<>(data.getData());
        CenterState.sortForStaff(centers);
        for (EvacuationCenter center : centers) {
            CenterState state = CenterState.of(center);
            if (state != CenterState.PUBLISHED) needReview++;
            // Figma center row: status and update time in the meta line, icon colour = state
            View row = inflater.inflate(R.layout.item_data_row, list, false);
            ImageView tile = row.findViewById(R.id.data_row_icon);
            tile.setImageResource(R.drawable.ic_center);
            int tint = state == CenterState.NEEDS_VERIFICATION ? R.color.rr_orange
                    : state == CenterState.PUBLISHED ? R.color.rr_teal_green : R.color.rr_navy_light;
            tile.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), tint)));
            ((TextView) row.findViewById(R.id.data_row_title)).setText(center.getName());
            long updated = center.getAuditInfo() == null ? 0 : center.getAuditInfo().getUpdatedAt();
            String headcount = center.getCapacity().isSet() ? center.getCapacity().getCurrentOccupancy() + "/"
                    + center.getCapacity().getTotalPersons() + " people • " : "";
            ((TextView) row.findViewById(R.id.data_row_meta)).setText(center.getStatus().getLabel() + " • " + headcount
                    + "Updated " + Formats.relative(updated, System.currentTimeMillis()));
            TextView chip = row.findViewById(R.id.data_row_chip);
            chip.setVisibility(View.VISIBLE);
            Badges.centerState(chip, state);
            // Phase 1: a center row opens "Center capacity"; details are under its More menu.
            row.setOnClickListener(v -> startActivity(new Intent(requireContext(), CenterCapacityActivity.class)
                    .putExtra(CenterCapacityActivity.EXTRA_ID, center.getId())));
            list.addView(row);
        }
        if (data.getData().isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.lgu_no_centers);
            empty.setTextAppearance(R.style.TextAppearance_RR_Caption);
            list.addView(empty);
        }
        ((TextView) view.findViewById(R.id.data_need_review)).setText(needReview == 0 ? "" : needReview + " need review");
    }
}
