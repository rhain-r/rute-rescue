package com.rutarescue.ui.lgu.reviews;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rutarescue.R;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.ligtas.StatusChange;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.UiCallback;
import com.rutarescue.util.Formats;
import com.rutarescue.validation.TextRules;

import java.util.Locale;

/**
 * Review one Ligtas application: synthetic proof, justification, history, and the moderator's
 * decision (approve, needs documents or reject) with a message the resident will read. Since
 * 2026-10-20 the decision can be changed later, and "Email the resident" opens the moderator's
 * email app with the decision already written. The decision and its audit event are saved
 * together; the database rules reject decisions from other cities and from the LGU role.
 * Opened from a scanned QR, it shows the verification result first.
 */
public class ReviewDetailActivity extends BaseActivity {

    private static final String EXTRA_ID = "application_id";
    private static final String EXTRA_FROM_QR = "from_qr";

    private StaffProfile staff;
    private LigtasApplication application;
    private ListenerHandle listener = ListenerHandle.NONE;
    private boolean proofLoaded;
    /** The status the chips were last set from, so a live update never overwrites the moderator's pick. */
    private ApplicationStatus shownStatus;

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
        staff = container().session().requireStaff();

        listener = container().ligtas().listenOne(getIntent().getStringExtra(EXTRA_ID), UiCallback.of(this,
                data -> bind(data.getData()), error -> {
                    showError(error);
                    TextView banner = findViewById(R.id.review_qr_banner);
                    banner.setVisibility(View.VISIBLE);
                    banner.setText("This application could not be opened. It may belong to another jurisdiction "
                            + "or the QR may be invalid.");
                }));
        findViewById(R.id.review_save).setOnClickListener(v -> decide(chosenStatus()));
        ((ChipGroup) findViewById(R.id.review_choices)).setOnCheckedStateChangeListener((group, ids) ->
                findViewById(R.id.review_choice_error).setVisibility(View.GONE));
        findViewById(R.id.review_change).setOnClickListener(v -> openStatusEditor());
        findViewById(R.id.review_email).setOnClickListener(v -> emailResident());
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

        // Moderators of the city can decide, and change a decision later; the LGU only reads it.
        boolean canDecide = staff.canReview(application);
        findViewById(R.id.review_decision).setVisibility(canDecide ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.review_decision_title)).setText(application.isDecided()
                ? R.string.change_status_title : R.string.decision_title);
        ((TextView) findViewById(R.id.review_save)).setText(application.isDecided()
                ? R.string.save_status : R.string.save_decision);
        bindCurrentStatus(canDecide);
        if (application.getStatus() != shownStatus) {
            shownStatus = application.getStatus();
            int chip = choiceFor(shownStatus);
            ChipGroup choices = findViewById(R.id.review_choices);
            if (chip == View.NO_ID) choices.clearCheck(); else choices.check(chip);
        }
        boolean canEmail = canDecide && application.isDecided() && !application.getApplicantEmail().isEmpty();
        findViewById(R.id.review_email).setVisibility(canEmail ? View.VISIBLE : View.GONE);

        if (!proofLoaded && !application.getProofRef().isEmpty()) {
            proofLoaded = true;
            container().ligtas().downloadProof(application.getProofRef(), UiCallback.of(this, bytes ->
                            ((ImageView) findViewById(R.id.review_proof)).setImageBitmap(
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length)),
                    error -> ((TextView) findViewById(R.id.review_proof_info)).setText(
                            "Proof image unavailable offline or not accessible.")));
        }
    }

    /** The top card of a decided application: its current status and message, and "Change status". */
    private void bindCurrentStatus(boolean canDecide) {
        View card = findViewById(R.id.review_current);
        if (!canDecide || !application.isDecided()) {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.review_current_text)).setText("Current status: "
                + application.getStatus().getLabel() + " • " + Formats.dateTime(application.getDecidedAt())
                + "\nMessage to the resident: " + application.getReviewReason()
                + "\nApproved by mistake or something missing? Change the status below.");
    }

    /** "Change status": scrolls to the status choices and opens the message box. */
    private void openStatusEditor() {
        View panel = findViewById(R.id.review_decision);
        ScrollView scroll = findViewById(R.id.review_root);
        scroll.post(() -> scroll.smoothScrollTo(0, panel.getTop()));
        TextInputEditText message = findViewById(R.id.review_reason);
        message.requestFocus();
    }

    private static int choiceFor(ApplicationStatus status) {
        switch (status) {
            case APPROVED: return R.id.review_choice_approved;
            case NEEDS_DOCUMENTS: return R.id.review_choice_documents;
            case REJECTED: return R.id.review_choice_rejected;
            default: return View.NO_ID;
        }
    }

    /** The status picked in the chips, or null when none is picked yet. */
    private ApplicationStatus chosenStatus() {
        int id = ((ChipGroup) findViewById(R.id.review_choices)).getCheckedChipId();
        if (id == R.id.review_choice_approved) return ApplicationStatus.APPROVED;
        if (id == R.id.review_choice_documents) return ApplicationStatus.NEEDS_DOCUMENTS;
        if (id == R.id.review_choice_rejected) return ApplicationStatus.REJECTED;
        return null;
    }

    private void decide(ApplicationStatus decision) {
        if (application == null) return;
        if (decision == null) {
            findViewById(R.id.review_choice_error).setVisibility(View.VISIBLE);
            return;
        }
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
        String question = application.getStatus() == decision
                ? "Keep " + application.getMemberName() + " as " + decision.getLabel()
                + " and send the resident this new message?"
                : application.isDecided()
                ? "Change the status for " + application.getMemberName() + " from " + application.getStatus().getLabel()
                + " to " + decision.getLabel() + "? The resident will see your message."
                : "Mark the application for " + application.getMemberName() + " as " + decision.getLabel()
                + "? The resident will see your message.";
        String action = decision == ApplicationStatus.APPROVED ? getString(R.string.approve)
                : decision == ApplicationStatus.REJECTED ? getString(R.string.reject) : "Ask for documents";
        Dialogs.confirm(this, decision.getLabel(), question + " This is recorded in the audit trail.", action,
                () -> container().ligtas().decide(staff, application, decision, reason, UiCallback.of(this, ignored -> {
                    ((TextInputEditText) findViewById(R.id.review_reason)).setText("");
                    ScrollView scroll = findViewById(R.id.review_root);
                    scroll.post(() -> scroll.smoothScrollTo(0, 0)); // show the new "Current status"
                    showMessage("Saved: " + decision.getLabel() + (application.getApplicantEmail().isEmpty() ? ""
                            : ". You can also email the resident at the bottom."));
                }, error -> {
                    if (error instanceof ValidationException) reasonLayout.setError(error.getMessage());
                    else showError(error);
                })));
    }

    /**
     * Opens the moderator's own email app with the resident's address, a subject and the decision
     * already written. Sending stays the moderator's choice; no server is needed (free plan).
     */
    private void emailResident() {
        if (application == null || application.getApplicantEmail().isEmpty()) return;
        String city = application.getCity() == null ? "" : application.getCity().getDisplayName();
        String body = "Hello " + application.getApplicantName() + ",\n\n"
                + "Your Ligtas Priority application for " + application.getMemberName() + " is now: "
                + application.getStatus().getLabel() + ".\n\n"
                + "Message from the " + city + " moderator:\n" + application.getReviewReason() + "\n\n"
                + "You can also see this in the RutaRescue app under My Record > Ligtas Priority.\n\n"
                + staff.getDisplayName() + "\n" + staff.getRoleLabel();
        Intent email = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                .putExtra(Intent.EXTRA_EMAIL, new String[]{application.getApplicantEmail()})
                .putExtra(Intent.EXTRA_SUBJECT, "RutaRescue: your Ligtas Priority application is "
                        + application.getStatus().getLabel().toLowerCase(Locale.ROOT))
                .putExtra(Intent.EXTRA_TEXT, body);
        try {
            startActivity(email);
        } catch (ActivityNotFoundException noEmailApp) {
            showMessage("No email app on this phone. The resident can still see the message in RutaRescue.");
        }
    }

    @Override
    protected void onDestroy() {
        listener.remove();
        super.onDestroy();
    }
}
