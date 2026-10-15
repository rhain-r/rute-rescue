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
import com.rutarescue.ui.common.SimpleListAdapter;
import com.rutarescue.util.Formats;

/**
 * The resident's Ligtas applications with status, reviewer reason and full history, and
 * the verification QR for approved ones. Readable offline from the cache.
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
            default: Badges.label(state, "Pending", Badges.Tone.ORANGE);
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
        ((TextView) row.findViewById(R.id.ligtas_footer)).setText(decided
                ? "LGU reason: " + application.getReviewReason() : "Waiting for LGU review");
        View qr = row.findViewById(R.id.ligtas_qr);
        qr.setVisibility(application.getStatus() == ApplicationStatus.APPROVED ? View.VISIBLE : View.GONE);
        qr.setOnClickListener(v -> Dialogs.qr(this, getString(R.string.open_verification_record), application.getQrPayload()));
    }

    @Override
    protected void onDestroy() {
        listener.remove();
        super.onDestroy();
    }
}
