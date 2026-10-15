package com.rutarescue.ui.lgu.reviews;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rutarescue.R;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.ligtas.StatusChange;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.UiCallback;
import com.rutarescue.util.Formats;
import com.rutarescue.validation.TextRules;

/**
 * Review one Ligtas application: synthetic proof, justification, history, and the
 * approve/reject decision with a required reason. The decision and its audit event are
 * saved together; the database rules reject decisions from other jurisdictions.
 * Opened from a scanned QR, it shows the verification result first.
 */
public class ReviewDetailActivity extends BaseActivity {

    private static final String EXTRA_ID = "application_id";
    private static final String EXTRA_FROM_QR = "from_qr";

    private LguProfile lgu;
    private LigtasApplication application;
    private ListenerHandle listener = ListenerHandle.NONE;
    private boolean proofLoaded;

    public static Intent intent(Context context, String applicationId, boolean fromQr) {
        return new Intent(context, ReviewDetailActivity.class)
                .putExtra(EXTRA_ID, applicationId)
                .putExtra(EXTRA_FROM_QR, fromQr);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review_detail);
        applySystemBarInsets(findViewById(R.id.review_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText("Ligtas application");
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        lgu = container().session().requireLgu();

        listener = container().ligtas().listenOne(getIntent().getStringExtra(EXTRA_ID), UiCallback.of(this,
                data -> bind(data.getData()), error -> {
                    showError(error);
                    TextView banner = findViewById(R.id.review_qr_banner);
                    banner.setVisibility(View.VISIBLE);
                    banner.setText("This application could not be opened. It may belong to another jurisdiction "
                            + "or the QR may be invalid.");
                }));
        findViewById(R.id.review_approve).setOnClickListener(v -> decide(true));
        findViewById(R.id.review_reject).setOnClickListener(v -> decide(false));
    }

    private void bind(LigtasApplication loaded) {
        application = loaded;
        ((TextView) findViewById(R.id.review_member)).setText(application.getMemberName());
        Badges.application(findViewById(R.id.review_status), application.getStatus());
        ((TextView) findViewById(R.id.review_meta)).setText(application.getCategory().getLabel() + " • "
                + (application.getCity() == null ? "" : application.getCity().getDisplayName()) + " • submitted by "
                + application.getApplicantName() + " • " + Formats.dateTime(application.getSubmittedAt()));
        ((TextView) findViewById(R.id.review_justification)).setText(application.getJustification());

        StringBuilder history = new StringBuilder();
        for (StatusChange change : application.getHistory()) {
            if (history.length() > 0) history.append("\n\n");
            history.append(Formats.dateTime(change.getAt())).append(" • ").append(change.getStatus().getLabel());
            if (!change.getReason().isEmpty()) history.append("\n").append(change.getReason());
        }
        ((TextView) findViewById(R.id.review_history)).setText(history);

        if (getIntent().getBooleanExtra(EXTRA_FROM_QR, false)) {
            TextView banner = findViewById(R.id.review_qr_banner);
            banner.setVisibility(View.VISIBLE);
            banner.setText(application.getStatus() == ApplicationStatus.APPROVED
                    ? "QR verified: this application is APPROVED for priority assistance."
                    : "QR checked: this application is " + application.getStatus().getLabel().toUpperCase()
                    + " - priority assistance is not approved.");
        }

        boolean canDecide = application.getStatus() == ApplicationStatus.PENDING && lgu.canReview(application);
        findViewById(R.id.review_decision).setVisibility(canDecide ? View.VISIBLE : View.GONE);

        if (!proofLoaded && !application.getProofRef().isEmpty()) {
            proofLoaded = true;
            container().ligtas().downloadProof(application.getProofRef(), UiCallback.of(this, bytes ->
                            ((ImageView) findViewById(R.id.review_proof)).setImageBitmap(
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length)),
                    error -> ((TextView) findViewById(R.id.review_proof_info)).setText(
                            "Proof image unavailable offline or not accessible.")));
        }
    }

    private void decide(boolean approve) {
        if (application == null) return;
        TextInputLayout reasonLayout = findViewById(R.id.review_reason_layout);
        reasonLayout.setError(null);
        String reason = Dialogs.text((TextInputEditText) findViewById(R.id.review_reason));
        try {
            // Same rule as LigtasApplication.decide(), checked first so the error shows before the dialog.
            TextRules.multilineText("reason", reason, 5, 300);
        } catch (ValidationException invalid) {
            reasonLayout.setError(invalid.getMessage());
            return;
        }
        Dialogs.confirm(this, approve ? getString(R.string.approve) : getString(R.string.reject),
                (approve ? "Approve" : "Reject") + " the application for " + application.getMemberName()
                        + "? This is recorded in the audit trail.", approve ? getString(R.string.approve) : getString(R.string.reject),
                () -> container().ligtas().decide(lgu, application, approve, reason, UiCallback.of(this, ignored -> {
                    showMessage(approve ? "Approved" : "Rejected");
                }, error -> {
                    if (error instanceof ValidationException) reasonLayout.setError(error.getMessage());
                    else showError(error);
                })));
    }

    @Override
    protected void onDestroy() {
        listener.remove();
        super.onDestroy();
    }
}
