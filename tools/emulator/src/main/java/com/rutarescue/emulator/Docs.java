package com.rutarescue.emulator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds documents with the same field names the Android app writes (see
 * FirestoreMappers in the app), so the rules are tested against realistic data.
 */
public final class Docs {

    private Docs() {
    }

    /** map("a", 1, "b", "x") -> {a=1, b=x}; allows null values unlike Map.of. */
    public static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) map.put((String) keyValues[i], keyValues[i + 1]);
        return map;
    }

    public static Map<String, Object> resident(String name, String email, String city) {
        return map("displayName", name, "email", email, "city", city, "role", "RESIDENT",
                "createdAt", System.currentTimeMillis());
    }

    public static Map<String, Object> lguStaff(String name, String email, String jurisdiction, String position) {
        return map("displayName", name, "email", email, "jurisdiction", jurisdiction, "position", position);
    }

    /** An approved staff record with a role (MODERATOR or LGU), as the LGU's approval writes it. */
    public static Map<String, Object> staff(String name, String email, String jurisdiction, String position, String role,
                                            String approvedBy) {
        return map("displayName", name, "email", email, "jurisdiction", jurisdiction, "position", position,
                "role", role, "approvedBy", approvedBy, "approvedAt", System.currentTimeMillis());
    }

    /** 2026-10-22: an approved moderator with the center the LGU assigned. */
    public static Map<String, Object> moderator(String name, String email, String jurisdiction, String approvedBy,
                                                String centerId, String centerName) {
        Map<String, Object> staff = staff(name, email, jurisdiction, "Barangay DRRM", "MODERATOR", approvedBy);
        staff.put("centerId", centerId);
        staff.put("centerName", centerName);
        return staff;
    }

    /** A staff sign-up as the app's FirestoreStaffRepository writes it. */
    public static Map<String, Object> staffRequest(String name, String email, String role, String jurisdiction) {
        return map("displayName", name, "email", email, "role", role, "jurisdiction", jurisdiction,
                "position", "Barangay DRRM", "requestedAt", System.currentTimeMillis(), "status", "PENDING",
                "decidedBy", "", "decisionReason", "", "decidedAt", 0L);
    }

    public static Map<String, Object> family(String ownerUid, String city) {
        List<Object> members = new ArrayList<>();
        members.add(map("memberId", "m1", "fullName", "Demo Head", "age", 40, "pwd", false, "pregnant", false,
                "supportNeeds", ""));
        return map("recordId", "FR-0001", "ownerUid", ownerUid, "headName", "Demo Head", "contactNumber", "09171234567",
                "address", "1 Demo Street", "city", city, "preferredCenter", "Demo Covered Court", "declaredSize", 1,
                "members", members, "trustedContacts", new ArrayList<>(), "archived", false);
    }

    public static Map<String, Object> audit(String actorUid, String jurisdiction, String action, String entityType,
                                            String recordId, String reason) {
        return map("actorUid", actorUid, "jurisdiction", jurisdiction, "action", action, "entityType", entityType,
                "recordId", recordId, "reason", reason, "timestamp", System.currentTimeMillis());
    }

    public static Map<String, Object> advisory(String city, String title, String message, String lastAuditId) {
        return map("title", title, "message", message, "level", "ALERT_2", "city", city, "published", true,
                "publishedAt", System.currentTimeMillis(), "authorName", "Demo LGU", "lastAuditId", lastAuditId,
                "archived", false);
    }

    /** A moderator's advisory about their center, waiting for the LGU (2026-10-22). */
    public static Map<String, Object> moderatorAdvisory(String city, String centerId, String requestedByUid,
                                                        String lastAuditId) {
        Map<String, Object> advisory = advisory(city, "Center almost full", "Almario school has room for 20 more people.",
                lastAuditId);
        advisory.put("level", "ADVISORY");
        advisory.put("published", false);
        advisory.put("publishedAt", 0L);
        advisory.put("authorName", "Almario Elementary School");
        advisory.put("review", "PENDING");
        advisory.put("awaitingLgu", true);
        advisory.put("lguNote", "");
        advisory.put("requestedBy", "Manila Moderator");
        advisory.put("requestedByUid", requestedByUid);
        advisory.put("centerId", centerId);
        advisory.put("centerName", "Almario Elementary School");
        return advisory;
    }

    /** The city alert level as the app's FirestoreCityAlertRepository writes it. */
    public static Map<String, Object> cityAlert(String city, String level, String lastAuditId) {
        return map("city", city, "level", level, "note", "", "setByName", "Demo LGU",
                "setAt", System.currentTimeMillis(), "lastAuditId", lastAuditId, "archived", false);
    }

    /** A road closure as the app writes it. */
    public static Map<String, Object> closure(String city, String lastAuditId) {
        return map("name", "Demo Bridge", "lat", 14.6, "lon", 120.98, "radiusMeters", 80, "reason", "Flooded (demo)",
                "city", city, "active", true, "reportedAt", System.currentTimeMillis(), "lastAuditId", lastAuditId,
                "archived", false);
    }

    /** A newly issued priority ID (nothing checked yet), as FirestorePriorityCardRepository writes it. */
    public static Map<String, Object> priorityCard(String ownerUid, String city, String group) {
        return map("ownerUid", ownerUid, "holderName", "Demo Senior", "group", group, "city", city,
                "issuedAt", System.currentTimeMillis(), "checks", new HashMap<String, Object>(), "missingCount", 0,
                "flagNote", "", "checkedBy", "", "checkedByName", "", "checkedAt", 0, "checkedAtCenter", "");
    }

    public static Map<String, Object> center(String city, boolean verified, boolean published, String lastAuditId) {
        return map("name", "Demo Covered Court", "address", "Demo Street", "city", city, "lat", 14.6042, "lon", 120.9822,
                "status", "OPEN", "features", new ArrayList<>(), "verified", verified,
                "verificationSource", verified ? "Barangay field check (demo)" : "Not verified", "verifiedBy", "",
                "verifiedAt", 0, "published", published, "notes", "", "lastAuditId", lastAuditId, "archived", false);
    }

    public static Map<String, Object> application(String ownerUid, String city) {
        List<Object> history = new ArrayList<>();
        history.add(map("status", "PENDING", "actorUid", ownerUid, "reason", "Submitted by resident",
                "at", System.currentTimeMillis()));
        return map("ownerUid", ownerUid, "city", city, "applicantName", "Demo Resident", "memberId", "m1",
                "memberName", "Demo Senior", "category", "SENIOR_CITIZEN",
                "justification", "Synthetic demo justification for a senior member.", "proofRef", "inline:demo",
                "submittedAt", System.currentTimeMillis(), "status", "PENDING", "reviewerUid", "", "reviewReason", "",
                "decidedAt", 0, "history", history);
    }

    /** The same application after an LGU decision. */
    public static Map<String, Object> decided(Map<String, Object> application, String status, String reviewerUid,
                                              String reason, String lastAuditId) {
        Map<String, Object> copy = new HashMap<>(application);
        copy.put("status", status);
        copy.put("reviewerUid", reviewerUid);
        copy.put("reviewReason", reason);
        copy.put("decidedAt", System.currentTimeMillis());
        if (lastAuditId != null) copy.put("lastAuditId", lastAuditId);
        return copy;
    }
}
