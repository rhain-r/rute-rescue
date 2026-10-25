package com.rutarescue.repository;

import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.user.ModeratorProfile;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.model.user.StaffRequest;

import java.util.List;

/**
 * Staff sign-up requests (staffRequests/{uid}) and approved staff (lgu_staff/{uid}).
 * The rules let a person create only their own PENDING request, and only the LGU of the
 * same city approve or reject it.
 */
public interface StaffRepository {

    /** Creates the signed-in person's own PENDING request. */
    void submitRequest(StaffRequest request, Callback<Void> callback);

    /** Follows the requests of one city, newest first (the LGU's Finalize tab). */
    ListenerHandle listenRequests(MetroManilaCity city, Callback<List<StaffRequest>> callback);

    /** Approves: creates lgu_staff/{uid} and marks the request approved, with the audit event, atomically. */
    void approve(StaffRequest request, StaffProfile newStaff, AuditEvent audit, Callback<Void> callback);

    /** Follows the approved staff of one city (the LGU's list of moderators and their centers). */
    ListenerHandle listenStaff(MetroManilaCity city, Callback<List<StaffProfile>> callback);

    /** Gives a moderator (another) center to run, with the audit event, atomically. */
    void assignCenter(ModeratorProfile moderator, AuditEvent audit, Callback<Void> callback);

    /** Rejects with the reason the applicant will read, with the audit event, atomically. */
    void reject(StaffRequest request, AuditEvent audit, Callback<Void> callback);
}
