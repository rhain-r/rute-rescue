package com.rutarescue.ui.resident.centers;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.rutarescue.R;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.AccessibilityFeature;
import com.rutarescue.model.center.CapacityChange;
import com.rutarescue.model.center.CenterCapacity;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.DataFreshness;
import com.rutarescue.model.place.SavedPlace;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.service.CenterService;
import com.rutarescue.ui.common.Badges;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.CapacityGaugeView;
import com.rutarescue.ui.common.UiCallback;
import com.rutarescue.ui.shell.MainShellActivity;
import com.rutarescue.util.Formats;

/** Center details: status, posted headcount, accessibility, verification source + timestamp, route button. */
public class CenterDetailActivity extends BaseActivity {

    private static final String EXTRA_ID = "center_id";

    private EvacuationCenter center;

    public static Intent intent(Context context, String centerId) {
        return new Intent(context, CenterDetailActivity.class).putExtra(EXTRA_ID, centerId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_center_detail);
        applySystemBarInsets(findViewById(R.id.detail_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText("Evacuation center");
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());

        container().centers().get(getIntent().getStringExtra(EXTRA_ID), ui(this::bind));
        findViewById(R.id.detail_route).setOnClickListener(v -> {
            if (center != null) startActivity(MainShellActivity.routeIntent(this, center));
        });
        findViewById(R.id.detail_save).setOnClickListener(v -> save());
    }

    private void bind(EvacuationCenter loaded) {
        center = loaded;
        ((TextView) findViewById(R.id.detail_name)).setText(center.getName());
        Badges.center(findViewById(R.id.detail_status), center.getStatus());
        ((TextView) findViewById(R.id.detail_address)).setText(center.getAddress() + "\n" + center.getCity().getDisplayName());

        TextView distance = findViewById(R.id.detail_distance);
        if (container().location.hasPermission()) {
            container().location.getCurrent(UiCallback.of(this, origin -> distance.setText(
                    Formats.distance(CenterService.distance(center, origin)) + " from your location (straight line)"),
                    ignored -> distance.setText("")));
        }

        ChipGroup features = findViewById(R.id.detail_features);
        features.removeAllViews();
        for (AccessibilityFeature feature : center.getFeatures()) {
            Chip chip = new Chip(this);
            chip.setText(feature.getLabel());
            chip.setCheckable(false);
            features.addView(chip);
        }
        findViewById(R.id.detail_no_features).setVisibility(center.getFeatures().isEmpty() ? View.VISIBLE : View.GONE);
        bindCapacity();

        if (CenterService.isBundled(center)) {
            // Bundled PLM SSC list: always shown, but nobody has confirmed it yet.
            ((TextView) findViewById(R.id.detail_verification)).setText(getString(R.string.bundled_center_note,
                    center.getLandmark().isEmpty() ? center.getAddress() : center.getLandmark()));
            Badges.freshness(findViewById(R.id.detail_freshness), DataFreshness.PREPARED, 0,
                    CenterService.BUNDLED_SOURCE);
            return;
        }
        String verification = center.getVerification().isVerified()
                ? "Verified by the LGU against: " + center.getVerification().getSource() + "\nVerified "
                + Formats.dateTime(center.getVerification().getVerifiedAt())
                : "Not yet verified by the LGU. Source: " + center.getVerification().getSource();
        if (!center.getNotes().isEmpty()) verification += "\n\nLGU notes: " + center.getNotes();
        ((TextView) findViewById(R.id.detail_verification)).setText(verification);
        long updated = center.getAuditInfo() == null ? 0 : center.getAuditInfo().getUpdatedAt();
        Badges.freshness(findViewById(R.id.detail_freshness), DataFreshness.CACHED, updated, "");
        ((TextView) findViewById(R.id.detail_freshness)).setText("LGU record updated " + Formats.relative(updated, System.currentTimeMillis()));
    }

    /** The headcount the LGU posted, with who posted it and when; a plain note when there is none. */
    private void bindCapacity() {
        CenterCapacity capacity = center.getCapacity();
        TextView count = findViewById(R.id.detail_capacity_count);
        count.setText(capacity.summary());
        count.setTextAppearance(capacity.isSet() ? R.style.TextAppearance_RR_Title : R.style.TextAppearance_RR_Label);
        CapacityGaugeView gauge = findViewById(R.id.detail_capacity_gauge);
        gauge.setVisibility(capacity.isSet() ? View.VISIBLE : View.GONE);
        gauge.setLevel(capacity.getFraction(), capacity.getLevel());

        String note;
        CapacityChange change = center.getLastCapacityChange();
        if (capacity.isSet() && change != null) {
            note = getString(R.string.capacity_posted_by, change.getActorName(),
                    Formats.relative(change.getChangedAt(), System.currentTimeMillis()));
        } else if (capacity.isSet()) {
            note = getString(R.string.capacity_posted);
        } else if (CenterService.isBundled(center)) {
            note = getString(R.string.capacity_none_bundled);
        } else {
            note = getString(R.string.capacity_none);
        }
        ((TextView) findViewById(R.id.detail_capacity_note)).setText(note);
    }

    private void save() {
        UserProfile profile = container().session().getProfile();
        if (center == null || profile == null) return;
        try {
            SavedPlace place = new SavedPlace(null, center.getName(), center.getLocation(), System.currentTimeMillis());
            container().savedPlaces().add(profile.getUid(), place, ui(ignored -> showMessage("Saved to your places")));
        } catch (ValidationException e) {
            showError(e);
        }
    }
}
