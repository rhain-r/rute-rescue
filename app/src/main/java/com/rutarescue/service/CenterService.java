package com.rutarescue.service;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.AccessibilityFeature;
import com.rutarescue.model.center.CenterCapacity;
import com.rutarescue.model.center.CenterStatus;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.CenterRepository;
import com.rutarescue.repository.CenterSnapshot;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.util.GeoMath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Evacuation center use cases: resident search/filter (DOCX section 4 "Centers") and LGU
 * management with audit events (DOCX sections 3 and 5).
 */
public final class CenterService {

    /** "Nearby" means within 10 km of the resident (the legacy app used 5 km). */
    public static final double NEARBY_METERS = 10_000;

    private final CenterRepository repository;

    public CenterService(CenterRepository repository) {
        this.repository = repository;
    }

    public ListenerHandle listenPublished(Callback<SourcedData<List<EvacuationCenter>>> callback) {
        return repository.listenPublished(callback);
    }

    public ListenerHandle listenForManager(LguProfile lgu, Callback<SourcedData<List<EvacuationCenter>>> callback) {
        return repository.listenForCity(lgu.getJurisdiction(), callback);
    }

    public void get(String id, Callback<EvacuationCenter> callback) {
        repository.get(id, callback);
    }

    /** Follows one center for the capacity screen (with the "Pending sync" flag). */
    public ListenerHandle listen(String id, Callback<CenterSnapshot> callback) {
        return repository.listen(id, callback);
    }

    /**
     * Saves the capacity screen: headcount, status and facilities, with an audit event that
     * says what changed (e.g. "Headcount 207 → 212 · barangay field check").
     */
    public void updateCapacity(LguProfile lgu, EvacuationCenter center, CenterCapacity capacity, CenterStatus status,
                               Set<AccessibilityFeature> features, String source, Callback<Void> callback) {
        if (!lgu.canManage(center.getCity())) {
            callback.onError(new IllegalStateException("You can only manage centers in " + lgu.getJurisdiction() + "."));
            return;
        }
        int before = center.getCapacity().getCurrentOccupancy();
        center.updateCapacity(capacity, status, features, lgu.getUid(), lgu.getDisplayName(), source,
                System.currentTimeMillis());
        String reason = "Headcount " + before + " → " + capacity.getCurrentOccupancy() + " of "
                + capacity.getTotalPersons() + " · " + center.getStatus().getLabel()
                + (source == null || source.isBlank() ? "" : " · " + source.strip());
        save(lgu, center, "UPDATE_CAPACITY", reason, callback);
    }

    /** Search + filter chips + distance sort (pure Java, also works on cached data offline). */
    public static List<EvacuationCenter> filter(List<EvacuationCenter> all, String query, boolean nearbyOnly,
                                                boolean openOnly, boolean accessibleOnly, GeoPoint origin) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        List<EvacuationCenter> result = new ArrayList<>();
        for (EvacuationCenter center : all) {
            if (!needle.isEmpty()) {
                String haystack = (center.getName() + " " + center.getAddress() + " "
                        + center.getCity().getDisplayName()).toLowerCase(Locale.ROOT);
                if (!haystack.contains(needle)) continue;
            }
            if (openOnly && !center.getStatus().isAcceptingEvacuees()) continue;
            if (accessibleOnly && !center.hasAccessibilityFeature()) continue;
            if (nearbyOnly && origin != null && distance(center, origin) > NEARBY_METERS) continue;
            result.add(center);
        }
        if (origin != null) {
            result.sort(Comparator.comparingDouble(center -> distance(center, origin)));
        } else {
            result.sort(Comparator.comparing(EvacuationCenter::getName, String.CASE_INSENSITIVE_ORDER));
        }
        return result;
    }

    public static double distance(EvacuationCenter center, GeoPoint origin) {
        return GeoMath.distanceMeters(center.getLocation(), origin);
    }

    public static EvacuationCenter nearest(List<EvacuationCenter> centers, GeoPoint origin) {
        EvacuationCenter best = null;
        double bestDistance = Double.MAX_VALUE;
        for (EvacuationCenter center : centers) {
            CenterStatus status = center.getStatus();
            if (status == CenterStatus.CLOSED || status == CenterStatus.FULL || status == CenterStatus.UNAVAILABLE) continue;
            double d = origin == null ? 0 : distance(center, origin);
            if (d < bestDistance) {
                best = center;
                bestDistance = d;
            }
        }
        return best;
    }

    /** Creates or updates a center for the LGU's own city, with an audit event. */
    public void save(LguProfile lgu, EvacuationCenter center, String action, String reason, Callback<Void> callback) {
        if (!lgu.canManage(center.getCity())) {
            callback.onError(new IllegalStateException("You can only manage centers in " + lgu.getJurisdiction() + "."));
            return;
        }
        if (center.getId() == null) center.assignId(repository.newId());
        AuditEvent audit = AuditEvent.create(lgu.getUid(), lgu.getJurisdiction().code(), action, center,
                reason, System.currentTimeMillis());
        repository.save(center, audit, callback);
    }

    /** Verifies (with a named source) and publishes a center. */
    public void verifyAndPublish(LguProfile lgu, EvacuationCenter center, String source, Callback<Void> callback) {
        long now = System.currentTimeMillis();
        try {
            center.verify(source, lgu.getUid(), now);
            center.setPublished(true, lgu.getUid(), now);
        } catch (ValidationException | IllegalStateException e) {
            callback.onError(e);
            return;
        }
        save(lgu, center, "VERIFY_AND_PUBLISH", "Verified against: " + source, callback);
    }

    /** Archive instead of delete (DOCX section 5); archived centers disappear for residents. */
    public void archive(LguProfile lgu, EvacuationCenter center, String reason, Callback<Void> callback) {
        long now = System.currentTimeMillis();
        center.setPublished(false, lgu.getUid(), now);
        center.archive(lgu.getUid(), now);
        save(lgu, center, "ARCHIVE", reason, callback);
    }
}
