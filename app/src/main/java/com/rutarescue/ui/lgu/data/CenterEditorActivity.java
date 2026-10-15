package com.rutarescue.ui.lgu.data;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.repository.Callback;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.UiCallback;
import com.rutarescue.util.Formats;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Create, edit, verify+publish or archive an evacuation center of the officer's city.
 * Capacity is deliberately not editable: there is no legitimate live source for it.
 */
public class CenterEditorActivity extends BaseActivity {

    public static final String EXTRA_ID = "center_id";

    private final Map<String, TextInputLayout> layouts = new HashMap<>();
    private final Map<AccessibilityFeature, MaterialCheckBox> featureBoxes = new EnumMap<>(AccessibilityFeature.class);
    private LguProfile lgu;
    private EvacuationCenter center;
    private CenterStatus status = CenterStatus.NOT_REPORTED;
    private TextInputEditText name, address, lat, lon, notes, source;
    private MaterialAutoCompleteTextView statusField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_center_editor);
        applySystemBarInsets(findViewById(R.id.editor_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.center_editor);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        lgu = container().session().requireLgu();
        ((TextView) findViewById(R.id.editor_city)).setText(lgu.getJurisdiction().getDisplayName() + " LGU");

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
            text.setText("New center. It stays hidden from residents until you verify and publish it.");
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
                center = new EvacuationCenter(null, Dialogs.text(name), Dialogs.text(address), lgu.getJurisdiction(),
                        location, status, features, VerificationInfo.unverified("Entered by " + lgu.getRoleLabel()),
                        false, Dialogs.text(notes));
            } else {
                center.update(Dialogs.text(name), Dialogs.text(address), lgu.getJurisdiction(), location, status, features,
                        Dialogs.text(notes), lgu.getUid(), now);
            }
        } catch (ValidationException invalid) {
            TextInputLayout layout = layouts.get(invalid.getField());
            if (layout != null) layout.setError(invalid.getMessage()); else showError(invalid);
            return;
        }
        Callback<Void> done = UiCallback.of(this, ignored -> {
            showMessage(verifyAndPublish ? "Center verified and published" : "Center saved");
            finish();
        }, error -> {
            if (error instanceof ValidationException && layouts.containsKey(((ValidationException) error).getField())) {
                layouts.get(((ValidationException) error).getField()).setError(error.getMessage());
            } else {
                showError(error);
            }
        });
        if (verifyAndPublish) {
            container().centers().verifyAndPublish(lgu, center, Dialogs.text(source), done);
        } else {
            container().centers().save(lgu, center, "SAVE", "Saved center details", done);
        }
    }

    private void archive() {
        if (center == null) return;
        Dialogs.input(this, getString(R.string.archive), "Reason (kept in the audit trail)", "", reason ->
                container().centers().archive(lgu, center, reason.isBlank() ? "Archived by LGU" : reason, ui(ignored -> {
                    showMessage("Center archived");
                    finish();
                })));
    }
}
