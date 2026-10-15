package com.rutarescue.model;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.ligtas.NeedCategory;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.model.user.ResidentProfile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class LigtasApplicationTest {

    private static final long NOW = 1_800_000_000_000L;

    private static LigtasApplication pending() throws ValidationException {
        return LigtasApplication.submit("AbCdEf1234567890", "resident-1", MetroManilaCity.MANILA, "Maria Santos",
                "m1", "Pedro Santos", NeedCategory.SENIOR_CITIZEN,
                "Senior resident who needs daily medication (synthetic demo).", "proof-ref", NOW);
    }

    private final LguProfile manilaReviewer =
            new LguProfile("lgu-1", "Reviewer", "lgu@example.test", MetroManilaCity.MANILA, "DRRM Officer");

    @Test
    public void approvalNeedsReasonAndKeepsHistory() throws Exception {
        LigtasApplication app = pending();
        assertThrows(ValidationException.class, () -> app.approve(manilaReviewer, " ", NOW));
        app.approve(manilaReviewer, "Synthetic proof matches the senior category.", NOW + 1);
        assertEquals(ApplicationStatus.APPROVED, app.getStatus());
        assertEquals(2, app.getHistory().size());
        assertThrows(IllegalStateException.class, () -> app.reject(manilaReviewer, "Changed my mind", NOW + 2));
    }

    @Test
    public void onlyLguOfTheSameCityCanReview() throws Exception {
        LigtasApplication app = pending();
        LguProfile otherCity = new LguProfile("lgu-2", "Other", "", MetroManilaCity.PASIG, "Officer");
        ResidentProfile resident = new ResidentProfile("resident-1", "Maria", "", MetroManilaCity.MANILA, NOW);
        assertThrows(IllegalStateException.class, () -> app.approve(otherCity, "Looks fine to me", NOW));
        assertThrows(IllegalStateException.class, () -> app.approve(resident, "Approving myself", NOW));
        assertEquals(ApplicationStatus.PENDING, app.getStatus());
    }

    @Test
    public void qrContainsOnlyTheOpaqueId() throws Exception {
        LigtasApplication app = pending();
        assertThrows(IllegalStateException.class, app::getQrPayload);
        app.approve(manilaReviewer, "Approved for the demo.", NOW);
        String qr = app.getQrPayload();
        assertEquals("RUTARESCUE:LIGTAS:AbCdEf1234567890", qr);
        assertFalse(qr.contains("Pedro"));
        assertFalse(qr.contains("medication"));
        assertEquals("AbCdEf1234567890", LigtasApplication.parseQrPayload(qr));
        assertNull(LigtasApplication.parseQrPayload("https://example.com/whatever"));
    }

    @Test
    public void submissionRequiresProofAndJustification() {
        assertThrows(ValidationException.class, () -> LigtasApplication.submit("id12345678", "r1",
                MetroManilaCity.MANILA, "Maria", "m1", "Pedro", NeedCategory.PERSON_WITH_DISABILITY, "short", "proof", NOW));
        assertThrows(ValidationException.class, () -> LigtasApplication.submit("id12345678", "r1",
                MetroManilaCity.MANILA, "Maria", "m1", "Pedro", NeedCategory.PERSON_WITH_DISABILITY,
                "A long enough justification text.", "", NOW));
    }
}
