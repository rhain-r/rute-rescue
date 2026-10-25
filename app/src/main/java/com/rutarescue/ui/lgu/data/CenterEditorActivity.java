package com.rutarescue.ui.lgu.data;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rutarescue.R;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.AccessibilityFeature;
import com.rutarescue.model.center.CenterStatus;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.center.VerificationInfo;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.repository.Callback;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.UiCallback;
import com.rutarescue.util.Formats;

import java.util.EnumMap;
import java.util.Locale;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The LGU creates, edits, verifies, publishes or archives an evacuation center of its own city
 * (since 2026-10-22 moderators no longer add centers). "Verify and publish" names the source the
 * officer checked and shows the center to residents at once. The headcount is entered on the
 * capacity screen by the moderator who runs the center.
 */
public class CenterEditorActivity extends BaseActivity {

    public static final String EXTRA_ID = "center_id";

    private final Map<String, TextInputLayout> layouts = new HashMap<>();
    private final Map<AccessibilityFeature, MaterialCheckBox> featureBoxes = new EnumMap<>(AccessibilityFeature.class);
    private StaffProfile staff;
    private EvacuationCenter center;
    private CenterStatus status = CenterStatus.NOT_REPORTED;
    private TextInputEditText name, address, lat, lon, notes, source;
    private MaterialAutoCompleteTextView statusField;
    /** "Pick on map": the picked point fills the latitude and longitude fields. */
    private final ActivityResultLauncher<Intent> picker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                GeoPoint point = result.getResultCode() == Activity.RESULT_OK
                        ? LocationPickerActivity.result(result.getData()) : null;
                if (point == null) return;
                lat.setText(String.format(Locale.US, "%.6f", point.getLatitude()));
                lon.setText(String.format(Locale.US, "%.6f", point.getLongitude()));
                layouts.get("location").setError(null);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_center_editor);
        applySystemBarInsets(findViewById(R.id.editor_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.center_editor);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        staff = container().session().requireStaff();
        ((TextView) findViewById(R.id.editor_city)).setText(staff.getJurisdiction().getDisplayName() + " LGU");

        name = findViewById(R.id.editor_name);
        address = findViewById(R.id.editor_address);
        lat = findViewById(R.id.editor_lat);
        lon = findViewById(R.id.editor_lon);
        notes = findViewById(R.id.editor_notes);
        source = findViewById(R.id.editor_source);
        statusField = findViewById(R.id.editor_status);
        layouts.put("centerName", findViewById(R.id.editor_name_layout));
        layouts.put("address", findViewById(R.id.editor_address_layout));
        layouts.put("location", findViewById(R.id.editor_lat_layout));
        layouts.put("notes", findViewById(R.id.editor_notes_layout));
        layouts.put("verificationSource", findViewById(R.id.editor_source_layout));

        String[] statusLabels = new String[CenterStatus.values().length];
        for (int i = 0; i < statusLabels.length; i++) statusLabels[i] = CenterStatus.values()[i].getLabel();
        statusField.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, statusLabels));
        statusField.setText(status.getLabel(), false);
        statusField.setOnItemClickListener((parent, view, position, id) -> status = CenterStatus.values()[position]);

        LinearLayout features = findViewById(R.id.editor_features);
        for (AccessibilityFeature feature : AccessibilityFeature.values()) {
            MaterialCheckBox box = new MaterialCheckBox(this);
            box.setText(feature.getLabel());
            features.addView(box);
            featureBoxes.put(feature, box);
        }
        updateVerificationText();

        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id != null) container().centers().get(id, ui(this::fill));

        findViewById(R.id.editor_pick_on_map).setOnClickListener(v -> picker.launch(
                LocationPickerActivity.intent(this, typedLocationOr(staff.getJurisdiction().getHallLocation()))));
        findViewById(R.id.editor_save).setOnClickListener(v -> save(false));
        findViewById(R.id.editor_publish).setOnClickListener(v -> save(true));
        findViewById(R.id.editor_archive).setOnClickListener(v -> archive());
    }

    private void fill(EvacuationCenter existing) {
        center = existing;
        name.setText(existing.getName());
        address.setText(existing.getAddress());
        lat.setText(String.valueOf(existing.getLocation().getLatitude()));
        lon.setText(String.valueOf(existing.getLocation().getLongitude()));
        notes.setText(existing.getNotes());
        status = existing.getStatus();
        statusField.setText(status.getLabel(), false);
        for (Map.Entry<AccessibilityFeature, MaterialCheckBox> entry : featureBoxes.entrySet()) {
            entry.getValue().setChecked(existing.getFeatures().contains(entry.getKey()));
        }
        if (existing.getVerification().isVerified()) source.setText(existing.getVerification().getSource());
        findViewById(R.id.editor_archive).setVisibility(View.VISIBLE);
        updateVerificationText();
    }

    private void updateVerificationText() {
        TextView text = findViewById(R.id.editor_verification);
        if (center == null) {
            text.setText("New center. Residents see it once you verify it and publish it. Then assign a moderator "
                    + "to it under Finalize > Moderators.");
        } else if (!center.getLguNote().isEmpty() && !center.isPublished()) {
            text.setText("Sent back earlier: " + center.getLguNote() + "\nFix it, then verify and publish it.");
        } else if (center.isAwaitingLgu()) {
            text.setText("Verified by a moderator and waiting to be published. Source: " + center.getVerification().getSource());
        } else if (center.getVerification().isVerified()) {
            text.setText("Verified: " + center.getVerification().getSource() + " • "
                    + Formats.dateTime(center.getVerification().getVerifiedAt())
                    + (center.isPublished() ? "\nPublished to residents." : "\nNot published."));
        } else {
            text.setText("Not verified yet. Source: " + center.getVerification().getSource());
        }
    }

    private void save(boolean verifyAndPublish) {
        for (TextInputLayout layout : layouts.values()) layout.setError(null);
        GeoPoint location;
        try {
            location = new GeoPoint(Double.parseDouble(Dialogs.text(lat).strip()), Double.parseDouble(Dialogs.text(lon).strip()));
        } catch (IllegalArgumentException badNumber) {
            layouts.get("location").setError("Enter decimal coordinates, e.g. 14.6042 and 120.9822");
            return;
        }
        Set<AccessibilityFeature> features = EnumSet.noneOf(AccessibilityFeature.class);
        for (Map.Entry<AccessibilityFeature, MaterialCheckBox> entry : featureBoxes.entrySet()) {
            if (entry.getValue().isChecked()) features.add(entry.getKey());
        }
        long now = System.currentTimeMillis();
        try {
            if (center == null) {
                center = new EvacuationCenter(null, Dialogs.text(name), Dialogs.text(address), staff.getJurisdiction(),
                        location, status, features, VerificationInfo.unverified("Entered by " + staff.getRoleLabel()),
                        false, Dialogs.text(notes));
            } else {
                center.update(Dialogs.text(name), Dialogs.text(address), staff.getJurisdiction(), location, status, features,
                        Dialogs.text(notes), staff.getUid(), now);
            }
        } catch (ValidationException invalid) {
            TextInputLayout layout = layouts.get(invalid.getField());
            if (layout != null) layout.setError(invalid.getMessage()); else showError(invalid);
            return;
        }
        boolean isNew = center.getId() == null;
        Callback<Void> done = UiCallback.of(this, ignored -> {
            showMessage(!verifyAndPublish ? "Center saved (not published)" : "Center verified and published");
            // A new center opens its own page (state, headcount, "Show on map"), so it is easy to find.
            if (isNew) {
                startActivity(new Intent(this, CenterCapacityActivity.class)
                        .putExtra(CenterCapacityActivity.EXTRA_ID, center.getId())
                        .putExtra(CenterCapacityActivity.EXTRA_JUST_SAVED, true));
            }
            finish();
        }, error -> {
            if (error instanceof ValidationException && layouts.containsKey(((ValidationException) error).getField())) {
                layouts.get(((ValidationException) error).getField()).setError(error.getMessage());
            } else {
                showError(error);
            }
        });
        if (verifyAndPublish) {
            container().centers().verifyAndPublish(staff, center, Dialogs.text(source), done);
        } else {
            container().centers().save(staff, center, "SAVE", "Saved center details", done);
        }
    }

    /** The coordinates typed in the form, or the fallback when they are empty or not numbers. */
    private GeoPoint typedLocationOr(GeoPoint fallback) {
        try {
            GeoPoint typed = new GeoPoint(Double.parseDouble(Dialogs.text(lat).strip()),
                    Double.parseDouble(Dialogs.text(lon).strip()));
            return typed.isInsideMetroManilaRegion() ? typed : fallback;
        } catch (IllegalArgumentException notTyped) {
            return fallback;
        }
    }

    private void archive() {
        if (center == null) return;
        Dialogs.input(this, getString(R.string.archive), "Reason (kept in the audit trail)", "", reason ->
                container().centers().archive(staff, center, reason.isBlank() ? "Archived by the LGU" : reason, ui(ignored -> {
                    showMessage("Center archived");
                    finish();
                })));
    }
}
