package com.rutarescue.repository.firestore;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.model.user.ModeratorProfile;
import com.rutarescue.model.user.Role;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.model.user.StaffRequest;
import com.rutarescue.model.user.StaffRequestStatus;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.repository.StaffRepository;
import com.rutarescue.service.ConnectivityMonitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * staffRequests/{uid} (sign-ups waiting for the LGU) and lgu_staff/{uid} (approved staff).
 * Approving writes the new staff record, the decided request and the audit event in one batch,
 * so the rules can check all three together.
 */
public final class FirestoreStaffRepository implements StaffRepository {

    static final String REQUESTS = "staffRequests";
    static final String STAFF = "lgu_staff";

    private final FirebaseFirestore db;
    private final ConnectivityMonitor connectivity;

    public FirestoreStaffRepository(FirebaseFirestore db, ConnectivityMonitor connectivity) {
        this.db = db;
        this.connectivity = connectivity;
    }

    @Override
    public void submitRequest(StaffRequest request, Callback<Void> callback) {
        Map<String, Object> map = new HashMap<>();
        map.put("displayName", request.getDisplayName());
        map.put("email", request.getEmail());
        map.put("role", request.getRole().name());
        map.put("jurisdiction", request.getJurisdiction().code());
        map.put("position", request.getPosition());
        map.put("requestedAt", request.getRequestedAt());
        map.put("status", StaffRequestStatus.PENDING.name());
        map.put("decidedBy", "");
        map.put("decisionReason", "");
        map.put("decidedAt", 0L);
        FirestoreWrites.complete(db.collection(REQUESTS).document(request.getUid()).set(map), connectivity, callback);
    }

    @Override
    public ListenerHandle listenRequests(MetroManilaCity city, Callback<List<StaffRequest>> callback) {
        ListenerRegistration registration = db.collection(REQUESTS)
                .whereEqualTo("jurisdiction", city.code())
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snapshot == null) return;
                    List<StaffRequest> requests = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        requests.add(request(doc));
                    }
                    requests.sort((a, b) -> Long.compare(b.getRequestedAt(), a.getRequestedAt()));
                    callback.onSuccess(requests);
                });
        return registration::remove;
    }

    @Override
    public void approve(StaffRequest request, StaffProfile newStaff, AuditEvent audit, Callback<Void> callback) {
        Map<String, Object> staff = new HashMap<>();
        staff.put("displayName", newStaff.getDisplayName());
        staff.put("email", newStaff.getEmail());
        staff.put("jurisdiction", newStaff.getJurisdiction().code());
        staff.put("position", newStaff.getPosition());
        staff.put("role", newStaff.getRole().name());
        staff.put("approvedBy", audit.getActorUid());
        staff.put("approvedAt", audit.getTimestamp());
        if (newStaff instanceof ModeratorProfile) {
            // 2026-10-22: a moderator runs the center the LGU chose when approving.
            staff.put("centerId", ((ModeratorProfile) newStaff).getCenterId());
            staff.put("centerName", ((ModeratorProfile) newStaff).getCenterName());
        }
        WriteBatch batch = db.batch();
        batch.set(db.collection(STAFF).document(request.getUid()), staff);
        batch.update(db.collection(REQUESTS).document(request.getUid()), decision(request, audit));
        batch.set(db.collection("auditEvents").document(audit.getId()), FirestoreMappers.audit(audit));
        FirestoreWrites.complete(batch.commit(), connectivity, callback);
    }

    @Override
    public ListenerHandle listenStaff(MetroManilaCity city, Callback<List<StaffProfile>> callback) {
        ListenerRegistration registration = db.collection(STAFF)
                .whereEqualTo("jurisdiction", city.code())
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snapshot == null) return;
                    List<StaffProfile> members = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        StaffProfile member = staff(doc);
                        if (member != null) members.add(member);
                    }
                    members.sort((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));
                    callback.onSuccess(members);
                });
        return registration::remove;
    }

    @Override
    public void assignCenter(ModeratorProfile moderator, AuditEvent audit, Callback<Void> callback) {
        Map<String, Object> changes = new HashMap<>();
        changes.put("centerId", moderator.getCenterId());
        changes.put("centerName", moderator.getCenterName());
        changes.put("assignedBy", audit.getActorUid());
        changes.put("assignedAt", audit.getTimestamp());
        changes.put("lastAuditId", audit.getId());
        WriteBatch batch = db.batch();
        batch.update(db.collection(STAFF).document(moderator.getUid()), changes);
        batch.set(db.collection("auditEvents").document(audit.getId()), FirestoreMappers.audit(audit));
        FirestoreWrites.complete(batch.commit(), connectivity, callback);
    }

    @Override
    public void reject(StaffRequest request, AuditEvent audit, Callback<Void> callback) {
        WriteBatch batch = db.batch();
        batch.update(db.collection(REQUESTS).document(request.getUid()), decision(request, audit));
        batch.set(db.collection("auditEvents").document(audit.getId()), FirestoreMappers.audit(audit));
        FirestoreWrites.complete(batch.commit(), connectivity, callback);
    }

    /** Only the decision fields change; the rules check that nothing else does. */
    private static Map<String, Object> decision(StaffRequest request, AuditEvent audit) {
        Map<String, Object> changes = new HashMap<>();
        changes.put("status", request.getStatus().name());
        changes.put("decidedBy", request.getDecidedBy());
        changes.put("decisionReason", request.getDecisionReason());
        changes.put("decidedAt", request.getDecidedAt());
        changes.put("lastAuditId", audit.getId());
        return changes;
    }

    /**
     * Reads an approved staff record (lgu_staff/{uid}); null when it has no valid city.
     * Polymorphism: the stored role picks the subclass; every screen then asks the profile.
     */
    static StaffProfile staff(DocumentSnapshot doc) {
        MetroManilaCity jurisdiction = MetroManilaCity.fromCode(FirestoreMappers.str(doc, "jurisdiction"));
        if (jurisdiction == null) return null;
        String name = FirestoreMappers.str(doc, "displayName");
        String email = FirestoreMappers.str(doc, "email");
        String position = FirestoreMappers.str(doc, "position");
        return Role.staffRoleFrom(FirestoreMappers.str(doc, "role")) == Role.MODERATOR
                ? new ModeratorProfile(doc.getId(), name, email, jurisdiction, position,
                FirestoreMappers.str(doc, "centerId"), FirestoreMappers.str(doc, "centerName"))
                : new LguProfile(doc.getId(), name, email, jurisdiction, position);
    }

    /** Reads a stored request (also used when a pending person signs in). */
    static StaffRequest request(DocumentSnapshot doc) {
        return new StaffRequest(doc.getId(), FirestoreMappers.str(doc, "displayName"), FirestoreMappers.str(doc, "email"),
                Role.staffRoleFrom(FirestoreMappers.str(doc, "role")),
                MetroManilaCity.fromCode(FirestoreMappers.str(doc, "jurisdiction")), FirestoreMappers.str(doc, "position"),
                FirestoreMappers.num(doc, "requestedAt"), StaffRequestStatus.fromName(FirestoreMappers.str(doc, "status")),
                FirestoreMappers.str(doc, "decidedBy"), FirestoreMappers.str(doc, "decisionReason"),
                FirestoreMappers.num(doc, "decidedAt"));
    }
}
