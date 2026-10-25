package com.rutarescue.ui.lgu.overview;

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
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.advisory.CityAlert;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.route.RoadClosure;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.model.user.StaffRequest;
import com.rutarescue.model.user.StaffRequestStatus;
import com.rutarescue.service.AdvisoryService;
import com.rutarescue.ui.common.AlertStrip;
import com.rutarescue.ui.common.BaseFragment;
import com.rutarescue.ui.resident.home.InboxActivity;
import com.rutarescue.ui.shell.MainShellActivity;

import java.util.List;

/**
 * The LGU Overview (Figma "LGU / Overview"). Every metric is computed from real records of the
 * officer's city (nothing is invented). Since 2026-10-22 it is for the LGU only (moderators have
 * ModeratorOverviewFragment) and starts with Advisories: the city's alert level, which only the
 * LGU sets ("Change"), then - since 2026-10-25, like the resident Home - only the newest published
 * announcement of the last 7 days (a level older than 7 days reads "No alert level set" again).
 * "Waiting for you" counts the moderators' advisories to accept or decline and the staff
 * sign-ups; "Ligtas Priority" (since 2026-10-25, in place of "Updates") the applications pending
 * review; quick operations "Finalize" and "Publish community advisory".
 */
public class LguOverviewFragment extends BaseFragment {

    private StaffProfile staff;
    /** The city's alert level as last read (new advisories carry it). */
    private CityAlert cityAlert;
    private int publishedCenters;
    /** Active road closures in the officer's city (for the short description). */
    private int cityClosures;
    /** Null until the first advisories arrive (so the empty message never flashes while loading). */
    private List<Advisory> overviewAdvisories;
    // "Waiting for you": what moderators and new staff sent to the LGU.
    private int waitingCenters;
    private int waitingAdvisories;
    private int waitingRequests;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_lgu_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        staff = container().session().requireStaff();
        ((TextView) view.findViewById(R.id.header_eyebrow)).setText(R.string.eyebrow_lgu);
        view.findViewById(R.id.header_bell).setOnClickListener(v -> startActivity(new Intent(requireContext(), InboxActivity.class)));
        ((TextView) view.findViewById(R.id.lgu_hero_city)).setText(staff.getJurisdiction().getDisplayName() + " • "
                + staff.getPosition() + " • " + staff.getRole().getLabel());

        MainShellActivity shell = (MainShellActivity) requireActivity();
        view.findViewById(R.id.lgu_op_reviews).setOnClickListener(v -> shell.selectTab(2));
        view.findViewById(R.id.lgu_op_advisory).setOnClickListener(v -> startActivity(new Intent(requireContext(), AdvisoryEditorActivity.class)));
        view.findViewById(R.id.lgu_view_report).setOnClickListener(v -> startActivity(new Intent(requireContext(), ReportActivity.class)));

        track(container().staff().listenRequests(staff, ui(requests -> {
            waitingRequests = 0;
            for (StaffRequest request : requests) {
                if (request.getStatus() == StaffRequestStatus.PENDING) waitingRequests++;
            }
            showWaitingForLgu();
        })));
        showWaitingForLgu();
        track(container().alerts().listen(staff.getJurisdiction(), ui(this::onCityAlert)));
        track(container().centers().listenForManager(staff, ui(this::onCenters)));
        track(container().datasets().listenClosures(ui(this::onClosures)));
        track(container().ligtas().listenForReviewer(staff, ui(data -> onLigtas(data.getData()))));
        track(container().advisories().listenForCity(staff.getJurisdiction(), ui(this::onAdvisories)));
        updateHeroBody();
    }

    /** The city's alert level: shown at the top, "Change" lets the LGU set a new one. */
    private void onCityAlert(CityAlert alert) {
        cityAlert = alert;
        AlertStrip.bind(requireView().findViewById(R.id.alert_strip), staff.getJurisdiction(), alert,
                (level, note) -> container().alerts().set(staff, cityAlert, level, note,
                        ui(ignored -> showMessage("Alert level set to " + level.getLabel()))));
        ((TextView) requireView().findViewById(R.id.lgu_hero_alert)).setText(alert == null
                ? "" : alert.currentLevel(System.currentTimeMillis()).getLabel());
    }

    /** The first metric and quick operation count what waits for the LGU's decision. */
    private void showWaitingForLgu() {
        if (getView() == null) return;
        int total = waitingCenters + waitingAdvisories + waitingRequests;
        setMetric(R.id.lgu_metric_reviews, R.id.lgu_metric_reviews_sub, total, waitingAdvisories + " advisories • "
                + waitingRequests + " staff" + (waitingCenters == 0 ? "" : " • " + waitingCenters + " centers"));
        ((TextView) requireView().findViewById(R.id.lgu_op_reviews_sub)).setText(total == 0
                ? "Nothing waiting for you" : total + " item(s) waiting for you");
        updateHeroBody();
    }

    private void onCenters(SourcedData<List<EvacuationCenter>> data) {
        int unverified = 0;
        publishedCenters = 0;
        waitingCenters = 0;
        for (EvacuationCenter center : data.getData()) {
            if (center.isPublished()) publishedCenters++;
            if (!center.getVerification().isVerified()) unverified++;
            if (center.isAwaitingLgu()) waitingCenters++;
        }
        showWaitingForLgu();
        setMetric(R.id.lgu_metric_centers, R.id.lgu_metric_centers_sub, publishedCenters,
                data.getData().isEmpty() ? "None added yet" : unverified == 0 ? "All verified" : unverified + " need verification");
        updateHeroBody();
    }

    /**
     * 2026-10-25, in place of "Updates": the city's Ligtas Priority applications still pending
     * review by a moderator (0 when there are none; "Needs documents" waits on the resident).
     */
    private void onLigtas(List<LigtasApplication> applications) {
        int pending = 0;
        for (LigtasApplication application : applications) {
            if (application.getStatus() == ApplicationStatus.PENDING) pending++;
        }
        ((TextView) requireView().findViewById(R.id.lgu_metric_ligtas)).setText(String.valueOf(pending));
    }

    private void onClosures(List<RoadClosure> closures) {
        int mine = 0;
        for (RoadClosure closure : closures) {
            if (closure.getCity() == staff.getJurisdiction()) mine++;
        }
        setMetric(R.id.lgu_metric_closures, R.id.lgu_metric_closures_sub, mine, closures.size() + " active in Metro Manila");
        cityClosures = mine;
        updateHeroBody();
    }

    private void onAdvisories(SourcedData<List<Advisory>> data) {
        waitingAdvisories = 0;
        for (Advisory advisory : data.getData()) {
            if (advisory.isAwaitingLgu()) waitingAdvisories++;
        }
        showWaitingForLgu();

        // Only the newest one of the week; Notifications (the header bell) have the last 30 days.
        overviewAdvisories = data.getData();
        showOverviewAdvisories();
    }

    /** Saving or un-saving elsewhere changes the "Saved" label here when this screen comes back. */
    @Override
    public void onResume() {
        super.onResume();
        showOverviewAdvisories();
    }

    /**
     * 2026-10-25: one card - the city's newest published announcement of the last 7 days (pending
     * ones wait in Finalize). Older ones are in Notifications (the header bell).
     */
    private void showOverviewAdvisories() {
        View view = getView();
        if (view == null || overviewAdvisories == null) return;
        LinearLayout list = view.findViewById(R.id.lgu_advisories);
        list.removeAllViews();
        Advisory newest = AdvisoryService.latestThisWeek(overviewAdvisories, staff.getJurisdiction(), true,
                System.currentTimeMillis());
        if (newest != null) {
            View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_advisory, list, false);
            AdvisoryListActivity.bindForStaff(card, newest, this::showOverviewAdvisories);
            list.addView(card);
        } else {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.no_announcement_this_week);
            empty.setTextAppearance(R.style.TextAppearance_RR_Caption);
            list.addView(empty);
        }
    }

    /**
     * 2026-10-22: a short description of the city's day in place of the big "Operations overview"
     * title, e.g. "2 centers open to residents, 1 item waiting for you and no road closures."
     */
    private void updateHeroBody() {
        View view = getView();
        if (view == null) return;
        int waiting = waitingCenters + waitingAdvisories + waitingRequests;
        ((TextView) view.findViewById(R.id.lgu_hero_body)).setText(count(publishedCenters, "center", "centers")
                + " open to residents, " + (waiting == 0 ? "nothing" : count(waiting, "item", "items")) + " waiting for you and "
                + (cityClosures == 0 ? "no road closures" : count(cityClosures, "road closure", "road closures")) + ".");
        String version = container().hazards.getManifest().versionOf(FloodScenario.DEFAULT);
        ((TextView) view.findViewById(R.id.lgu_hero_data)).setText("Flood maps: prepared NOAH data " + version + " (not live)");
    }

    private static String count(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    private void setMetric(int valueId, int subId, int value, String sub) {
        View view = requireView();
        ((TextView) view.findViewById(valueId)).setText(String.valueOf(value));
        ((TextView) view.findViewById(subId)).setText(sub);
    }
}
