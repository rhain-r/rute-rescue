package com.rutarescue.ui.resident.ligtas;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rutarescue.R;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.ligtas.StatusChange;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.ImageCompressor;
import com.rutarescue.ui.common.SimpleListAdapter;
import com.rutarescue.util.Formats;

import java.io.IOException;

/**
 * The resident's Ligtas applications with status, the moderator's latest message and the full
 * history, and the verification QR for approved ones. Readable offline from the cache.
 * Since 2026-10-20 a decision can change later; "Needs documents" shows "Send new documents".
 */
public class LigtasStatusActivity extends BaseActivity {

    private ListenerHandle listener = ListenerHandle.NONE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        applySystemBarInsets(findViewById(R.id.list_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.my_applications);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());

        TextView intro = findViewById(R.id.list_intro);
        intro.setVisibility(View.VISIBLE);
        intro.setText("Apply from My Record > Apply for Ligtas Priority. Approved applications show a QR that "
                + "contains only an application ID.");

        TextView freshness = findViewById(R.id.list_freshness);
        TextView empty = findViewById(R.id.list_empty);
        RecyclerView recycler = findViewById(R.id.list_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        SimpleListAdapter<LigtasApplication> adapter = new SimpleListAdapter<>(R.layout.item_ligtas, this::bind);
        recycler.setAdapter(adapter);

        listener = container().ligtas().listenMine(container().session().getProfile().getUid(), ui(data -> {
            freshness.setVisibility(View.VISIBLE);
            Badges.freshness(freshness, data.getFreshness(), data.getRetrievedAt(), "");
            adapter.submit(data.getData());
            empty.setVisibility(data.getData().isEmpty() ? View.VISIBLE : View.GONE);
            empty.setText("No applications yet.");
        }));
    }

    private void bind(View row, LigtasApplication application, int position) {
        ((TextView) row.findViewById(R.id.ligtas_title)).setText(application.getMemberName());
        ((TextView) row.findViewById(R.id.ligtas_meta)).setText(application.getCategory().getLabel()
                + " • submitted " + Formats.relative(application.getSubmittedAt(), System.currentTimeMillis()));
        TextView state = row.findViewById(R.id.ligtas_badge);
        switch (application.getStatus()) {
            case APPROVED: Badges.label(state, "Approved", Badges.Tone.GREEN); break;
            case REJECTED: Badges.label(state, "Rejected", Badges.Tone.RED); break;
            case NEEDS_DOCUMENTS: Badges.label(state, "Needs documents", Badges.Tone.ORANGE); break;
            default: Badges.label(state, "Pending", Badges.Tone.NAVY);
        }
        StringBuilder history = new StringBuilder(application.getJustification());
        history.append("\n\nHistory:");
        for (StatusChange change : application.getHistory()) {
            history.append("\n• ").append(change.getStatus().getLabel()).append(" • ")
                    .append(Formats.dateTime(change.getAt()));
            if (!change.getReason().isEmpty()) history.append(" — ").append(change.getReason());
        }
        ((TextView) row.findViewById(R.id.ligtas_body)).setText(history);
        boolean decided = !application.getReviewReason().isEmpty();
        row.findViewById(R.id.ligtas_proof).setVisibility(View.VISIBLE);
        String city = application.getCity() == null ? "" : application.getCity().getDisplayName() + " ";
        ((TextView) row.findViewById(R.id.ligtas_footer)).setText(
                application.getStatus() == ApplicationStatus.PENDING && decided
                        ? "New documents sent. Waiting for the " + city + "moderator."
                        : decided ? "Message from the " + city + "moderator: " + application.getReviewReason()
                        : "Waiting for review by the " + city + "moderator");
        View resubmit = row.findViewById(R.id.ligtas_resubmit);
        resubmit.setVisibility(application.getStatus() == ApplicationStatus.NEEDS_DOCUMENTS ? View.VISIBLE : View.GONE);
        resubmit.setOnClickListener(v -> resubmit(application));
        View qr = row.findViewById(R.id.ligtas_qr);
        qr.setVisibility(application.getStatus() == ApplicationStatus.APPROVED ? View.VISIBLE : View.GONE);
        qr.setOnClickListener(v -> Dialogs.qr(this, getString(R.string.open_verification_record), application.getQrPayload()));
    }

    /**
     * Sends the synthetic demo proof again (this class demo never uses real documents); the
     * application goes back to "Pending" for the moderator.
     */
    private void resubmit(LigtasApplication application) {
        Dialogs.confirm(this, getString(R.string.resubmit_documents), "Send the demo proof again for "
                        + application.getMemberName() + "? The moderator will review it again.", getString(R.string.send_request),
                () -> container().executors.io().execute(() -> {
                    try {
                        byte[] proof = ImageCompressor.compressAsset(getAssets(), LigtasApplyActivity.DEMO_PROOF_ASSET);
                        container().executors.main().execute(() -> container().ligtas().resubmit(
                                container().session().getProfile(), application, proof,
                                ui(ignored -> showMessage("New documents sent. The moderator will review them."))));
                    } catch (IOException e) {
                        container().executors.main().execute(() -> showError(e));
                    }
                }));
    }

    @Override
    protected void onDestroy() {
        listener.remove();
        super.onDestroy();
    }
}
