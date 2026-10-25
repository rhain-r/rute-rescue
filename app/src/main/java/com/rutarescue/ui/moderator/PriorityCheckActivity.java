package com.rutarescue.ui.moderator;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import com.rutarescue.R;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.priority.CheckResult;
import com.rutarescue.model.priority.ChecklistItem;
import com.rutarescue.model.priority.PriorityCard;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.UiCallback;
import com.rutarescue.ui.lgu.reviews.ReviewDetailActivity;
import com.rutarescue.util.Formats;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Moderator: the priority ID a resident showed at the evacuation center (2026-10-22). Opens the
 * record by the scanned (or typed) code, shows the Ligtas application of the moderator's city, and
 * lets the moderator mark each checklist item OK or Missing with a note the resident reads.
 */
public class PriorityCheckActivity extends BaseActivity {

    private static final String EXTRA_CODE = "priority_code";

    private StaffProfile staff;
    private PriorityCard card;
    private ListenerHandle ligtasListener = ListenerHandle.NONE;
    /** What the moderator has marked on screen (saved only with "Save check"). */
    private final Map<String, CheckResult> marks = new HashMap<>();

    public static Intent intent(Context context, String scannedOrTyped) {
        return new Intent(context, PriorityCheckActivity.class).putExtra(EXTRA_CODE, scannedOrTyped);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_priority_check);
        applySystemBarInsets(findViewById(R.id.check_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.priority_id_check);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        staff = container().session().requireStaff();
        findViewById(R.id.check_save).setOnClickListener(v -> save());
        findViewById(R.id.check_save).setEnabled(false);

        container().priorityIds().open(getIntent().getStringExtra(EXTRA_CODE), UiCallback.of(this, this::show, error -> {
            showError(error);
            finish();
        }));
    }

    @Override
    protected void onDestroy() {
        ligtasListener.remove();
        super.onDestroy();
    }

    private void show(PriorityCard loaded) {
        card = loaded;
        ((TextView) findViewById(R.id.check_holder)).setText(card.getHolderName());
        ((TextView) findViewById(R.id.check_details)).setText(card.getGroup().getLabel() + " • "
                + (card.getCity() == null ? "" : card.getCity().getDisplayName() + " • ") + "ID " + card.getDisplayCode()
                + "\nIssued " + Formats.dateTime(card.getIssuedAt()));
        bindStatus();

        marks.clear();
        LinearLayout list = findViewById(R.id.check_items);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ChecklistItem item : card.getChecklist()) {
            View row = inflater.inflate(R.layout.item_check_row, list, false);
            ((TextView) row.findViewById(R.id.check_kind)).setText(item.getKind().getLabel());
            ((TextView) row.findViewById(R.id.check_label)).setText(item.getLabel());
            Chip ok = row.findViewById(R.id.check_ok);
            Chip missing = row.findViewById(R.id.check_missing);
            CheckResult before = card.resultOf(item);
            marks.put(item.getKey(), before);
            ok.setChecked(before == CheckResult.OK);
            missing.setChecked(before == CheckResult.MISSING);
            ok.setOnCheckedChangeListener((chip, checked) -> mark(item, checked ? CheckResult.OK : null, ok, missing));
            missing.setOnCheckedChangeListener((chip, checked) -> mark(item, checked ? CheckResult.MISSING : null, ok, missing));
            list.addView(row);
        }
        ((TextInputEditText) findViewById(R.id.check_note)).setText(card.getFlagNote());
        findViewById(R.id.check_save).setEnabled(true);

        // The resident's Ligtas application, when it belongs to this moderator's city (the rules allow no other).
        bindLigtas(null);
        ligtasListener = container().ligtas().listenForReviewer(staff, ui(this::onApplications));
    }

    /** Keeps the on-screen marks; unticking a chip leaves the item "not checked". */
    private void mark(ChecklistItem item, CheckResult result, Chip ok, Chip missing) {
        if (result != null) {
            marks.put(item.getKey(), result);
        } else if (!ok.isChecked() && !missing.isChecked()) {
            marks.put(item.getKey(), CheckResult.NOT_CHECKED);
        }
    }

    private void bindStatus() {
        TextView status = findViewById(R.id.check_status);
        boolean complete = card.isChecked() && card.getMissingItems().isEmpty();
        Badges.label(status, card.getStatusLabel(), !card.isChecked() ? Badges.Tone.TEAL
                : complete ? Badges.Tone.GREEN : Badges.Tone.ORANGE);
        ((TextView) findViewById(R.id.check_last)).setText(card.isChecked()
                ? "Last checked by " + card.getCheckedByName()
                + (card.getCheckedAtCenter().isEmpty() ? "" : " at " + card.getCheckedAtCenter())
                + " • " + Formats.dateTime(card.getCheckedAt())
                : "First check: go through every item below with the resident.");
    }

    private void onApplications(SourcedData<List<LigtasApplication>> data) {
        LigtasApplication newest = null;
        for (LigtasApplication application : data.getData()) {
            if (!application.getOwnerUid().equals(card.getOwnerUid())) continue;
            if (newest == null || application.getSubmittedAt() > newest.getSubmittedAt()) newest = application;
        }
        bindLigtas(newest);
    }

    private void bindLigtas(LigtasApplication application) {
        TextView text = findViewById(R.id.check_ligtas_text);
        View row = findViewById(R.id.check_ligtas);
        View chevron = findViewById(R.id.check_ligtas_chevron);
        if (application == null) {
            boolean sameCity = card.getCity() == staff.getJurisdiction();
            text.setText(sameCity ? "No Ligtas Priority application from this resident yet."
                    : "Registered in " + (card.getCity() == null ? "another city" : card.getCity().getDisplayName())
                    + ": only that city's staff see the Ligtas application.");
            chevron.setVisibility(View.GONE);
            row.setOnClickListener(null);
            return;
        }
        text.setText(application.getStatus().getLabel() + " • " + application.getMemberName() + " • "
                + application.getCategory().getLabel());
        chevron.setVisibility(View.VISIBLE);
        row.setOnClickListener(v -> startActivity(ReviewDetailActivity.intent(this, application.getId(), true)));
    }

    private void save() {
        if (card == null) return;
        container().priorityIds().recordCheck(staff, card, new HashMap<>(marks),
                Dialogs.text(findViewById(R.id.check_note)), UiCallback.of(this, ignored -> {
                    showMessage(card.getMissingItems().isEmpty() ? "Check saved: complete"
                            : "Check saved: " + card.getStatusLabel());
                    bindStatus();
                }, error -> {
                    if (error instanceof ValidationException) showMessage(error.getMessage());
                    else showError(error);
                }));
    }
}
