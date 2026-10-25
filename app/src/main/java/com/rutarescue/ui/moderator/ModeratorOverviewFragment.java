package com.rutarescue.ui.moderator;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.rutarescue.R;
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.center.CapacityChange;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.user.ModeratorProfile;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.CenterSnapshot;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.ui.common.AlertStrip;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseFragment;
import com.rutarescue.ui.common.CapacityGaugeView;
import com.rutarescue.ui.lgu.data.CenterCapacityActivity;
import com.rutarescue.ui.lgu.overview.AdvisoryEditorActivity;
import com.rutarescue.ui.lgu.overview.AdvisoryListActivity;
import com.rutarescue.ui.resident.home.InboxActivity;
import com.rutarescue.ui.shell.MainShellActivity;
import com.rutarescue.util.Formats;

import java.util.ArrayList;
import java.util.List;

/**
 * The moderator's Overview (2026-10-22). Controller for fragment_moderator_overview.xml:
 *  1. Advisories at the very top: the city's alert level (read-only), the 3 newest advisories the
 *     moderator should know about (published ones of the city, and this center's pending or
 *     declined ones), and "Write a center update", which goes to the LGU for approval (no "See all
 *     advisories" since 2026-10-25: the header bell opens Notifications);
 *  2. My center (Manage Center): the one center this moderator runs, with its headcount;
 *  3. Quick operations: check a resident's priority ID, review Ligtas applications.
 */
public class ModeratorOverviewFragment extends BaseFragment {

    private static final int OVERVIEW_ADVISORIES = 3;

    private ModeratorProfile moderator;
    private ListenerHandle centerListener = ListenerHandle.NONE;
    /** Null until the first advisories arrive (so the empty message never flashes while loading). */
    private List<Advisory> advisories;

    private final ActivityResultLauncher<ScanOptions> scanner = registerForActivityResult(new ScanContract(), result -> {
        if (result.getContents() != null) open(result.getContents());
    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_moderator_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        moderator = (ModeratorProfile) container().session().requireStaff();
        ((TextView) view.findViewById(R.id.header_eyebrow)).setText(R.string.role_moderator_eyebrow);
        view.findViewById(R.id.header_bell).setOnClickListener(v -> startActivity(new Intent(requireContext(), InboxActivity.class)));

        MainShellActivity shell = (MainShellActivity) requireActivity();
        view.findViewById(R.id.mod_write_advisory).setOnClickListener(v -> writeAdvisory());
        view.findViewById(R.id.mod_op_priority).setOnClickListener(v -> QrCheck.choose(requireContext(),
                () -> scanner.launch(QrCheck.scanOptions()), this::open));
        view.findViewById(R.id.mod_op_reviews).setOnClickListener(v -> shell.selectTab(2));

        track(container().alerts().listen(moderator.getJurisdiction(), ui(alert ->
                AlertStrip.bind(requireView().findViewById(R.id.alert_strip), moderator.getJurisdiction(), alert, null))));
        track(container().advisories().listenForCity(moderator.getJurisdiction(), ui(this::onAdvisories)));
        track(container().ligtas().listenForReviewer(moderator, ui(this::onApplications)));
        bindCenterHeader();
        followCenter();
    }

    /** The LGU may have assigned (another) center since sign-in: reload the profile each time the tab returns. */
    @Override
    public void onResume() {
        super.onResume();
        showAdvisories();
        container().session().restore(new Callback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile fresh) {
                if (!(fresh instanceof ModeratorProfile) || getView() == null) return;
                boolean changed = !((ModeratorProfile) fresh).getCenterId().equals(moderator.getCenterId());
                moderator = (ModeratorProfile) fresh;
                if (changed) requireActivity().runOnUiThread(() -> {
                    bindCenterHeader();
                    followCenter();
                    showAdvisories();
                });
            }

            @Override
            public void onError(Exception error) {
                // Offline: keep the profile from sign-in.
            }
        });
    }

    @Override
    public void onDestroyView() {
        centerListener.remove();
        super.onDestroyView();
    }

    // ------------------------------------------------------------------ advisories

    private void onAdvisories(SourcedData<List<Advisory>> data) {
        advisories = new ArrayList<>();
        for (Advisory advisory : data.getData()) {
            // What residents see, plus this center's own advisories that are not published (yet).
            if (advisory.isPublished() || isMine(advisory)) advisories.add(advisory);
        }
        showAdvisories();
    }

    private boolean isMine(Advisory advisory) {
        return moderator.hasAssignedCenter() && moderator.getCenterId().equals(advisory.getCenterId());
    }

    private void showAdvisories() {
        View view = getView();
        if (view == null || advisories == null) return;
        LinearLayout list = view.findViewById(R.id.mod_advisories);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < Math.min(OVERVIEW_ADVISORIES, advisories.size()); i++) {
            View card = inflater.inflate(R.layout.item_advisory, list, false);
            AdvisoryListActivity.bindForStaff(card, advisories.get(i), this::showAdvisories);
            list.addView(card);
        }
        if (advisories.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.no_recent_advisories);
            empty.setTextAppearance(R.style.TextAppearance_RR_Caption);
            list.addView(empty);
        }
    }

    private void writeAdvisory() {
        if (!moderator.canSubmitAdvisories()) {
            showMessage("Ask the " + moderator.getJurisdiction().getDisplayName()
                    + " LGU to assign you to an evacuation center first.");
            return;
        }
        startActivity(new Intent(requireContext(), AdvisoryEditorActivity.class));
    }

    // ------------------------------------------------------------------ my center

    private void bindCenterHeader() {
        View view = getView();
        if (view == null) return;
        ((TextView) view.findViewById(R.id.mod_write_advisory_sub)).setText(moderator.hasAssignedCenter()
                ? getString(R.string.write_center_update_sub_for, moderator.getCenterName())
                : getString(R.string.write_center_update_sub));
    }

    private void followCenter() {
        centerListener.remove();
        View view = requireView();
        View manage = view.findViewById(R.id.mod_center_manage);
        if (!moderator.hasAssignedCenter()) {
            ((TextView) view.findViewById(R.id.mod_center_name)).setText(R.string.no_center_assigned);
            ((TextView) view.findViewById(R.id.mod_center_place)).setText(getString(R.string.no_center_assigned_sub,
                    moderator.getJurisdiction().getDisplayName()));
            view.findViewById(R.id.mod_center_status).setVisibility(View.GONE);
            view.findViewById(R.id.mod_center_gauge).setVisibility(View.GONE);
            view.findViewById(R.id.mod_center_count).setVisibility(View.GONE);
            view.findViewById(R.id.mod_center_updated).setVisibility(View.GONE);
            manage.setVisibility(View.GONE);
            return;
        }
        ((TextView) view.findViewById(R.id.mod_center_name)).setText(moderator.getCenterName());
        manage.setVisibility(View.VISIBLE);
        manage.setOnClickListener(v -> startActivity(new Intent(requireContext(), CenterCapacityActivity.class)
                .putExtra(CenterCapacityActivity.EXTRA_ID, moderator.getCenterId())));
        centerListener = container().centers().listen(moderator.getCenterId(), ui(this::bindCenter));
    }

    private void bindCenter(CenterSnapshot snapshot) {
        View view = getView();
        if (view == null) return;
        EvacuationCenter center = snapshot.getCenter();
        ((TextView) view.findViewById(R.id.mod_center_name)).setText(center.getName());
        String barangay = center.getBarangay().isEmpty() ? "" : center.getBarangay() + " · ";
        ((TextView) view.findViewById(R.id.mod_center_place)).setText(barangay + center.getCity().getDisplayName()
                + (center.isPublished() ? "" : " · not published yet"));
        TextView status = view.findViewById(R.id.mod_center_status);
        status.setVisibility(View.VISIBLE);
        Badges.center(status, center.getStatus());
        CapacityGaugeView gauge = view.findViewById(R.id.mod_center_gauge);
        TextView count = view.findViewById(R.id.mod_center_count);
        gauge.setVisibility(View.VISIBLE);
        count.setVisibility(View.VISIBLE);
        if (center.getCapacity().isSet()) {
            gauge.setLevel(center.getCapacity().getFraction(), center.getCapacity().getLevel());
            count.setText(center.getCapacity().summary());
        } else {
            gauge.setVisibility(View.GONE);
            count.setText(R.string.capacity_not_set);
        }
        TextView updated = view.findViewById(R.id.mod_center_updated);
        updated.setVisibility(View.VISIBLE);
        CapacityChange change = center.getLastCapacityChange();
        updated.setText(snapshot.isPendingSync() ? getString(R.string.waiting_to_upload)
                : change == null ? getString(center.getCapacity().isSet() ? R.string.no_capacity_change_yet : R.string.no_capacity_change)
                : change.describe() + " • " + Formats.relative(change.getChangedAt(), System.currentTimeMillis()));
    }

    // ------------------------------------------------------------------ quick operations

    private void onApplications(SourcedData<List<LigtasApplication>> data) {
        int pending = 0;
        for (LigtasApplication application : data.getData()) {
            if (application.getStatus() == ApplicationStatus.PENDING) pending++;
        }
        ((TextView) requireView().findViewById(R.id.mod_op_reviews_sub)).setText(pending + " resident request(s) waiting");
    }

    private void open(String contents) {
        Intent destination = QrCheck.destination(requireContext(), contents);
        if (destination == null) showMessage("This is not a RutaRescue priority ID or Ligtas QR code.");
        else startActivity(destination);
    }
}
