package com.rutarescue.ui.lgu.overview;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rutarescue.R;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.advisory.AlertLevel;
import com.rutarescue.model.advisory.CityAlert;
import com.rutarescue.model.user.ModeratorProfile;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.UiCallback;

/**
 * Write, review or archive a community advisory (2026-10-22). What the screen offers depends on
 * the role (the profile's canPublishToResidents()) and the advisory's review state:
 *  - Moderator: writes an advisory about their own center and sends it to the LGU; can fix or
 *    withdraw it while it is pending; a declined one is read-only with the LGU's comment and
 *    "Write a new advisory" (EXTRA_COPY_FROM starts the new one from the declined text).
 *  - LGU: writes and publishes its own advisories (they carry the city's alert level), accepts or
 *    declines a moderator's pending advisory, hides or archives any advisory of the city.
 * Nobody picks an alert level here: only the LGU sets the city's level, on its Overview.
 */
public class AdvisoryEditorActivity extends BaseActivity {

    public static final String EXTRA_ID = "advisory_id";
    public static final String EXTRA_MESSAGE = "advisory_message";
    /** A declined advisory whose text starts a new one. */
    public static final String EXTRA_COPY_FROM = "advisory_copy_from";

    private StaffProfile staff;
    private boolean lgu;
    private Advisory advisory;
    private CityAlert cityAlert;
    private ListenerHandle alertListener = ListenerHandle.NONE;
    private TextInputEditText title;
    private TextInputEditText message;
    private MaterialSwitch published;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_advisory_editor);
        applySystemBarInsets(findViewById(R.id.advisory_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.advisory_editor);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        staff = container().session().requireStaff();
        lgu = staff.canPublishToResidents();

        title = findViewById(R.id.advisory_title_input);
        message = findViewById(R.id.advisory_message_input);
        published = findViewById(R.id.advisory_published);
        TextView city = findViewById(R.id.advisory_city);
        if (lgu) {
            city.setText(staff.getJurisdiction().getDisplayName() + " LGU");
            alertListener = container().alerts().listen(staff.getJurisdiction(), ui(alert -> {
                cityAlert = alert;
                showLevelInfo();
            }));
        } else {
            ModeratorProfile moderator = (ModeratorProfile) staff;
            city.setText((moderator.hasAssignedCenter() ? moderator.getCenterName() + " • " : "")
                    + staff.getJurisdiction().getDisplayName());
            // Moderator: no "Published" switch; the advisory goes to the LGU for approval.
            published.setVisibility(View.GONE);
            ((TextView) findViewById(R.id.advisory_rule)).setText(getString(R.string.advisory_rule_moderator,
                    staff.getJurisdiction().getDisplayName()));
            ((MaterialButton) findViewById(R.id.advisory_save)).setText(R.string.send_to_lgu);
        }
        showLevelInfo();

        String prefill = getIntent().getStringExtra(EXTRA_MESSAGE);
        if (prefill != null) message.setText(prefill);

        String id = getIntent().getStringExtra(EXTRA_ID);
        String copyFrom = getIntent().getStringExtra(EXTRA_COPY_FROM);
        if (id != null) {
            container().advisories().get(id, ui(this::fill));
        } else if (copyFrom != null) {
            container().advisories().get(copyFrom, ui(declined -> {
                title.setText(declined.getTitle());
                message.setText(declined.getMessage());
                showBanner("New advisory based on the declined one. The LGU commented: " + declined.getLguNote());
            }));
        }
        findViewById(R.id.advisory_save).setOnClickListener(v -> save());
        findViewById(R.id.advisory_accept).setOnClickListener(v -> accept());
        findViewById(R.id.advisory_decline).setOnClickListener(v -> decline());
        findViewById(R.id.advisory_rewrite).setOnClickListener(v -> rewrite());
        findViewById(R.id.advisory_archive).setOnClickListener(v -> archive());
    }

    @Override
    protected void onDestroy() {
        alertListener.remove();
        super.onDestroy();
    }

    private AlertLevel cityLevel() {
        return cityAlert == null ? AlertLevel.ADVISORY : cityAlert.currentLevel(System.currentTimeMillis());
    }

    private void showLevelInfo() {
        TextView info = findViewById(R.id.advisory_level_info);
        if (advisory != null && advisory.isPublished()) {
            info.setText(getString(R.string.advisory_level_published, advisory.getLevel().getLabel()));
        } else if (lgu) {
            info.setText(getString(R.string.advisory_level_lgu, staff.getJurisdiction().getDisplayName(),
                    cityLevel().getLabel()));
        } else {
            info.setText(R.string.advisory_level_moderator);
        }
    }

    private void fill(Advisory existing) {
        advisory = existing;
        title.setText(existing.getTitle());
        message.setText(existing.getMessage());
        published.setChecked(existing.isPublished());
        showLevelInfo();

        boolean mine = !lgu && staff instanceof ModeratorProfile
                && ((ModeratorProfile) staff).getCenterId().equals(existing.getCenterId());
        boolean editable;
        if (existing.isAwaitingLgu()) {
            String by = existing.getRequestedBy().isEmpty() ? "a moderator" : existing.getRequestedBy();
            if (lgu) {
                showBanner("Pending: " + existing.getCenterName() + " • sent by " + by
                        + ". Accept publishes it to residents as written; Decline sends your comment back.");
                editable = false;
                show(R.id.advisory_accept, true);
                show(R.id.advisory_decline, true);
            } else {
                showBanner(mine ? "Pending: waiting for the LGU. You can still fix it, or withdraw it."
                        : "Pending: written by " + by + " for " + existing.getCenterName() + ".");
                editable = mine;
                ((MaterialButton) findViewById(R.id.advisory_save)).setText(R.string.save_changes);
                ((MaterialButton) findViewById(R.id.advisory_archive)).setText(R.string.withdraw);
                show(R.id.advisory_archive, mine);
            }
        } else if (existing.isDeclined()) {
            showBanner("Declined by the " + existing.getCity().getDisplayName() + " LGU: " + existing.getLguNote()
                    + (lgu ? "" : "\nA declined advisory cannot be published. Write a new one instead."));
            editable = false;
            show(R.id.advisory_rewrite, mine);
            show(R.id.advisory_archive, lgu);
        } else if (lgu) {
            if (existing.isAboutCenter()) {
                showBanner("From " + existing.getCenterName() + " • approved"
                        + (existing.getRequestedBy().isEmpty() ? "" : " • written by " + existing.getRequestedBy()));
            }
            editable = true;
            show(R.id.advisory_archive, true);
        } else {
            showBanner(existing.isPublished() ? "Published by the LGU. Only the LGU can change a published advisory."
                    : "Only the LGU can change this advisory.");
            editable = false;
        }
        title.setEnabled(editable);
        message.setEnabled(editable);
        published.setVisibility(lgu && editable ? View.VISIBLE : View.GONE);
        show(R.id.advisory_save, editable);
    }

    private void show(int id, boolean visible) {
        findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showBanner(String text) {
        TextView banner = findViewById(R.id.advisory_banner);
        banner.setVisibility(View.VISIBLE);
        banner.setText(text);
    }

    private void save() {
        TextInputLayout titleLayout = findViewById(R.id.advisory_title_layout);
        TextInputLayout messageLayout = findViewById(R.id.advisory_message_layout);
        titleLayout.setError(null);
        messageLayout.setError(null);
        UiCallback<Void> done = UiCallback.of(this, ignored -> {
            showMessage(lgu ? "Advisory saved" : "Sent to the LGU for approval");
            finish();
        }, error -> {
            if (error instanceof ValidationException) {
                ("title".equals(((ValidationException) error).getField()) ? titleLayout : messageLayout)
                        .setError(error.getMessage());
            } else {
                showError(error);
            }
        });
        if (!lgu) {
            container().advisories().submitForApproval(staff, advisory, Dialogs.text(title), Dialogs.text(message), done);
        } else if (advisory == null) {
            if (published.isChecked()) {
                container().advisories().publishNew(staff, Dialogs.text(title), Dialogs.text(message), cityLevel(), done);
            } else {
                showMessage("New advisories are published immediately. Switch on \"Published\" to continue.");
            }
        } else {
            container().advisories().update(staff, advisory, Dialogs.text(title), Dialogs.text(message),
                    published.isChecked(), done);
        }
    }

    /** LGU: publishes the moderator's advisory as written, with the city's alert level. */
    private void accept() {
        if (advisory == null) return;
        Dialogs.confirm(this, getString(R.string.accept), "Publish it to residents of "
                        + staff.getJurisdiction().getDisplayName() + " now, with the city's alert level ("
                        + cityLevel().getLabel() + ")?", getString(R.string.accept_and_publish),
                () -> container().advisories().approve(staff, advisory, cityLevel(), ui(ignored -> {
                    showMessage("Approved and published");
                    finish();
                })));
    }

    /** LGU: declines with a comment the moderator reads. */
    private void decline() {
        if (advisory == null) return;
        Dialogs.input(this, getString(R.string.decline), getString(R.string.decline_comment_hint), "",
                comment -> container().advisories().decline(staff, advisory, comment, ui(ignored -> {
                    showMessage("Declined. The moderator sees your comment.");
                    finish();
                })));
    }

    /** Moderator: a new advisory that starts from the declined text (the declined one stays as it is). */
    private void rewrite() {
        if (advisory == null) return;
        startActivity(new Intent(this, AdvisoryEditorActivity.class).putExtra(EXTRA_COPY_FROM, advisory.getId()));
        finish();
    }

    private void archive() {
        if (advisory == null) return;
        boolean withdraw = !lgu;
        Dialogs.input(this, getString(withdraw ? R.string.withdraw : R.string.archive),
                withdraw ? "Why are you withdrawing it? (kept in the audit trail)" : "Reason for archiving (kept in the audit trail)",
                "", reason -> container().advisories().archive(staff, advisory, reason, ui(ignored -> {
                    showMessage(withdraw ? "Advisory withdrawn" : "Advisory archived");
                    finish();
                })));
    }
}
