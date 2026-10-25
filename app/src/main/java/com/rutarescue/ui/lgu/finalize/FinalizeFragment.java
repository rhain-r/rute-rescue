package com.rutarescue.ui.lgu.finalize;

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
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.advisory.AlertLevel;
import com.rutarescue.model.advisory.CityAlert;
import com.rutarescue.model.center.CenterState;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.user.ModeratorProfile;
import com.rutarescue.model.user.Role;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.model.user.StaffRequest;
import com.rutarescue.model.user.StaffRequestStatus;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseFragment;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.lgu.data.CenterCapacityActivity;
import com.rutarescue.ui.lgu.overview.AdvisoryEditorActivity;
import com.rutarescue.ui.resident.home.InboxActivity;
import com.rutarescue.util.Formats;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The LGU's "Finalize" tab (2026-10-20, reworked 2026-10-22): everything only the LGU can finish.
 *  - Advisories from moderators: Accept (published at once with the city's alert level) or
 *    Decline (with a comment the moderator reads; the advisory is then locked).
 *  - Staff sign-ups: approve (a moderator is given the center they will run) or not approve.
 *  - Moderators: which center each one runs; a tap opens ModeratorDashboardActivity (capacity,
 *    flood status, advisories and activity of that center, with "Change center").
 *  - Centers moderators sent before 2026-10-22, if any are still waiting.
 * Every list follows the database live, so an item disappears as soon as it is decided.
 */
public class FinalizeFragment extends BaseFragment {

    private StaffProfile staff;
    private CityAlert cityAlert;
    /** The city's centers (not archived), offered when a moderator is given a center. */
    private final List<EvacuationCenter> cityCenters = new ArrayList<>();
    private int waitingCenters;
    private int waitingAdvisories;
    private int waitingRequests;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_lgu_finalize, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        staff = container().session().requireStaff();
        ((TextView) view.findViewById(R.id.header_eyebrow)).setText(R.string.eyebrow_lgu);
        view.findViewById(R.id.header_bell).setOnClickListener(v -> startActivity(new Intent(requireContext(), InboxActivity.class)));
        ((TextView) view.findViewById(R.id.finalize_city)).setText(staff.getJurisdiction().getDisplayName() + " • "
                + staff.getPosition());
        updateTotal();

        track(container().alerts().listen(staff.getJurisdiction(), ui(alert -> cityAlert = alert)));
        track(container().centers().listenForManager(staff, ui(this::onCenters)));
        track(container().advisories().listenForCity(staff.getJurisdiction(), ui(this::onAdvisories)));
        track(container().staff().listenRequests(staff, ui(this::onRequests)));
        track(container().staff().listenStaff(staff, ui(this::onStaff)));
    }

    // ------------------------------------------------------------------ advisories to approve

    private void onAdvisories(SourcedData<List<Advisory>> data) {
        LinearLayout list = requireView().findViewById(R.id.finalize_advisories);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        waitingAdvisories = 0;
        long now = System.currentTimeMillis();
        for (Advisory advisory : data.getData()) {
            if (!advisory.isAwaitingLgu()) continue;
            waitingAdvisories++;
            View card = inflater.inflate(R.layout.item_advisory_review, list, false);
            ((TextView) card.findViewById(R.id.review_center)).setText(advisory.getCenterName().isEmpty()
                    ? advisory.getAuthorName() : advisory.getCenterName());
            ((TextView) card.findViewById(R.id.review_meta)).setText("Pending • by " + advisory.getRequestedBy()
                    + " • " + Formats.relative(advisory.getActivityTime(), now));
            ((TextView) card.findViewById(R.id.review_title)).setText(advisory.getTitle());
            ((TextView) card.findViewById(R.id.review_message)).setText(advisory.getMessage());
            card.findViewById(R.id.review_accept).setOnClickListener(v -> accept(advisory));
            card.findViewById(R.id.review_decline).setOnClickListener(v -> decline(advisory));
            card.setOnClickListener(v -> startActivity(new Intent(requireContext(), AdvisoryEditorActivity.class)
                    .putExtra(AdvisoryEditorActivity.EXTRA_ID, advisory.getId())));
            list.addView(card);
        }
        if (waitingAdvisories == 0) list.addView(empty(getString(R.string.no_advisories_waiting)));
        updateTotal();
    }

    private AlertLevel cityLevel() {
        return cityAlert == null ? AlertLevel.ADVISORY : cityAlert.currentLevel(System.currentTimeMillis());
    }

    private void accept(Advisory advisory) {
        Dialogs.confirm(requireContext(), getString(R.string.accept), "Publish \"" + advisory.getTitle()
                        + "\" to residents of " + staff.getJurisdiction().getDisplayName() + " now? It will show the city's "
                        + "alert level (" + cityLevel().getLabel() + ").", getString(R.string.accept_and_publish),
                () -> container().advisories().approve(staff, advisory, cityLevel(), ui(ignored ->
                        showMessage("Approved and published"))));
    }

    private void decline(Advisory advisory) {
        Dialogs.input(requireContext(), getString(R.string.decline), getString(R.string.decline_comment_hint), "",
                comment -> container().advisories().decline(staff, advisory, comment, ui(ignored ->
                        showMessage("Declined. The moderator sees your comment."))));
    }

    // ------------------------------------------------------------------ staff sign-ups

    private void onRequests(List<StaffRequest> requests) {
        LinearLayout list = requireView().findViewById(R.id.finalize_requests);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        waitingRequests = 0;
        long now = System.currentTimeMillis();
        for (StaffRequest request : requests) {
            if (request.getStatus() != StaffRequestStatus.PENDING) continue;
            waitingRequests++;
            View row = row(inflater, list, R.drawable.ic_reviews, request.getDisplayName(), request.getRole().getLabel()
                    + (request.getPosition().isEmpty() ? "" : " • " + request.getPosition()) + " • "
                    + request.getEmail() + " • " + Formats.relative(request.getRequestedAt(), now));
            row.setOnClickListener(v -> decide(request));
            list.addView(row);
        }
        if (waitingRequests == 0) list.addView(empty("No staff sign-ups waiting."));
        updateTotal();
    }

    /** Approve or refuse one sign-up; a refusal needs a reason the person will read at sign-in. */
    private void decide(StaffRequest request) {
        boolean moderator = request.getRole() == Role.MODERATOR;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(request.getDisplayName())
                .setMessage("Asks to be a " + request.getRole().getLabel() + " of " + staff.getJurisdiction().getDisplayName()
                        + (request.getPosition().isEmpty() ? "" : " (" + request.getPosition() + ")") + ".\n"
                        + request.getEmail() + "\n\nApprove only people you know work for the city."
                        + (moderator ? "\nNext you choose the evacuation center they will run." : ""))
                .setNegativeButton("Not approve", (dialog, which) -> Dialogs.input(requireContext(), "Not approve",
                        "Reason (the person reads it when signing in)", "", reason ->
                                container().staff().reject(staff, request, reason, ui(ignored ->
                                        showMessage("Request not approved")))))
                .setNeutralButton(R.string.cancel, null)
                .setPositiveButton(R.string.approve, (dialog, which) -> {
                    if (moderator) {
                        pickCenter("Which center will " + request.getDisplayName() + " run?", center -> approve(request, center));
                    } else {
                        approve(request, null);
                    }
                })
                .show();
    }

    private void approve(StaffRequest request, EvacuationCenter center) {
        container().staff().approve(staff, request, center, ui(ignored -> showMessage(request.getDisplayName()
                + " can now sign in as " + request.getRole().getLabel()
                + (center == null ? "" : " of " + center.getName()))));
    }

    // ------------------------------------------------------------------ moderators and their centers

    private void onStaff(List<StaffProfile> members) {
        LinearLayout list = requireView().findViewById(R.id.finalize_moderators);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int moderators = 0;
        for (StaffProfile member : members) {
            if (!(member instanceof ModeratorProfile)) continue;
            moderators++;
            ModeratorProfile moderator = (ModeratorProfile) member;
            View row = row(inflater, list, R.drawable.ic_center, moderator.getDisplayName(), moderator.hasAssignedCenter()
                    ? "Runs " + moderator.getCenterName() + " • tap for its capacity, flood status and activity"
                    : "No center yet • tap to assign one");
            // 2026-10-22: a dashboard of the moderator's center (with "Change center"), not just the picker.
            row.setOnClickListener(v -> startActivity(ModeratorDashboardActivity.intent(requireContext(), moderator.getUid())));
            list.addView(row);
        }
        if (moderators == 0) list.addView(empty("No moderators yet. Approve their sign-ups above."));
    }

    /** Lists the city's centers; the LGU adds new ones under Data. */
    private void pickCenter(String title, Consumer<EvacuationCenter> onPicked) {
        if (cityCenters.isEmpty()) {
            showMessage("Add the evacuation center first (Data > Manage > Add center).");
            return;
        }
        String[] names = new String[cityCenters.size()];
        for (int i = 0; i < names.length; i++) names[i] = cityCenters.get(i).getName();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setItems(names, (dialog, which) -> onPicked.accept(cityCenters.get(which)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ centers (before 2026-10-22)

    private void onCenters(SourcedData<List<EvacuationCenter>> data) {
        cityCenters.clear();
        for (EvacuationCenter center : data.getData()) {
            if (!center.isArchived()) cityCenters.add(center);
        }
        cityCenters.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        LinearLayout list = requireView().findViewById(R.id.finalize_centers);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        waitingCenters = 0;
        for (EvacuationCenter center : data.getData()) {
            if (CenterState.of(center) != CenterState.WAITING_FOR_LGU) continue;
            waitingCenters++;
            View row = row(inflater, list, R.drawable.ic_center, center.getName(), center.getAddress()
                    + " • verified against " + center.getVerification().getSource());
            TextView chip = row.findViewById(R.id.data_row_chip);
            chip.setVisibility(View.VISIBLE);
            Badges.centerState(chip, CenterState.WAITING_FOR_LGU);
            // The center's page shows its details and pin, with "Publish to residents" and "Send back".
            row.setOnClickListener(v -> startActivity(new Intent(requireContext(), CenterCapacityActivity.class)
                    .putExtra(CenterCapacityActivity.EXTRA_ID, center.getId())));
            list.addView(row);
        }
        requireView().findViewById(R.id.finalize_centers_header).setVisibility(waitingCenters == 0 ? View.GONE : View.VISIBLE);
        updateTotal();
    }

    // ------------------------------------------------------------------ helpers

    private View row(LayoutInflater inflater, LinearLayout parent, int icon, String title, String meta) {
        View row = inflater.inflate(R.layout.item_data_row, parent, false);
        ImageView tile = row.findViewById(R.id.data_row_icon);
        tile.setImageResource(icon);
        tile.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.rr_navy_light)));
        ((TextView) row.findViewById(R.id.data_row_title)).setText(title);
        TextView metaView = row.findViewById(R.id.data_row_meta);
        metaView.setMaxLines(4);
        metaView.setText(meta);
        return row;
    }

    private void updateTotal() {
        View view = getView();
        if (view == null) return;
        int total = waitingCenters + waitingAdvisories + waitingRequests;
        ((TextView) view.findViewById(R.id.finalize_total)).setText(total == 0 ? "Nothing waiting for you"
                : total + (total == 1 ? " item waits for you" : " items wait for you"));
    }

    private TextView empty(String text) {
        TextView empty = new TextView(requireContext());
        empty.setText(text);
        empty.setTextAppearance(R.style.TextAppearance_RR_Caption);
        return empty;
    }
}
