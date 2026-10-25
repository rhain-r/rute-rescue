package com.rutarescue.model;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.model.user.ModeratorProfile;
import com.rutarescue.model.user.Role;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.model.user.StaffRequest;
import com.rutarescue.model.user.StaffRequestStatus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Staff sign-up: a locked request until the LGU of the same city approves it. */
public class StaffRequestTest {

    private static final long NOW = 1_800_000_000_000L;
    private final LguProfile manilaLgu = new LguProfile("lgu-1", "Ben Reyes", "", MetroManilaCity.MANILA, "DRRM Officer");

    private static StaffRequest moderatorRequest() throws ValidationException {
        return StaffRequest.create("uid-9", "ana cruz", "ana@example.test", Role.MODERATOR, MetroManilaCity.MANILA,
                "Barangay DRRM", NOW);
    }

    @Test
    public void aNewRequestIsPendingAndNamesTheCity() throws ValidationException {
        StaffRequest request = moderatorRequest();
        assertEquals(StaffRequestStatus.PENDING, request.getStatus());
        assertEquals("Ana Cruz", request.getDisplayName());
        assertEquals(MetroManilaCity.MANILA, request.getJurisdiction());
        assertEquals("staffRequest", request.getEntityType());
        assertTrue(request.waitingMessage().contains("Manila LGU"));
    }

    @Test
    public void onlyStaffRolesCanBeRequested() {
        assertThrows(ValidationException.class, () -> StaffRequest.create("uid-9", "Ana Cruz", "ana@example.test",
                Role.RESIDENT, MetroManilaCity.MANILA, "", NOW));
        assertThrows(ValidationException.class, () -> StaffRequest.create("uid-9", "Ana Cruz", "ana@example.test",
                Role.MODERATOR, null, "", NOW));
    }

    @Test
    public void theLguOfTheSameCityApprovesIntoTheRightProfile() throws ValidationException {
        StaffRequest request = moderatorRequest();
        request.approve(manilaLgu, "c1", "Almario Elementary School", NOW + 1);
        assertEquals(StaffRequestStatus.APPROVED, request.getStatus());
        StaffProfile profile = request.toProfile();
        assertTrue(profile instanceof ModeratorProfile);
        assertEquals(MetroManilaCity.MANILA, profile.getJurisdiction());
        assertEquals("c1", ((ModeratorProfile) profile).getCenterId());
        assertEquals("Almario Elementary School", ((ModeratorProfile) profile).getCenterName());
        assertThrows("decided only once", IllegalStateException.class, () -> request.reject(manilaLgu, "Too late", NOW + 2));
    }

    @Test
    public void aModeratorIsApprovedWithTheCenterTheyRun() throws ValidationException {
        StaffRequest request = moderatorRequest();
        assertThrows("a moderator needs a center", IllegalStateException.class, () -> request.approve(manilaLgu, NOW));
        assertEquals(StaffRequestStatus.PENDING, request.getStatus());

        StaffRequest lguRequest = StaffRequest.create("uid-8", "Ben Cruz", "ben@example.test", Role.LGU,
                MetroManilaCity.MANILA, "", NOW);
        lguRequest.approve(manilaLgu, NOW + 1);
        assertTrue("an LGU officer has no center", lguRequest.toProfile() instanceof LguProfile);
    }

    @Test
    public void otherCitiesAndModeratorsCannotApprove() throws ValidationException {
        StaffRequest request = moderatorRequest();
        LguProfile pasigLgu = new LguProfile("lgu-2", "Pasig Officer", "", MetroManilaCity.PASIG, "Officer");
        ModeratorProfile manilaModerator = new ModeratorProfile("mod-1", "Mod", "", MetroManilaCity.MANILA, "Staff");
        assertThrows(IllegalStateException.class, () -> request.approve(pasigLgu, NOW));
        assertThrows(IllegalStateException.class, () -> request.approve(manilaModerator, NOW));
        assertEquals(StaffRequestStatus.PENDING, request.getStatus());
    }

    @Test
    public void aRejectionNeedsAReasonTheApplicantCanRead() throws ValidationException {
        StaffRequest request = moderatorRequest();
        assertThrows(ValidationException.class, () -> request.reject(manilaLgu, " ", NOW));
        request.reject(manilaLgu, "Please use your official city email.", NOW + 1);
        assertEquals(StaffRequestStatus.REJECTED, request.getStatus());
        assertTrue(request.waitingMessage().contains("official city email"));
    }
}
