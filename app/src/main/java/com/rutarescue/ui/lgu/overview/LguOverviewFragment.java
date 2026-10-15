package com.rutarescue.ui.lgu.overview;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rutarescue.R;
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.route.RoadClosure;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.ui.about.AboutActivity;
import com.rutarescue.ui.common.BaseFragment;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.ViewBinders;
import com.rutarescue.ui.lgu.data.CenterEditorActivity;
import com.rutarescue.ui.resident.home.InboxActivity;
import com.rutarescue.ui.shell.MainShellActivity;
import com.rutarescue.util.Formats;

import java.util.ArrayList;
import java.util.List;

/**
 * LGU Overview (Figma "LGU / Overview"). Every metric is computed from real records of
 * the officer's jurisdiction (nothing is invented): pending reviews, active closures,
 * published centers and audit events in the last 24 hours.
 */
public class LguOverviewFragment extends BaseFragment {

    private static final long DAY = 24L * 60 * 60 * 1000;

    private LguProfile lgu;
    private List<AuditEvent> auditEvents = new ArrayList<>();
    private int publishedCenters;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_lgu_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        lgu = container().session().requireLgu();
        ((TextView) view.findViewById(R.id.header_eyebrow)).setText(R.string.eyebrow_lgu);
        view.findViewById(R.id.header_bell).setOnClickListener(v -> startActivity(new Intent(requireContext(), InboxActivity.class)));
        view.findViewById(R.id.header_logo).setOnClickListener(v -> startActivity(new Intent(requireContext(), AboutActivity.class)));
        ((TextView) view.findViewById(R.id.lgu_hero_city)).setText(lgu.getJurisdiction().getDisplayName() + " • " + lgu.getPosition());

        MainShellActivity shell = (MainShellActivity) requireActivity();
        view.findViewById(R.id.lgu_op_reviews).setOnClickListener(v -> shell.selectTab(2));
        view.findViewById(R.id.lgu_op_advisory).setOnClickListener(v -> startActivity(new Intent(requireContext(), AdvisoryEditorActivity.class)));
        view.findViewById(R.id.lgu_view_report).setOnClickListener(v -> showReport());
        view.findViewById(R.id.lgu_compose_center).setOnClickListener(v -> startActivity(new Intent(requireContext(), CenterEditorActivity.class)));
        view.findViewById(R.id.lgu_compose_closure).setOnClickListener(v -> {
            shell.selectTab(1);
            showMessage("Long-press the map to mark a risk zone: report the closed or impassable road there.");
        });
        EditText composer = view.findViewById(R.id.lgu_composer);
        view.findViewById(R.id.lgu_compose_publish).setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), AdvisoryEditorActivity.class)
                    .putExtra(AdvisoryEditorActivity.EXTRA_MESSAGE, Dialogs.text(composer)));
            composer.setText("");
        });

        track(container().ligtas().listenForReviewer(lgu, ui(this::onApplications)));
        track(container().centers().listenForManager(lgu, ui(this::onCenters)));
        track(container().datasets().listenClosures(ui(this::onClosures)));
        track(container().audit().listenForCity(lgu.getJurisdiction(), ui(this::onAudit)));
        track(container().advisories().listenForCity(lgu.getJurisdiction(), ui(this::onAdvisories)));
        updateHeroBody();
    }

    private void onApplications(SourcedData<List<LigtasApplication>> data) {
        int pending = 0;
        int waitingLong = 0;
        long now = System.currentTimeMillis();
        for (LigtasApplication application : data.getData()) {
            if (application.getStatus() == ApplicationStatus.PENDING) pending++;
            if (application.isWaitingLong(now)) waitingLong++;
        }
        setMetric(R.id.lgu_metric_reviews, R.id.lgu_metric_reviews_sub, pending, waitingLong + " waiting > 24 h");
        ((TextView) requireView().findViewById(R.id.lgu_op_reviews_sub)).setText(pending + " resident request(s) waiting");
    }

    private void onCenters(SourcedData<List<EvacuationCenter>> data) {
        int unverified = 0;
        publishedCenters = 0;
        for (EvacuationCenter center : data.getData()) {
            if (center.isPublished()) publishedCenters++;
            if (!center.getVerification().isVerified()) unverified++;
        }
        setMetric(R.id.lgu_metric_centers, R.id.lgu_metric_centers_sub, publishedCenters,
                data.getData().isEmpty() ? "None added yet" : unverified == 0 ? "All verified" : unverified + " need verification");
        updateHeroBody();
    }

    private void onClosures(List<RoadClosure> closures) {
        int mine = 0;
        for (RoadClosure closure : closures) {
            if (closure.getCity() == lgu.getJurisdiction()) mine++;
        }
        setMetric(R.id.lgu_metric_closures, R.id.lgu_metric_closures_sub, mine, closures.size() + " active in Metro Manila");
    }

    private void onAudit(List<AuditEvent> events) {
        auditEvents = events;
        long now = System.currentTimeMillis();
        int recent = 0;
        for (AuditEvent event : events) {
            if (now - event.getTimestamp() < DAY) recent++;
        }
        setMetric(R.id.lgu_metric_updates, R.id.lgu_metric_updates_sub, recent, events.isEmpty() ? "No changes yet"
                : "Last " + Formats.relative(events.get(0).getTimestamp(), now));
    }

    private void onAdvisories(SourcedData<List<Advisory>> data) {
        View view = requireView();
        TextView alert = view.findViewById(R.id.lgu_hero_alert);
        Advisory latestPublished = null;
        for (Advisory advisory : data.getData()) {
            if (advisory.isPublished()) {
                latestPublished = advisory;
                break;
            }
        }
        alert.setText(latestPublished == null ? "No active advisory" : latestPublished.getLevel().getLabel());

        LinearLayout list = view.findViewById(R.id.lgu_advisories);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Advisory advisory : data.getData()) {
            View card = inflater.inflate(R.layout.item_advisory, list, false);
            ViewBinders.bindAdvisory(card, advisory);
            // "Helpful" / "Save update" are resident actions; the LGU edits the advisory instead.
            card.findViewById(R.id.advisory_helpful).setVisibility(View.GONE);
            card.findViewById(R.id.advisory_save).setVisibility(View.GONE);
            if (!advisory.isPublished()) {
                ((TextView) card.findViewById(R.id.advisory_meta)).setText("DRAFT (not visible to residents)");
            }
            card.setOnClickListener(v -> startActivity(new Intent(requireContext(), AdvisoryEditorActivity.class)
                    .putExtra(AdvisoryEditorActivity.EXTRA_ID, advisory.getId())));
            list.addView(card);
        }
    }

    private void updateHeroBody() {
        View view = getView();
        if (view == null) return;
        String version = container().hazards.getManifest().versionOf(FloodScenario.DEFAULT);
        ((TextView) view.findViewById(R.id.lgu_hero_body)).setText("Prepared risk data " + version
                + " (not live) • " + publishedCenters + " centers published");
    }

    private void setMetric(int valueId, int subId, int value, String sub) {
        View view = requireView();
        ((TextView) view.findViewById(valueId)).setText(String.valueOf(value));
        ((TextView) view.findViewById(subId)).setText(sub);
    }

    /** "View report": the latest entries of the jurisdiction's audit trail. */
    private void showReport() {
        StringBuilder text = new StringBuilder();
        int shown = 0;
        for (AuditEvent event : auditEvents) {
            if (shown++ == 30) break;
            text.append(Formats.dateTime(event.getTimestamp())).append(" • ").append(event.getAction())
                    .append(" ").append(event.getEntityType()).append('\n')
                    .append("   ").append(event.getReason()).append("\n\n");
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Audit trail • " + lgu.getJurisdiction().getDisplayName())
                .setMessage(text.length() == 0 ? "No recorded changes yet." : text.toString())
                .setPositiveButton("Close", null)
                .show();
    }
}
