package com.rutarescue.ui.resident.record;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.rutarescue.R;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.family.FamilyMember;
import com.rutarescue.model.family.FamilyRecord;
import com.rutarescue.model.family.TrustedContact;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.ui.about.AboutActivity;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseFragment;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.UiCallback;
import com.rutarescue.ui.resident.home.InboxActivity;
import com.rutarescue.ui.resident.ligtas.LigtasApplyActivity;
import com.rutarescue.ui.resident.ligtas.LigtasStatusActivity;
import com.rutarescue.util.Formats;

import java.util.ArrayList;
import java.util.List;

/**
 * My Record (DOCX section 4, Figma "Client / My Record"): the family evacuation record,
 * Ligtas Priority status with the verification QR, trusted contacts, and the Lab files.
 * Everything here is readable offline from the cache.
 */
public class MyRecordFragment extends BaseFragment {

    private FamilyRecord record;
    private List<LigtasApplication> applications = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_my_record, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        view.findViewById(R.id.header_bell).setOnClickListener(v -> startActivity(new Intent(requireContext(), InboxActivity.class)));
        view.findViewById(R.id.header_logo).setOnClickListener(v -> startActivity(new Intent(requireContext(), AboutActivity.class)));
        view.findViewById(R.id.record_edit).setOnClickListener(v -> startActivity(new Intent(requireContext(), FamilyEditActivity.class)));
        view.findViewById(R.id.record_create).setOnClickListener(v -> startActivity(new Intent(requireContext(), FamilyEditActivity.class)));
        view.findViewById(R.id.record_lab_open).setOnClickListener(v -> startActivity(new Intent(requireContext(), LegacyDataActivity.class)));
        view.findViewById(R.id.record_ligtas_apply).setOnClickListener(v -> startActivity(new Intent(requireContext(), LigtasApplyActivity.class)));
        view.findViewById(R.id.record_ligtas_list).setOnClickListener(v -> startActivity(new Intent(requireContext(), LigtasStatusActivity.class)));

        UserProfile profile = profile();
        if (profile != null) {
            track(container().ligtas().listenMine(profile.getUid(), ui(this::onApplications)));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public void onTabShown() {
        if (getView() != null) reload();
    }

    private void reload() {
        UserProfile profile = profile();
        if (profile == null) return;
        // The Lab files are read after the record loads, because loading updates family_records.txt.
        container().family().load(profile.getUid(), UiCallback.of(this, data -> {
            onRecord(data);
            showLabStatus();
        }, error -> {
            showError(error);
            showLabStatus();
        }));
    }

    private void showLabStatus() {
        container().family().readLocalFiles(UiCallback.of(this, status -> ((TextView) requireView()
                .findViewById(R.id.record_lab_status)).setText("family_records.txt: "
                + status.textReport.getRecords().size() + " record(s) • system.dat: " + status.systemState
                + (status.lastMirrorError.isEmpty() ? "" : "\nLast mirror problem: " + status.lastMirrorError)),
                error -> { }));
    }

    private void onRecord(SourcedData<FamilyRecord> data) {
        View view = requireView();
        record = data.getData();
        view.findViewById(R.id.record_empty).setVisibility(record == null ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.record_content).setVisibility(record == null ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.record_edit).setVisibility(record == null ? View.GONE : View.VISIBLE);
        if (record == null) return;

        // Figma shows a plain 52 dp circle; the initials are kept only for screen readers.
        view.findViewById(R.id.record_avatar).setContentDescription(initials(record.getHeadName()));
        ((TextView) view.findViewById(R.id.record_head)).setText(record.getHeadName());
        ((TextView) view.findViewById(R.id.record_address)).setText(record.getAddress()
                + (record.getCity() == null ? "" : ", " + record.getCity().getDisplayName()));
        ((TextView) view.findViewById(R.id.record_id)).setText(record.getRecordId() + " • Preferred center: "
                + record.getPreferredCenter());
        view.findViewById(R.id.record_review).setVisibility(record.needsMemberReview() ? View.VISIBLE : View.GONE);
        showSyncStatus(view.findViewById(R.id.record_freshness), data);

        ((TextView) view.findViewById(R.id.record_member_count)).setText(record.getFamilySize()
                + (record.getFamilySize() == 1 ? " person" : " people"));
        LinearLayout members = view.findViewById(R.id.record_members);
        members.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (FamilyMember member : record.getMembers()) {
            // Figma "Member": status dot, name, description, READY / PRIORITY
            View row = inflater.inflate(R.layout.item_member, members, false);
            boolean priority = member.needsPriorityHelp();
            int color = ContextCompat.getColor(requireContext(), priority ? R.color.rr_orange : R.color.rr_green);
            row.findViewById(R.id.member_dot).setBackgroundTintList(ColorStateList.valueOf(color));
            ((TextView) row.findViewById(R.id.member_name)).setText(member.getFullName());
            ((TextView) row.findViewById(R.id.member_meta)).setText(member.describe());
            TextView state = row.findViewById(R.id.member_state);
            state.setText(priority ? R.string.member_priority : R.string.member_ready);
            state.setTextColor(color);
            members.addView(row);
        }

        StringBuilder contacts = new StringBuilder();
        for (TrustedContact contact : record.getTrustedContacts()) {
            if (contacts.length() > 0) contacts.append('\n');
            contacts.append(contact.describe());
        }
        ((TextView) view.findViewById(R.id.record_contacts)).setText(contacts.length() == 0
                ? "No trusted contacts yet. Add them with Edit." : contacts);
        bindLigtas();
    }

    private void onApplications(SourcedData<List<LigtasApplication>> data) {
        applications = data.getData();
        bindLigtas();
    }

    private void bindLigtas() {
        View view = getView();
        if (view == null) return;
        TextView title = view.findViewById(R.id.record_ligtas_title);
        TextView body = view.findViewById(R.id.record_ligtas_body);
        TextView badge = view.findViewById(R.id.record_ligtas_badge);
        View qr = view.findViewById(R.id.record_ligtas_qr);
        View apply = view.findViewById(R.id.record_ligtas_apply);
        apply.setVisibility(record == null ? View.GONE : View.VISIBLE);

        LigtasApplication approved = null;
        LigtasApplication latest = applications.isEmpty() ? null : applications.get(0);
        for (LigtasApplication application : applications) {
            if (application.getStatus() == ApplicationStatus.APPROVED) {
                approved = application;
                break;
            }
        }
        if (approved != null) {
            final LigtasApplication shown = approved;
            title.setText(shown.getMemberName() + " is approved for priority assistance.");
            body.setText("Reviewed by your LGU: " + shown.getReviewReason()
                    + "\nShow the verification QR when assistance is available.");
            badge.setVisibility(View.VISIBLE);
            Badges.application(badge, ApplicationStatus.APPROVED);
            qr.setVisibility(View.VISIBLE);
            qr.setOnClickListener(v -> Dialogs.qr(requireContext(), getString(R.string.open_verification_record),
                    shown.getQrPayload()));
        } else if (latest != null) {
            title.setText("Application for " + latest.getMemberName() + ": " + latest.getStatus().getLabel());
            body.setText(latest.getStatus() == ApplicationStatus.REJECTED
                    ? "Reason: " + latest.getReviewReason() : "Your LGU will review the synthetic proof you attached.");
            badge.setVisibility(View.VISIBLE);
            Badges.application(badge, latest.getStatus());
            qr.setVisibility(View.GONE);
        } else {
            title.setText("Priority help for vulnerable family members");
            body.setText(record == null ? "Create your family record first."
                    : "Seniors, PWDs, pregnant members, infants or members with medical needs can apply. Your LGU reviews each request.");
            badge.setVisibility(View.GONE);
            qr.setVisibility(View.GONE);
        }
    }

    /**
     * Figma "Verified Address" row style (check icon + green label), used truthfully for the
     * record's sync state: live, cached (with age) or stale. DOCX section 11: never present cached
     * data as live.
     */
    private void showSyncStatus(TextView status, SourcedData<FamilyRecord> data) {
        String when = data.getRetrievedAt() > 0
                ? Formats.relative(data.getRetrievedAt(), System.currentTimeMillis()) : "not yet synced";
        int color;
        String text;
        switch (data.getFreshness()) {
            case LIVE:
                color = R.color.rr_green;
                text = "Record synced with your LGU database";
                break;
            case STALE:
                color = R.color.rr_orange;
                text = "Stale copy on this device • " + when;
                break;
            default:
                color = R.color.rr_navy_light;
                text = "Cached copy on this device • " + when;
        }
        int resolved = ContextCompat.getColor(requireContext(), color);
        status.setText(text);
        status.setTextColor(resolved);
        TextViewCompat.setCompoundDrawableTintList(status, ColorStateList.valueOf(resolved));
    }

    private static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length && out.length() < 2; i++) {
            if (!parts[i].isEmpty()) out.appendCodePoint(parts[i].codePointAt(0));
        }
        return out.toString();
    }
}
