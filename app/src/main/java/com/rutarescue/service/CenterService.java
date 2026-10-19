package com.rutarescue.service;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.AccessibilityFeature;
import com.rutarescue.model.center.CenterCapacity;
import com.rutarescue.model.center.CenterStatus;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.DataFreshness;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.persistence.BundledCenterReader;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.CenterRepository;
import com.rutarescue.repository.CenterSnapshot;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.util.Formats;
import com.rutarescue.util.GeoMath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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

    /** Shown on the "Evacuation centers" source chip when only the bundled list is available. */
    public static final String BUNDLED_SOURCE = "PLM SSC list, 24 Jul 2024";

    private final CenterRepository repository;
    /** The PLM SSC list bundled with the app (unverified), so residents always see centers. */
    private final List<EvacuationCenter> bundled;

    public CenterService(CenterRepository repository, List<EvacuationCenter> bundled) {
        this.repository = repository;
        this.bundled = bundled == null ? new ArrayList<>() : new ArrayList<>(bundled);
    }

    /**
     * Residents' centers: the ones the LGU published plus the bundled PLM SSC list. If the server
     * cannot answer, the bundled list is still shown (labelled "Prepared") instead of an error.
     */
    public ListenerHandle listenPublished(Callback<SourcedData<List<EvacuationCenter>>> callback) {
        return repository.listenPublished(new Callback<SourcedData<List<EvacuationCenter>>>() {
            @Override
            public void onSuccess(SourcedData<List<EvacuationCenter>> data) {
                callback.onSuccess(new SourcedData<>(withBundled(data.getData(), bundled), data.getFreshness(),
                        data.getRetrievedAt(), data.getSource()));
            }

            @Override
            public void onError(Exception error) {
                callback.onSuccess(new SourcedData<>(withBundled(new ArrayList<>(), bundled), DataFreshness.PREPARED,
                        0, BUNDLED_SOURCE));
            }
        });
    }

    public ListenerHandle listenForManager(LguProfile lgu, Callback<SourcedData<List<EvacuationCenter>>> callback) {
        return repository.listenForCity(lgu.getJurisdiction(), callback);
    }

    /** Bundled centers are found on the phone; the others are read from the server. */
    public void get(String id, Callback<EvacuationCenter> callback) {
        for (EvacuationCenter center : bundled) {
            if (center.getId().equals(id)) {
                callback.onSuccess(center);
                return;
            }
        }
        repository.get(id, callback);
    }

    /**
     * LGU-published centers plus every bundled center the LGU has not published yet. A bundled
     * center counts as published when an LGU center in the same city has the same name
     * (ignoring capital letters and extra spaces), so the same place never shows twice.
     */
    public static List<EvacuationCenter> withBundled(List<EvacuationCenter> published, List<EvacuationCenter> bundled) {
        List<EvacuationCenter> result = new ArrayList<>(published);
        Set<String> publishedKeys = new HashSet<>();
        for (EvacuationCenter center : published) publishedKeys.add(sameCenterKey(center));
        for (EvacuationCenter center : bundled) {
            if (!publishedKeys.contains(sameCenterKey(center))) result.add(center);
        }
        return result;
    }

    /** True for a center from the bundled PLM SSC list (not yet confirmed by the LGU). */
    public static boolean isBundled(EvacuationCenter center) {
        return center.getId() != null && center.getId().startsWith(BundledCenterReader.ID_PREFIX);
    }

    private static String sameCenterKey(EvacuationCenter center) {
        String name = center.getName().strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return center.getCity().code() + "|" + name;
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

    /**
     * One short line for lists of centers: the distance (when the resident's location is known),
     * then the posted status, or the city and "no status posted yet" when no LGU has posted one.
     * E.g. "0.8 km • Near capacity" or "489 m • Manila • no status posted yet".
     */
    public static String summary(EvacuationCenter center, GeoPoint origin) {
        StringBuilder text = new StringBuilder();
        if (origin != null) text.append(Formats.distance(distance(center, origin))).append(" • ");
        if (center.getStatus() == CenterStatus.NOT_REPORTED) {
            text.append(center.getCity().getDisplayName()).append(" • no status posted yet");
        } else {
            text.append(center.getStatus().getLabel());
        }
        return text.toString();
    }

    public static double distance(EvacuationCenter center, GeoPoint origin) {
        return GeoMath.distanceMeters(center.getLocation(), origin);
    }

    /**
     * The closest center that can still take people (not closed, full or unavailable), or null when
     * the resident's location is not known yet: without it, no center can honestly be called nearest.
     */
    public static EvacuationCenter nearest(List<EvacuationCenter> centers, GeoPoint origin) {
        if (origin == null) return null;
        EvacuationCenter best = null;
        double bestDistance = Double.MAX_VALUE;
        for (EvacuationCenter center : centers) {
            CenterStatus status = center.getStatus();
            if (status == CenterStatus.CLOSED || status == CenterStatus.FULL || status == CenterStatus.UNAVAILABLE) continue;
            double d = distance(center, origin);
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
