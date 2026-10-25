package com.rutarescue.ui.lgu.reviews;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.rutarescue.R;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseFragment;
import com.rutarescue.ui.common.SimpleListAdapter;
import com.rutarescue.ui.moderator.QrCheck;
import com.rutarescue.ui.resident.home.InboxActivity;
import com.rutarescue.util.Formats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ligtas Priority reviews (Figma "LGU / Ligtas Reviews"), the moderator's tab: Pending / Needs
 * documents / Approved / Rejected for the moderator's own city only, search, and QR
 * verification of approved applications. Since 2026-10-22 the same scanner (or a typed code)
 * opens a resident's priority ID and its document checklist.
 */
public class LguReviewsFragment extends BaseFragment {

    private StaffProfile staff;
    private SimpleListAdapter<LigtasApplication> adapter;
    private List<LigtasApplication> all = new ArrayList<>();
    private ApplicationStatus tab = ApplicationStatus.PENDING;
    private EditText search;

    /** One scanner for both QR kinds (2026-10-22): a Ligtas application or a resident's priority ID. */
    private final ActivityResultLauncher<ScanOptions> scanner = registerForActivityResult(new ScanContract(), result -> {
        if (result.getContents() != null) open(result.getContents());
    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_lgu_reviews, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        staff = container().session().requireStaff();
        ((TextView) view.findViewById(R.id.header_eyebrow)).setText(staff.canDecideLigtas()
                ? R.string.role_moderator_eyebrow : R.string.eyebrow_lgu);
        view.findViewById(R.id.header_bell).setOnClickListener(v -> startActivity(new Intent(requireContext(), InboxActivity.class)));

        RecyclerView list = view.findViewById(R.id.reviews_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SimpleListAdapter<>(R.layout.item_ligtas, this::bind);
        list.setAdapter(adapter);

        ChipGroup tabs = view.findViewById(R.id.reviews_tabs);
        tabs.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            tab = id == R.id.reviews_tab_approved ? ApplicationStatus.APPROVED
                    : id == R.id.reviews_tab_rejected ? ApplicationStatus.REJECTED
                    : id == R.id.reviews_tab_documents ? ApplicationStatus.NEEDS_DOCUMENTS : ApplicationStatus.PENDING;
            applyFilter();
        });
        search = view.findViewById(R.id.reviews_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter();
            }
        });
        view.findViewById(R.id.reviews_priority_check).setOnClickListener(v -> QrCheck.choose(requireContext(),
                () -> scanner.launch(QrCheck.scanOptions()), this::open));

        track(container().ligtas().listenForReviewer(staff, ui(this::onApplications)));
    }

    /** A scanned or typed code: the priority ID check or the Ligtas application. */
    private void open(String contents) {
        Intent destination = QrCheck.destination(requireContext(), contents);
        if (destination == null) showMessage("This is not a RutaRescue priority ID or Ligtas QR code.");
        else startActivity(destination);
    }

    private void onApplications(SourcedData<List<LigtasApplication>> data) {
        all = data.getData();
        Badges.freshness(requireView().findViewById(R.id.reviews_freshness), data.getFreshness(), data.getRetrievedAt(), "");
        int pending = 0, documents = 0, approved = 0, rejected = 0;
        for (LigtasApplication application : all) {
            if (application.getStatus() == ApplicationStatus.PENDING) pending++;
            else if (application.getStatus() == ApplicationStatus.NEEDS_DOCUMENTS) documents++;
            else if (application.getStatus() == ApplicationStatus.APPROVED) approved++;
            else rejected++;
        }
        View view = requireView();
        ((Chip) view.findViewById(R.id.reviews_tab_pending)).setText(getString(R.string.pending) + " " + pending);
        ((Chip) view.findViewById(R.id.reviews_tab_documents)).setText(getString(R.string.needs_documents) + " " + documents);
        ((Chip) view.findViewById(R.id.reviews_tab_approved)).setText(getString(R.string.approved) + " " + approved);
        ((Chip) view.findViewById(R.id.reviews_tab_rejected)).setText(getString(R.string.rejected) + " " + rejected);
        ((TextView) view.findViewById(R.id.reviews_pending_count)).setText(pending + " pending");
        applyFilter();
    }

    private void applyFilter() {
        if (adapter == null) return;
        String needle = search.getText().toString().strip().toLowerCase(Locale.ROOT);
        List<LigtasApplication> shown = new ArrayList<>();
        for (LigtasApplication application : all) {
            if (application.getStatus() != tab) continue;
            String haystack = (application.getMemberName() + " " + application.getApplicantName() + " "
                    + application.getCategory().getLabel()).toLowerCase(Locale.ROOT);
            if (!needle.isEmpty() && !haystack.contains(needle)) continue;
            shown.add(application);
        }
        adapter.submit(shown);
        TextView empty = requireView().findViewById(R.id.reviews_empty);
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText("No " + tab.getLabel().toLowerCase(Locale.ROOT) + " applications in "
                + staff.getJurisdiction().getDisplayName() + ".");
    }

    /** Figma "Application / ..." card. */
    private void bind(View row, LigtasApplication application, int position) {
        long now = System.currentTimeMillis();
        boolean urgent = application.isWaitingLong(now);
        row.findViewById(R.id.ligtas_avatar).setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), urgent ? R.color.rr_red_soft : R.color.rr_teal_soft)));
        ((TextView) row.findViewById(R.id.ligtas_title)).setText(application.getMemberName());
        ((TextView) row.findViewById(R.id.ligtas_meta)).setText("Submitted " + Formats.relative(application.getSubmittedAt(), now)
                + " • by " + application.getApplicantName());
        TextView state = row.findViewById(R.id.ligtas_badge);
        if (urgent) Badges.label(state, "Urgent", Badges.Tone.RED);
        else if (application.getStatus() == ApplicationStatus.PENDING) Badges.label(state, "New", Badges.Tone.TEAL);
        else if (application.getStatus() == ApplicationStatus.APPROVED) Badges.label(state, "Approved", Badges.Tone.GREEN);
        else if (application.getStatus() == ApplicationStatus.NEEDS_DOCUMENTS) Badges.label(state, "Needs documents", Badges.Tone.ORANGE);
        else Badges.label(state, "Rejected", Badges.Tone.RED);
        ((TextView) row.findViewById(R.id.ligtas_body)).setText(application.getCategory().getLabel() + ": "
                + application.getJustification());
        boolean pending = application.getStatus() == ApplicationStatus.PENDING;
        row.findViewById(R.id.ligtas_proof).setVisibility(pending ? View.VISIBLE : View.GONE);
        // Decided applications can still be changed (a mistaken approval): say so on the card.
        ((TextView) row.findViewById(R.id.ligtas_footer)).setText(pending
                ? getString(R.string.review_application)
                : "Message: " + application.getReviewReason() + "\n" + getString(R.string.change_status) + " →");
        row.setOnClickListener(v -> startActivity(ReviewDetailActivity.intent(requireContext(), application.getId(), false)));
    }
}
