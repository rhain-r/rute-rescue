package com.rutarescue.model.center;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.AuditableEntity;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.Locatable;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.validation.TextRules;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * An evacuation center managed by an LGU.
 *
 * Inheritance: it is an auditable record (AuditableEntity).
 * Polymorphism: it is also Locatable, so it can be passed to the Safe Route screen.
 * Capacity is intentionally NOT modelled: there is no legitimate live source for it.
 */
public class EvacuationCenter extends AuditableEntity implements Locatable {

    private String name;
    private String address;
    private MetroManilaCity city;
    private GeoPoint location;
    private CenterStatus status;
    private final EnumSet<AccessibilityFeature> features = EnumSet.noneOf(AccessibilityFeature.class);
    private VerificationInfo verification;
    private boolean published;
    private String notes;

    public EvacuationCenter(String id, String name, String address, MetroManilaCity city, GeoPoint location,
                            CenterStatus status, Set<AccessibilityFeature> features,
                            VerificationInfo verification, boolean published, String notes)
            throws ValidationException {
        super(id, null, false);
        applyValidated(name, address, city, location, status, features, notes);
        this.verification = verification == null ? VerificationInfo.unverified("Not recorded") : verification;
        this.published = published && this.verification.isVerified();
    }

    @Override
    public String getEntityType() {
        return "center";
    }

    /** Validated edit: nothing changes unless every value is valid. */
    public void update(String newName, String newAddress, MetroManilaCity newCity, GeoPoint newLocation,
                       CenterStatus newStatus, Set<AccessibilityFeature> newFeatures, String newNotes,
                       String actorUid, long now) throws ValidationException {
        applyValidated(newName, newAddress, newCity, newLocation, newStatus, newFeatures, newNotes);
        markUpdated(actorUid, now);
    }

    private void applyValidated(String newName, String newAddress, MetroManilaCity newCity, GeoPoint newLocation,
                                CenterStatus newStatus, Set<AccessibilityFeature> newFeatures, String newNotes)
            throws ValidationException {
        String checkedName = TextRules.requiredText("centerName", newName);
        TextRules.checkLength("centerName", checkedName, 3, 120);
        String checkedAddress = TextRules.requiredText("address", newAddress);
        String checkedNotes = TextRules.optionalText("notes", newNotes);
        if (newCity == null) {
            throw new ValidationException("city", "Choose the city that manages this center.");
        }
        if (newLocation == null || !newLocation.isInsideMetroManilaRegion()) {
            throw new ValidationException("location", "The center location must be inside Metro Manila.");
        }
        name = checkedName;
        address = checkedAddress;
        city = newCity;
        location = newLocation;
        status = newStatus == null ? CenterStatus.NOT_REPORTED : newStatus;
        features.clear();
        if (newFeatures != null) features.addAll(newFeatures);
        notes = checkedNotes;
    }

    /** Records that LGU staff checked this center against a named source. */
    public void verify(String source, String actorUid, long now) throws ValidationException {
        String checkedSource = TextRules.requiredText("verificationSource", source);
        verification = VerificationInfo.verified(checkedSource, actorUid, now);
        markUpdated(actorUid, now);
    }

    /** Residents only see published centers, and only verified centers can be published. */
    public void setPublished(boolean shouldPublish, String actorUid, long now) {
        if (shouldPublish && !verification.isVerified()) {
            throw new IllegalStateException("Verify the center before publishing it to residents.");
        }
        published = shouldPublish;
        markUpdated(actorUid, now);
    }

    @Override
    public GeoPoint getLocation() {
        return location;
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    @Override
    public String getSubtitle() {
        return status.getLabel() + " • " + address;
    }

    public String getName() { return name; }
    public String getAddress() { return address; }
    public MetroManilaCity getCity() { return city; }
    public CenterStatus getStatus() { return status; }
    public Set<AccessibilityFeature> getFeatures() { return Collections.unmodifiableSet(features); }
    public boolean hasAccessibilityFeature() { return !features.isEmpty(); }
    public VerificationInfo getVerification() { return verification; }
    public boolean isPublished() { return published; }
    public String getNotes() { return notes; }
}
