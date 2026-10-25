package com.rutarescue.model;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.AccessibilityFeature;
import com.rutarescue.model.center.CenterStatus;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.center.VerificationInfo;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.ligtas.NeedCategory;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.model.user.ModeratorProfile;
import com.rutarescue.model.user.ResidentProfile;
import com.rutarescue.model.user.Role;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.model.user.UserProfile;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 2026-10-22: a moderator runs one center (headcounts, Ligtas, priority IDs, advisories for approval);
 * the LGU creates centers, sets the alert level, posts closures and approves. Both only for their own city.
 */
public class StaffRolesTest {

    private final StaffProfile moderator = new ModeratorProfile("mod-1", "Ana Cruz", "ana@example.test",
            MetroManilaCity.MANILA, "Barangay DRRM");
    private final StaffProfile lgu = new LguProfile("lgu-1", "Ben Reyes", "ben@example.test",
            MetroManilaCity.MANILA, "DRRM Officer");

    private static LigtasApplication application(MetroManilaCity city) throws ValidationException {
        return LigtasApplication.submit("AbCdEf1234567890", "resident-1", city, "Maria Santos", "m1", "Pedro Santos",
                NeedCategory.SENIOR_CITIZEN, "Senior resident who needs daily medication.", "proof", "", 1_000L);
    }

    private static EvacuationCenter center(String id, MetroManilaCity city) throws ValidationException {
        return new EvacuationCenter(id, "Almario Elementary School", "Tondo", city, new GeoPoint(14.60, 120.98),
                CenterStatus.OPEN, EnumSet.noneOf(AccessibilityFeature.class), VerificationInfo.unverified("test"), true, "");
    }

    @Test
    public void theModeratorRunsTheirCenter() {
        assertEquals(Role.MODERATOR, moderator.getRole());
        assertFalse("only the LGU adds centers", moderator.canCreateCenters());
        assertTrue(moderator.canDecideLigtas());
        assertTrue(moderator.canVerifyPriorityIds());
        assertFalse(moderator.canPublishToResidents());
        assertFalse(moderator.canApproveStaff());
        assertFalse("only the LGU sets the alert level", moderator.canSetAlertLevel());
        assertFalse("only the LGU posts closures", moderator.canPostRoadNotices());
        assertFalse("no advisories before a center is assigned", moderator.canSubmitAdvisories());
        assertEquals("Barangay DRRM • Moderator • Manila", moderator.getRoleLabel());
    }

    @Test
    public void theLguCreatesAnnouncesAndApproves() {
        assertEquals(Role.LGU, lgu.getRole());
        assertTrue(lgu.canCreateCenters());
        assertFalse(lgu.canDecideLigtas());
        assertTrue(lgu.canPublishToResidents());
        assertTrue(lgu.canApproveStaff());
        assertTrue(lgu.canSetAlertLevel());
        assertTrue(lgu.canPostRoadNotices());
        assertFalse(lgu.canSubmitAdvisories());
        assertFalse(lgu.canVerifyPriorityIds());
        assertEquals("DRRM Officer • LGU • Manila", lgu.getRoleLabel());
    }

    @Test
    public void aModeratorManagesOnlyTheAssignedCenter() throws ValidationException {
        ModeratorProfile assigned = new ModeratorProfile("mod-3", "Ana Cruz", "", MetroManilaCity.MANILA, "Staff",
                "c1", "Almario Elementary School");
        assertTrue(assigned.canManageCenter(center("c1", MetroManilaCity.MANILA)));
        assertFalse(assigned.canManageCenter(center("c2", MetroManilaCity.MANILA)));
        assertFalse("never a center of another city", assigned.canManageCenter(center("c1", MetroManilaCity.PASIG)));
        assertFalse(moderator.canManageCenter(center("c1", MetroManilaCity.MANILA)));
        assertFalse("the LGU reads headcounts but does not enter them", lgu.canManageCenter(center("c1", MetroManilaCity.MANILA)));
        assertTrue(assigned.canSubmitAdvisories());
        assertEquals("Staff • Moderator • Manila • Almario Elementary School", assigned.getRoleLabel());
    }

    @Test
    public void bothAreLockedToTheirOwnCity() throws ValidationException {
        assertTrue(moderator.canManage(MetroManilaCity.MANILA));
        assertFalse(moderator.canManage(MetroManilaCity.PASIG));
        assertTrue(lgu.canManage(MetroManilaCity.MANILA));
        assertFalse(lgu.canManage(MetroManilaCity.PASIG));
        assertTrue(moderator.canReview(application(MetroManilaCity.MANILA)));
        assertFalse(moderator.canReview(application(MetroManilaCity.PASIG)));
        assertFalse("the LGU no longer decides Ligtas", lgu.canReview(application(MetroManilaCity.MANILA)));
    }

    @Test
    public void staffNeedACity() {
        assertThrows(IllegalArgumentException.class,
                () -> new ModeratorProfile("mod-2", "No City", "", null, "Volunteer"));
    }

    @Test
    public void residentsAreNotStaff() {
        UserProfile resident = new ResidentProfile("r1", "Maria", "", MetroManilaCity.MANILA, 0);
        assertFalse(resident instanceof StaffProfile);
        assertFalse(resident.canManage(MetroManilaCity.MANILA));
    }

    @Test
    public void staffRolesAreReadFromTheDatabaseText() {
        assertEquals(Role.MODERATOR, Role.staffRoleFrom("MODERATOR"));
        assertEquals(Role.LGU, Role.staffRoleFrom("LGU"));
        assertEquals("a missing role is the original LGU account", Role.LGU, Role.staffRoleFrom(""));
        assertEquals(Role.LGU, Role.staffRoleFrom(null));
        assertEquals("never RESIDENT for a staff record", Role.LGU, Role.staffRoleFrom("RESIDENT"));
    }
}
