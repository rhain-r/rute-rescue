package com.rutarescue.ui.lgu.data;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.rutarescue.R;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.AccessibilityFeature;
import com.rutarescue.model.center.CapacityChange;
import com.rutarescue.model.center.CenterCapacity;
import com.rutarescue.model.center.CenterState;
import com.rutarescue.model.center.CenterStatus;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.user.ModeratorProfile;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.repository.CenterSnapshot;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.CapacityGaugeView;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.UiCallback;
import com.rutarescue.ui.shell.MainShellActivity;
import com.rutarescue.util.Formats;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Phase 1 "Center capacity". The moderator who runs the center (2026-10-22: only that moderator)
 * changes the headcount with quick buttons or an exact number, picks a status (suggested from the
 * headcount, but they may override it) and ticks the facilities. "Save" stores everything with
 * one audit event (CenterService.updateCapacity). Everyone else sees the page read-only; the LGU
 * also gets "Edit center details" (More menu) and "Publish to residents" for a hidden center.
 *
 * The screen edits a draft: CenterCapacity is immutable and checks the 120% limit, so an invalid
 * number never reaches the center. Offline saves are queued by Firestore; the orange banner shows
 * them until they upload.
 */
public class CenterCapacityActivity extends BaseActivity {

    public static final String EXTRA_ID = "center_id";
    /** Set by the center editor right after a new center was saved. */
    public static final String EXTRA_JUST_SAVED = "just_saved";

    private static final int[] STEP_IDS = {R.id.capacity_minus10, R.id.capacity_minus5, R.id.capacity_minus1,
            R.id.capacity_plus1, R.id.capacity_plus5, R.id.capacity_plus10};
    private static final int[] STEP_DELTAS = {-10, -5, -1, 1, 5, 10};

    private StaffProfile staff;
    /** Only the moderator who runs this center edits it; for everyone else the page is read-only. */
    private boolean readOnly;
    private ListenerHandle listener;
    private EvacuationCenter center;
    private boolean pendingSync;
    private int pendingSaves;

    // The draft being edited on screen (saved only with "Save").
    private CenterCapacity draft = CenterCapacity.notSet();
    private CenterStatus status = CenterStatus.NOT_REPORTED;
    private boolean statusChosenByStaff;
    private final EnumSet<AccessibilityFeature> features = EnumSet.noneOf(AccessibilityFeature.class);
    private boolean dirty;
    private String error;

    private final Map<CenterStatus, Chip> statusChips = new EnumMap<>(CenterStatus.class);
    private final Map<AccessibilityFeature, MaterialCheckBox> featureBoxes = new EnumMap<>(AccessibilityFeature.class);
    private EditText exact;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_center_capacity);
        applySystemBarInsets(findViewById(R.id.capacity_root));
        staff = container().session().requireStaff();
        String id = getIntent().getStringExtra(EXTRA_ID);
        // The same check as staff.canManageCenter(center), made before the center has loaded.
        readOnly = !(staff instanceof ModeratorProfile) || !((ModeratorProfile) staff).getCenterId().equals(id);
        exact = findViewById(R.id.capacity_exact);

        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        findViewById(R.id.capacity_more).setOnClickListener(this::showMore);
        findViewById(R.id.capacity_set_total).setOnClickListener(v -> askTotalCapacity());
        findViewById(R.id.capacity_set).setOnClickListener(v -> setExact());
        findViewById(R.id.capacity_save).setOnClickListener(v -> save());
        findViewById(R.id.capacity_show_on_map).setOnClickListener(v -> {
            if (center != null) startActivity(MainShellActivity.staffMapIntent(this, center));
        });
        // Only the LGU has a staff map (moderators see the Centers list instead).
        findViewById(R.id.capacity_show_on_map).setVisibility(staff.canCreateCenters() ? View.VISIBLE : View.GONE);
        exact.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                setExact();
                return true;
            }
            return false;
        });
        for (int i = 0; i < STEP_IDS.length; i++) {
            int delta = STEP_DELTAS[i];
            View button = findViewById(STEP_IDS[i]);
            button.setContentDescription((delta > 0 ? "Add " : "Remove ") + Math.abs(delta));
            button.setOnClickListener(v -> adjust(delta));
        }
        buildStatusChips();
        buildFeatureBoxes();
        findViewById(R.id.capacity_scale).post(this::placeScaleLabels);

        findViewById(R.id.capacity_publish).setOnClickListener(v -> publish());
        findViewById(R.id.capacity_send_back).setOnClickListener(v -> sendBack());
        if (readOnly) hideEditing();

        listener = container().centers().listen(id, UiCallback.of(this, this::onSnapshot, this::showError));
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindSaveButton();
    }

    @Override
    protected void onDestroy() {
        if (listener != null) listener.remove();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ data

    private void onSnapshot(CenterSnapshot snapshot) {
        center = snapshot.getCenter();
        boolean wasPending = pendingSync;
        pendingSync = snapshot.isPendingSync();
        if (wasPending && !pendingSync) pendingSaves = 0; // everything uploaded
        if (!dirty) {
            draft = center.getCapacity();
            status = center.getStatus();
            statusChosenByStaff = status != draft.suggestedStatus() && status != CenterStatus.NOT_REPORTED;
            features.clear();
            features.addAll(center.getFeatures());
            error = null;
        }
        bindAll();
    }

    // ------------------------------------------------------------------ editing

    private void adjust(int delta) {
        try {
            changeDraft(draft.adjustedBy(delta));
        } catch (ValidationException tooMany) {
            error = tooMany.getMessage();
            bindHeadcount();
        }
    }

    private void setExact() {
        String text = Dialogs.text(exact).strip();
        try {
            changeDraft(draft.withOccupancy(text.isEmpty() ? -1 : Integer.parseInt(text)));
        } catch (NumberFormatException notANumber) {
            error = "Enter a whole number of people.";
            bindHeadcount();
        } catch (ValidationException invalid) {
            error = invalid.getMessage();
            bindHeadcount();
        }
    }

    private void changeDraft(CenterCapacity newDraft) {
        draft = newDraft;
        error = null;
        dirty = true;
        if (!statusChosenByStaff) status = draft.suggestedStatus();
        bindAll();
    }

    private void askTotalCapacity() {
        String current = draft.isSet() ? String.valueOf(draft.getTotalPersons()) : "";
        Dialogs.input(this, getString(R.string.set_capacity), "Number of people the center can hold", current, text -> {
            try {
                int total = Integer.parseInt(text.strip());
                if (total <= 0) throw new ValidationException("capacity", "Capacity must be at least 1 person.");
                changeDraft(draft.withTotal(total));
            } catch (NumberFormatException notANumber) {
                showMessage("Enter a whole number of people.");
            } catch (ValidationException invalid) {
                showMessage(invalid.getMessage());
            }
        });
    }

    /** Moderator of the center: set the capacity. LGU: edit the center's details. */
    private void showMore(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        if (!readOnly) menu.getMenu().add(0, 1, 0, R.string.set_capacity);
        if (staff.canCreateCenters()) menu.getMenu().add(0, 2, 1, R.string.edit_center_details);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                askTotalCapacity();
            } else if (center != null) {
                startActivity(new Intent(this, CenterEditorActivity.class)
                        .putExtra(CenterEditorActivity.EXTRA_ID, center.getId()));
            }
            return true;
        });
        menu.show();
    }

    /** Read-only view: no editing controls; the facts stay visible (the LGU keeps "Edit center details"). */
    private void hideEditing() {
        findViewById(R.id.capacity_more).setVisibility(staff.canCreateCenters() ? View.VISIBLE : View.GONE);
        findViewById(R.id.capacity_steps).setVisibility(View.GONE);
        findViewById(R.id.capacity_steps_plus).setVisibility(View.GONE);
        findViewById(R.id.capacity_exact_row).setVisibility(View.GONE);
        findViewById(R.id.capacity_set_total).setVisibility(View.GONE);
        findViewById(R.id.capacity_limit).setVisibility(View.GONE);
        findViewById(R.id.capacity_save).setVisibility(View.GONE);
        for (Chip chip : statusChips.values()) chip.setEnabled(false);
        for (MaterialCheckBox box : featureBoxes.values()) box.setEnabled(false);
    }

    /** LGU: publish a verified center that is still hidden; residents see it at once. */
    private void publish() {
        if (center == null) return;
        Dialogs.confirm(this, getString(R.string.publish_to_residents), "Publish " + center.getName()
                        + " to residents? They will see it in Centers, on the map and in Safe Route.",
                getString(R.string.publish), () -> container().centers().publishByLgu(staff, center, ui(ignored ->
                        showMessage("Published to residents"))));
    }

    /** LGU: return a center a moderator sent before 2026-10-22, with a note saying what to fix. */
    private void sendBack() {
        if (center == null) return;
        Dialogs.input(this, getString(R.string.send_back), "What should the moderator fix?", "",
                note -> container().centers().sendBack(staff, center, note, ui(ignored ->
                        showMessage("Sent back to the moderators"))));
    }

    private void save() {
        if (center == null) return;
        if (error != null) {
            showMessage(error);
            return;
        }
        boolean online = container().connectivity.isOnline();
        container().centers().updateCapacity(staff, center, draft, status, EnumSet.copyOf(features),
                "", UiCallback.of(this, ignored -> {
                    dirty = false;
                    if (!online) pendingSaves++;
                    showMessage(getString(online ? R.string.capacity_saved : R.string.capacity_saved_offline));
                    bindAll();
                }, this::showError));
    }

    // ------------------------------------------------------------------ binding

    private void bindAll() {
        if (center == null) return;
        bindHeader();
        bindHeadcount();
        bindStatus();
        bindFeatures();
        bindAudit();
        bindPending();
        bindSaveButton();
    }

    private void bindHeader() {
        ((TextView) findViewById(R.id.capacity_name)).setText(center.getName());
        String barangay = center.getBarangay().isEmpty() ? "" : center.getBarangay() + " · ";
        ((TextView) findViewById(R.id.capacity_place)).setText(barangay + center.getCity().getDisplayName());
        TextView location = findViewById(R.id.capacity_location_chip);
        boolean verified = center.isLocationVerified();
        location.setText(verified ? R.string.location_verified : R.string.location_not_verified);
        location.setCompoundDrawablesRelativeWithIntrinsicBounds(verified ? R.drawable.ic_status_check : 0, 0, 0, 0);
        tint(location, verified ? R.color.rr_green_tint : R.color.rr_orange_soft, verified ? R.color.rr_green : R.color.rr_orange);
        CenterState state = CenterState.of(center);
        Badges.centerState(findViewById(R.id.capacity_state), state);
        String explanation = state.getExplanation();
        if (state == CenterState.SENT_BACK) explanation += "\nLGU note: " + center.getLguNote();
        if (readOnly) {
            explanation = "Address: " + center.getAddress() + "\n" + explanation
                    + (center.getNotes().isEmpty() ? "" : "\nNotes: " + center.getNotes());
            // LGU: publish a verified hidden center; "Send back" only for one a moderator sent earlier.
            boolean lgu = staff.canPublishToResidents() && staff.canManage(center.getCity());
            boolean publishable = lgu && !center.isPublished() && !center.isArchived() && center.getVerification().isVerified();
            boolean waiting = lgu && state == CenterState.WAITING_FOR_LGU;
            findViewById(R.id.capacity_publish).setVisibility(publishable ? View.VISIBLE : View.GONE);
            findViewById(R.id.capacity_send_back).setVisibility(waiting ? View.VISIBLE : View.GONE);
            findViewById(R.id.capacity_savebar).setVisibility(publishable || waiting ? View.VISIBLE : View.GONE);
            if (!staff.canCreateCenters()) {
                explanation += "\nOnly its moderator updates this center. You can see it to send people where there is room.";
            }
        }
        if (center.getVerification().isVerified()) {
            explanation += "\nVerified against: " + center.getVerification().getSource();
        }
        if (getIntent().getBooleanExtra(EXTRA_JUST_SAVED, false)) {
            explanation = "Saved. You can find it again under Data > Evacuation centers, and on the Map tab. "
                    + "Assign its moderator under Finalize > Moderators.\n" + explanation;
        }
        ((TextView) findViewById(R.id.capacity_state_text)).setText(explanation);
        TextView landmark = findViewById(R.id.capacity_landmark_chip);
        landmark.setVisibility(center.getLandmark().isEmpty() ? View.GONE : View.VISIBLE);
        landmark.setText(getString(R.string.landmark_chip, center.getLandmark()));
        tint(landmark, R.color.rr_surface_secondary, R.color.rr_text_secondary);
    }

    private void bindHeadcount() {
        boolean set = draft.isSet();
        findViewById(R.id.capacity_unset).setVisibility(set ? View.GONE : View.VISIBLE);
        findViewById(R.id.capacity_counts).setVisibility(set ? View.VISIBLE : View.GONE);
        TextView levelChip = findViewById(R.id.capacity_level_chip);
        levelChip.setVisibility(set ? View.VISIBLE : View.GONE);
        if (!set) return;

        @ColorRes int strong;
        @ColorRes int soft;
        switch (draft.getLevel()) {
            case RED: strong = R.color.rr_red; soft = R.color.rr_red_soft; break;
            case AMBER: strong = R.color.rr_orange; soft = R.color.rr_orange_soft; break;
            default: strong = R.color.rr_green; soft = R.color.rr_green_tint;
        }
        levelChip.setText(status.getLabel());
        tint(levelChip, soft, strong);

        TextView count = findViewById(R.id.capacity_count);
        count.setText(String.valueOf(draft.getCurrentOccupancy()));
        count.setTextColor(ContextCompat.getColor(this, draft.getLevel() == com.rutarescue.model.center.OccupancyLevel.RED
                ? R.color.rr_red : R.color.rr_text));
        ((TextView) findViewById(R.id.capacity_of)).setText(getString(R.string.headcount_of,
                draft.getTotalPersons(), draft.getPercent()));
        ((CapacityGaugeView) findViewById(R.id.capacity_gauge)).setLevel(draft.getFraction(), draft.getLevel());
        for (int i = 0; i < STEP_IDS.length; i++) {
            int next = draft.getCurrentOccupancy() + STEP_DELTAS[i];
            View button = findViewById(STEP_IDS[i]);
            boolean allowed = STEP_DELTAS[i] < 0 ? draft.getCurrentOccupancy() > 0 : next <= draft.getLimit();
            button.setEnabled(allowed);
            button.setAlpha(allowed ? 1f : 0.4f);
        }
        if (!exact.hasFocus() || error == null) exact.setText(String.valueOf(draft.getCurrentOccupancy()));
        if (readOnly) return;

        TextView limit = findViewById(R.id.capacity_limit);
        View field = findViewById(R.id.capacity_field);
        TextView fieldLabel = findViewById(R.id.capacity_field_label);
        if (error != null) {
            limit.setText(error);
            limit.setTextColor(ContextCompat.getColor(this, R.color.rr_red));
            field.setBackgroundResource(R.drawable.bg_field_error);
            fieldLabel.setTextColor(ContextCompat.getColor(this, R.color.rr_red));
        } else {
            limit.setText(getString(R.string.capacity_limit, draft.getLimit()));
            limit.setTextColor(ContextCompat.getColor(this, R.color.rr_text_secondary));
            field.setBackgroundResource(R.drawable.bg_field);
            fieldLabel.setTextColor(ContextCompat.getColor(this, R.color.rr_text_secondary));
        }
        findViewById(R.id.capacity_set).setEnabled(error == null || exact.hasFocus());
    }

    private void buildStatusChips() {
        ChipGroup group = findViewById(R.id.capacity_status);
        for (CenterStatus choice : CenterStatus.CHOICES) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_status_chip, group, false);
            chip.setText(choice.getLabel());
            chip.setOnClickListener(v -> {
                status = choice;
                statusChosenByStaff = choice != draft.suggestedStatus();
                dirty = true;
                bindAll();
            });
            group.addView(chip);
            statusChips.put(choice, chip);
        }
    }

    private void bindStatus() {
        for (Map.Entry<CenterStatus, Chip> entry : statusChips.entrySet()) {
            entry.getValue().setChecked(entry.getKey() == status);
        }
        TextView note = findViewById(R.id.capacity_status_note);
        CenterStatus suggested = draft.suggestedStatus();
        if (!draft.isSet()) {
            note.setText("");
        } else if (status == suggested) {
            note.setText(R.string.status_suggested);
        } else {
            note.setText(getString(R.string.status_overridden, suggested.getLabel()));
        }
    }

    private void buildFeatureBoxes() {
        GridLayout grid = findViewById(R.id.capacity_facilities);
        for (AccessibilityFeature feature : AccessibilityFeature.values()) {
            MaterialCheckBox box = (MaterialCheckBox) getLayoutInflater().inflate(R.layout.item_facility_checkbox, grid, false);
            box.setText(feature.getLabel());
            box.setOnCheckedChangeListener((button, checked) -> {
                if (checked == features.contains(feature)) return;
                if (checked) features.add(feature); else features.remove(feature);
                dirty = true;
                bindSaveButton();
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            params.width = 0;
            grid.addView(box, params);
            featureBoxes.put(feature, box);
        }
    }

    private void bindFeatures() {
        for (Map.Entry<AccessibilityFeature, MaterialCheckBox> entry : featureBoxes.entrySet()) {
            entry.getValue().setChecked(features.contains(entry.getKey()));
        }
    }

    private void bindAudit() {
        TextView audit = findViewById(R.id.capacity_audit);
        CapacityChange change = center.getLastCapacityChange();
        if (change == null) {
            audit.setText(center.getCapacity().isSet() ? R.string.no_capacity_change_yet : R.string.no_capacity_change);
            return;
        }
        String tail = pendingSync ? "waiting to upload"
                : change.getSource().isEmpty() ? Formats.relative(change.getChangedAt(), System.currentTimeMillis())
                : "source: " + change.getSource();
        audit.setText(android.text.Html.fromHtml("<b>" + android.text.TextUtils.htmlEncode(change.describe()) + "</b> at "
                + Formats.clock(change.getChangedAt()) + " · " + android.text.TextUtils.htmlEncode(tail),
                android.text.Html.FROM_HTML_MODE_LEGACY));
    }

    private void bindPending() {
        View banner = findViewById(R.id.capacity_pending);
        banner.setVisibility(pendingSync ? View.VISIBLE : View.GONE);
        int count = Math.max(1, pendingSaves);
        ((TextView) findViewById(R.id.capacity_pending_text)).setText(getString(R.string.pending_sync,
                count == 1 ? "1 change" : count + " changes"));
    }

    private void bindSaveButton() {
        TextView save = findViewById(R.id.capacity_save);
        save.setText(container().connectivity.isOnline() ? R.string.save_changes : R.string.save_on_phone);
    }

    /** Puts "70%" and "90%" centred under their marks on the gauge. */
    private void placeScaleLabels() {
        FrameLayout scale = findViewById(R.id.capacity_scale);
        place(scale, findViewById(R.id.capacity_scale_70), 0.70);
        place(scale, findViewById(R.id.capacity_scale_90), 0.90);
    }

    private static void place(FrameLayout scale, TextView label, double fraction) {
        label.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        label.setTranslationX((float) (scale.getWidth() * fraction - label.getMeasuredWidth() / 2.0));
    }

    private void tint(TextView chip, @ColorRes int background, @ColorRes int foreground) {
        chip.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, background)));
        chip.setTextColor(ContextCompat.getColor(this, foreground));
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(chip,
                ColorStateList.valueOf(ContextCompat.getColor(this, foreground)));
    }
}
