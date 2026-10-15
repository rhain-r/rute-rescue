package com.rutarescue.repository.firestore;

import com.google.firebase.firestore.DocumentSnapshot;
import com.rutarescue.exception.InvalidFamilyRecordException;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.advisory.AlertLevel;
import com.rutarescue.model.center.AccessibilityFeature;
import com.rutarescue.model.center.CenterStatus;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.center.VerificationInfo;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.AuditInfo;
import com.rutarescue.model.common.AuditableEntity;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.family.FamilyMember;
import com.rutarescue.model.family.FamilyRecord;
import com.rutarescue.model.family.TrustedContact;
import com.rutarescue.model.hazard.DatasetStatus;
import com.rutarescue.model.hazard.DatasetType;
import com.rutarescue.model.hazard.DatasetVersion;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.ligtas.NeedCategory;
import com.rutarescue.model.ligtas.StatusChange;
import com.rutarescue.model.route.RoadClosure;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts model objects to Firestore maps and back. Keeping every field name in one
 * class makes the database layout easy to read and matches firebase/firestore.rules.
 *
 * Reading goes through the model constructors, so data from the database is validated
 * exactly like user input (a broken document cannot create an invalid object).
 */
final class FirestoreMappers {

    private FirestoreMappers() {
    }

    // ---------------------------------------------------------------- small helpers

    static String str(DocumentSnapshot doc, String key) {
        String value = doc.getString(key);
        return value == null ? "" : value;
    }

    static long num(DocumentSnapshot doc, String key) {
        Long value = doc.getLong(key);
        return value == null ? 0L : value;
    }

    static double dbl(DocumentSnapshot doc, String key) {
        Double value = doc.getDouble(key);
        return value == null ? 0.0 : value;
    }

    static boolean bool(DocumentSnapshot doc, String key) {
        Boolean value = doc.getBoolean(key);
        return value != null && value;
    }

    static String mapStr(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString();
    }

    static long mapNum(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    static boolean mapBool(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof Boolean && (Boolean) value;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> list(DocumentSnapshot doc, String key) {
        Object value = doc.get(key);
        if (!(value instanceof List)) return new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : (List<Object>) value) {
            if (item instanceof Map) out.add((Map<String, Object>) item);
        }
        return out;
    }

    static void putAudit(Map<String, Object> map, AuditableEntity entity) {
        AuditInfo info = entity.getAuditInfo();
        if (info != null) {
            map.put("createdBy", info.getCreatedBy());
            map.put("createdAt", info.getCreatedAt());
            map.put("updatedBy", info.getUpdatedBy());
            map.put("updatedAt", info.getUpdatedAt());
        }
        map.put("archived", entity.isArchived());
    }

    static void restoreAudit(AuditableEntity entity, DocumentSnapshot doc) {
        AuditInfo info = new AuditInfo(str(doc, "createdBy"), num(doc, "createdAt"),
                str(doc, "updatedBy"), num(doc, "updatedAt"));
        entity.restoreFromStorage(doc.getId(), info, bool(doc, "archived"));
    }

    // ---------------------------------------------------------------- audit events

    static Map<String, Object> audit(AuditEvent event) {
        Map<String, Object> map = new HashMap<>();
        map.put("actorUid", event.getActorUid());
        map.put("jurisdiction", event.getJurisdiction());
        map.put("action", event.getAction());
        map.put("entityType", event.getEntityType());
        map.put("recordId", event.getRecordId());
        map.put("reason", event.getReason());
        map.put("timestamp", event.getTimestamp());
        return map;
    }

    static AuditEvent auditEvent(DocumentSnapshot doc) {
        return new AuditEvent(doc.getId(), str(doc, "actorUid"), str(doc, "jurisdiction"), str(doc, "action"),
                str(doc, "entityType"), str(doc, "recordId"), str(doc, "reason"), num(doc, "timestamp"));
    }

    // ---------------------------------------------------------------- family records

    static Map<String, Object> family(FamilyRecord record) {
        Map<String, Object> map = new HashMap<>();
        map.put("recordId", record.getRecordId());
        map.put("ownerUid", record.getOwnerUid());
        map.put("headName", record.getHeadName());
        map.put("contactNumber", record.getContactNumber());
        map.put("address", record.getAddress());
        map.put("city", record.getCity() == null ? null : record.getCity().code());
        map.put("preferredCenter", record.getPreferredCenter());
        map.put("declaredSize", record.getDeclaredSize());
        List<Map<String, Object>> members = new ArrayList<>();
        for (FamilyMember member : record.getMembers()) {
            Map<String, Object> m = new HashMap<>();
            m.put("memberId", member.getMemberId());
            m.put("fullName", member.getFullName());
            m.put("age", member.getAge());
            m.put("pwd", member.isPwd());
            m.put("pregnant", member.isPregnant());
            m.put("supportNeeds", member.getSupportNeeds());
            members.add(m);
        }
        map.put("members", members);
        List<Map<String, Object>> contacts = new ArrayList<>();
        for (TrustedContact contact : record.getTrustedContacts()) {
            Map<String, Object> c = new HashMap<>();
            c.put("name", contact.getName());
            c.put("phone", contact.getPhone());
            contacts.add(c);
        }
        map.put("trustedContacts", contacts);
        putAudit(map, record);
        return map;
    }

    static FamilyRecord family(DocumentSnapshot doc) throws InvalidFamilyRecordException {
        List<FamilyMember> members = new ArrayList<>();
        for (Map<String, Object> m : list(doc, "members")) {
            members.add(new FamilyMember(mapStr(m, "memberId"), mapStr(m, "fullName"), (int) mapNum(m, "age"),
                    mapBool(m, "pwd"), mapBool(m, "pregnant"), mapStr(m, "supportNeeds")));
        }
        List<TrustedContact> contacts = new ArrayList<>();
        for (Map<String, Object> c : list(doc, "trustedContacts")) {
            contacts.add(new TrustedContact(mapStr(c, "name"), mapStr(c, "phone")));
        }
        int declared = (int) num(doc, "declaredSize");
        if (declared < 1) declared = Math.max(1, members.size());
        FamilyRecord record = new FamilyRecord(str(doc, "recordId"), str(doc, "headName"), str(doc, "contactNumber"),
                str(doc, "address"), str(doc, "preferredCenter"), declared);
        record.assignOwner(str(doc, "ownerUid"), MetroManilaCity.fromCode(str(doc, "city")));
        record.restoreMembersAndContacts(members, contacts);
        restoreAudit(record, doc);
        return record;
    }

    // ---------------------------------------------------------------- centers

    static Map<String, Object> center(EvacuationCenter center, String lastAuditId) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", center.getName());
        map.put("address", center.getAddress());
        map.put("city", center.getCity().code());
        map.put("lat", center.getLocation().getLatitude());
        map.put("lon", center.getLocation().getLongitude());
        map.put("status", center.getStatus().name());
        List<String> features = new ArrayList<>();
        for (AccessibilityFeature feature : center.getFeatures()) features.add(feature.name());
        map.put("features", features);
        map.put("verified", center.getVerification().isVerified());
        map.put("verificationSource", center.getVerification().getSource());
        map.put("verifiedBy", center.getVerification().getVerifiedBy());
        map.put("verifiedAt", center.getVerification().getVerifiedAt());
        map.put("published", center.isPublished());
        map.put("notes", center.getNotes());
        map.put("lastAuditId", lastAuditId);
        putAudit(map, center);
        return map;
    }

    @SuppressWarnings("unchecked")
    static EvacuationCenter center(DocumentSnapshot doc) throws ValidationException {
        Set<AccessibilityFeature> features = EnumSet.noneOf(AccessibilityFeature.class);
        Object raw = doc.get("features");
        if (raw instanceof List) {
            for (Object item : (List<Object>) raw) {
                AccessibilityFeature feature = AccessibilityFeature.fromName(String.valueOf(item));
                if (feature != null) features.add(feature);
            }
        }
        VerificationInfo verification = new VerificationInfo(bool(doc, "verified"), str(doc, "verificationSource"),
                str(doc, "verifiedBy"), num(doc, "verifiedAt"));
        EvacuationCenter center = new EvacuationCenter(doc.getId(), str(doc, "name"), str(doc, "address"),
                MetroManilaCity.fromCode(str(doc, "city")), new GeoPoint(dbl(doc, "lat"), dbl(doc, "lon")),
                CenterStatus.fromName(str(doc, "status")), features, verification, bool(doc, "published"),
                str(doc, "notes"));
        restoreAudit(center, doc);
        return center;
    }

    // ---------------------------------------------------------------- advisories

    static Map<String, Object> advisory(Advisory advisory, String lastAuditId) {
        Map<String, Object> map = new HashMap<>();
        map.put("title", advisory.getTitle());
        map.put("message", advisory.getMessage());
        map.put("level", advisory.getLevel().name());
        map.put("city", advisory.getCity().code());
        map.put("published", advisory.isPublished());
        map.put("publishedAt", advisory.getPublishedAt());
        map.put("authorName", advisory.getAuthorName());
        map.put("lastAuditId", lastAuditId);
        putAudit(map, advisory);
        return map;
    }

    static Advisory advisory(DocumentSnapshot doc) throws ValidationException {
        Advisory advisory = new Advisory(doc.getId(), str(doc, "title"), str(doc, "message"),
                AlertLevel.fromName(str(doc, "level")), MetroManilaCity.fromCode(str(doc, "city")),
                bool(doc, "published"), num(doc, "publishedAt"), str(doc, "authorName"));
        restoreAudit(advisory, doc);
        return advisory;
    }

    // ---------------------------------------------------------------- Ligtas applications

    static Map<String, Object> ligtas(LigtasApplication app, String lastAuditId) {
        Map<String, Object> map = new HashMap<>();
        map.put("ownerUid", app.getOwnerUid());
        map.put("city", app.getCity() == null ? null : app.getCity().code());
        map.put("applicantName", app.getApplicantName());
        map.put("memberId", app.getMemberId());
        map.put("memberName", app.getMemberName());
        map.put("category", app.getCategory().name());
        map.put("justification", app.getJustification());
        map.put("proofRef", app.getProofRef());
        map.put("submittedAt", app.getSubmittedAt());
        map.put("status", app.getStatus().name());
        map.put("reviewerUid", app.getReviewerUid());
        map.put("reviewReason", app.getReviewReason());
        map.put("decidedAt", app.getDecidedAt());
        List<Map<String, Object>> history = new ArrayList<>();
        for (StatusChange change : app.getHistory()) {
            Map<String, Object> h = new HashMap<>();
            h.put("status", change.getStatus().name());
            h.put("actorUid", change.getActorUid());
            h.put("reason", change.getReason());
            h.put("at", change.getAt());
            history.add(h);
        }
        map.put("history", history);
        if (lastAuditId != null) map.put("lastAuditId", lastAuditId);
        return map;
    }

    static LigtasApplication ligtas(DocumentSnapshot doc) {
        List<StatusChange> history = new ArrayList<>();
        for (Map<String, Object> h : list(doc, "history")) {
            history.add(new StatusChange(ApplicationStatus.fromName(mapStr(h, "status")), mapStr(h, "actorUid"),
                    mapStr(h, "reason"), mapNum(h, "at")));
        }
        return new LigtasApplication(doc.getId(), str(doc, "ownerUid"), MetroManilaCity.fromCode(str(doc, "city")),
                str(doc, "applicantName"), str(doc, "memberId"), str(doc, "memberName"),
                NeedCategory.fromName(str(doc, "category")), str(doc, "justification"), str(doc, "proofRef"),
                num(doc, "submittedAt"), ApplicationStatus.fromName(str(doc, "status")), str(doc, "reviewerUid"),
                str(doc, "reviewReason"), num(doc, "decidedAt"), history);
    }

    // ---------------------------------------------------------------- dataset versions

    static Map<String, Object> dataset(DatasetVersion version, String lastAuditId) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", version.getType().name());
        map.put("versionLabel", version.getVersionLabel());
        map.put("status", version.getStatus().name());
        map.put("source", version.getSource());
        map.put("sha256", version.getSha256());
        map.put("recordCount", version.getRecordCount());
        map.put("notes", version.getNotes());
        map.put("city", version.getCity() == null ? null : version.getCity().code());
        map.put("publishedAt", version.getPublishedAt());
        map.put("lastAuditId", lastAuditId);
        putAudit(map, version);
        return map;
    }

    static DatasetVersion dataset(DocumentSnapshot doc) {
        DatasetVersion version = new DatasetVersion(doc.getId(), DatasetType.fromName(str(doc, "type")),
                str(doc, "versionLabel").isEmpty() ? "unnamed" : str(doc, "versionLabel"),
                DatasetStatus.fromName(str(doc, "status")), str(doc, "source"), str(doc, "sha256"),
                (int) num(doc, "recordCount"), str(doc, "notes"), MetroManilaCity.fromCode(str(doc, "city")),
                num(doc, "publishedAt"));
        restoreAudit(version, doc);
        return version;
    }

    // ---------------------------------------------------------------- road closures

    static Map<String, Object> closure(RoadClosure closure, String lastAuditId) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", closure.getName());
        map.put("lat", closure.getLocation().getLatitude());
        map.put("lon", closure.getLocation().getLongitude());
        map.put("radiusMeters", closure.getRadiusMeters());
        map.put("reason", closure.getReason());
        map.put("city", closure.getCity().code());
        map.put("active", closure.isActive());
        map.put("reportedAt", closure.getReportedAt());
        map.put("lastAuditId", lastAuditId);
        putAudit(map, closure);
        return map;
    }

    static RoadClosure closure(DocumentSnapshot doc) throws ValidationException {
        RoadClosure closure = new RoadClosure(doc.getId(), str(doc, "name"), new GeoPoint(dbl(doc, "lat"), dbl(doc, "lon")),
                (int) num(doc, "radiusMeters"), str(doc, "reason"), MetroManilaCity.fromCode(str(doc, "city")),
                bool(doc, "active"), num(doc, "reportedAt"));
        restoreAudit(closure, doc);
        return closure;
    }
}
