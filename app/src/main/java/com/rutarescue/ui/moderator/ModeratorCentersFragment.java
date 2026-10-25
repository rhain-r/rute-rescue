package com.rutarescue.ui.moderator;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.rutarescue.R;
import com.rutarescue.model.center.CenterCapacity;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseFragment;
import com.rutarescue.ui.common.CapacityGaugeView;
import com.rutarescue.ui.lgu.data.CenterCapacityActivity;
import com.rutarescue.ui.resident.home.InboxActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * The moderator's "Centers" tab (2026-10-22), which replaced the map: the city's evacuation
 * centers with their current headcount, fullest first. A row opens the center's page (editable
 * only for the moderator's own center). The list follows the database live.
 */
public class ModeratorCentersFragment extends BaseFragment {

    private StaffProfile staff;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_moderator_centers, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        staff = container().session().requireStaff();
        ((TextView) view.findViewById(R.id.header_eyebrow)).setText(R.string.role_moderator_eyebrow);
        view.findViewById(R.id.header_bell).setOnClickListener(v -> startActivity(new Intent(requireContext(), InboxActivity.class)));
        ((TextView) view.findViewById(R.id.mod_centers_title)).setText(getString(R.string.mod_centers_title,
                staff.getJurisdiction().getDisplayName()));
        track(container().centers().listenForManager(staff, ui(this::onCenters)));
    }

    private void onCenters(SourcedData<List<EvacuationCenter>> data) {
        View view = requireView();
        Badges.freshness(view.findViewById(R.id.mod_centers_freshness), data.getFreshness(), data.getRetrievedAt(), "");
        List<EvacuationCenter> centers = new ArrayList<>();
        int people = 0;
        int room = 0;
        for (EvacuationCenter center : data.getData()) {
            // Published centers, plus the moderator's own even if the LGU has not published it yet.
            if (center.isArchived() || !(center.isPublished() || staff.canManageCenter(center))) continue;
            centers.add(center);
            CenterCapacity capacity = center.getCapacity();
            if (capacity.isSet()) {
                people += capacity.getCurrentOccupancy();
                room += Math.max(0, capacity.getTotalPersons() - capacity.getCurrentOccupancy());
            }
        }
        // Fullest first; centers without a headcount last, then A to Z.
        centers.sort((a, b) -> {
            double fa = a.getCapacity().isSet() ? a.getCapacity().getFraction() : -1;
            double fb = b.getCapacity().isSet() ? b.getCapacity().getFraction() : -1;
            if (fa != fb) return Double.compare(fb, fa);
            return a.getName().compareToIgnoreCase(b.getName());
        });
        ((TextView) view.findViewById(R.id.mod_centers_total)).setText(people + " in • " + room + " free");

        LinearLayout list = view.findViewById(R.id.mod_centers_list);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (EvacuationCenter center : centers) {
            View row = inflater.inflate(R.layout.item_center_load, list, false);
            ((TextView) row.findViewById(R.id.load_name)).setText(center.getName());
            Badges.center(row.findViewById(R.id.load_status), center.getStatus());
            row.findViewById(R.id.load_mine).setVisibility(staff.canManageCenter(center) ? View.VISIBLE : View.GONE);
            CapacityGaugeView gauge = row.findViewById(R.id.load_gauge);
            TextView count = row.findViewById(R.id.load_count);
            if (center.getCapacity().isSet()) {
                gauge.setLevel(center.getCapacity().getFraction(), center.getCapacity().getLevel());
                count.setText(center.getCapacity().summary());
            } else {
                gauge.setVisibility(View.GONE);
                count.setText(R.string.capacity_not_set);
            }
            row.setOnClickListener(v -> startActivity(new Intent(requireContext(), CenterCapacityActivity.class)
                    .putExtra(CenterCapacityActivity.EXTRA_ID, center.getId())));
            list.addView(row);
        }
        if (centers.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(getString(R.string.no_centers_in_city, staff.getJurisdiction().getDisplayName()));
            empty.setTextAppearance(R.style.TextAppearance_RR_Caption);
            list.addView(empty);
        }
    }
}
