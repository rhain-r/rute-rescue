package com.rutarescue.model;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.ligtas.NeedCategory;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.model.user.ModeratorProfile;
import com.rutarescue.model.user.ResidentProfile;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class LigtasApplicationTest {

    private static final long NOW = 1_800_000_000_000L;

    private static LigtasApplication pending() throws ValidationException {
        return LigtasApplication.submit("AbCdEf1234567890", "resident-1", MetroManilaCity.MANILA, "Maria Santos",
                "m1", "Pedro Santos", NeedCategory.SENIOR_CITIZEN,
                "Senior resident who needs daily medication (synthetic demo).", "proof-ref", "maria@example.test", NOW);
    }

    private final ModeratorProfile manilaModerator =
            new ModeratorProfile("mod-1", "Reviewer", "mod@example.test", MetroManilaCity.MANILA, "DRRM Staff");

    @Test
    public void approvalNeedsReasonAndKeepsHistory() throws Exception {
        LigtasApplication app = pending();
        assertThrows(ValidationException.class, () -> app.approve(manilaModerator, " ", NOW));
        app.approve(manilaModerator, "Synthetic proof matches the senior category.", NOW + 1);
        assertEquals(ApplicationStatus.APPROVED, app.getStatus());
        assertEquals(2, app.getHistory().size());
        assertEquals("maria@example.test", app.getApplicantEmail());
    }

    @Test
    public void aDecisionCanBeChangedLaterWithAMessageToTheResident() throws Exception {
        LigtasApplication app = pending();
        app.approve(manilaModerator, "Approved for the demo.", NOW + 1);
        // Approved by mistake: the medical certificate is missing.
        app.decide(manilaModerator, ApplicationStatus.NEEDS_DOCUMENTS,
                "Please attach the medical certificate.", NOW + 2);
        assertEquals(ApplicationStatus.NEEDS_DOCUMENTS, app.getStatus());
        assertEquals("Please attach the medical certificate.", app.getReviewReason());
        assertEquals(3, app.getHistory().size());
        assertEquals(NOW + 2, app.getDecidedAt());
        assertThrows("a withdrawn approval has no QR", IllegalStateException.class, app::getQrPayload);
        assertThrows("a decision is never 'pending'", IllegalArgumentException.class,
                () -> app.decide(manilaModerator, ApplicationStatus.PENDING, "Back to the queue", NOW + 3));
    }

    @Test
    public void theResidentResubmitsAfterNeedsDocuments() throws Exception {
        LigtasApplication app = pending();
        assertThrows("only after documents were asked for", IllegalStateException.class,
                () -> app.resubmit("resident-1", "proof-2", NOW));
        app.askForDocuments(manilaModerator, "Please attach a clearer photo.", NOW + 1);
        assertThrows("only the owner", IllegalStateException.class, () -> app.resubmit("someone-else", "proof-2", NOW + 2));
        app.resubmit("resident-1", "proof-2", NOW + 2);
        assertEquals(ApplicationStatus.PENDING, app.getStatus());
        assertEquals("proof-2", app.getProofRef());
        assertEquals(3, app.getHistory().size());
        assertTrue(app.isWaitingLong(NOW + 2 + 25L * 60 * 60 * 1000));
    }

    @Test
    public void onlyTheModeratorOfTheSameCityCanReview() throws Exception {
        LigtasApplication app = pending();
        ModeratorProfile otherCity = new ModeratorProfile("mod-2", "Other", "", MetroManilaCity.PASIG, "Staff");
        LguProfile manilaLgu = new LguProfile("lgu-1", "Officer", "", MetroManilaCity.MANILA, "DRRM Officer");
        ResidentProfile resident = new ResidentProfile("resident-1", "Maria", "", MetroManilaCity.MANILA, NOW);
        assertThrows(IllegalStateException.class, () -> app.approve(otherCity, "Looks fine to me", NOW));
        assertThrows(IllegalStateException.class, () -> app.approve(manilaLgu, "The LGU finalizes, not decides", NOW));
        assertThrows(IllegalStateException.class, () -> app.approve(resident, "Approving myself", NOW));
        assertEquals(ApplicationStatus.PENDING, app.getStatus());
    }

    @Test
    public void qrContainsOnlyTheOpaqueId() throws Exception {
        LigtasApplication app = pending();
        assertThrows(IllegalStateException.class, app::getQrPayload);
        app.approve(manilaModerator, "Approved for the demo.", NOW);
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
                MetroManilaCity.MANILA, "Maria", "m1", "Pedro", NeedCategory.PERSON_WITH_DISABILITY, "short", "proof",
                "", NOW));
        assertThrows(ValidationException.class, () -> LigtasApplication.submit("id12345678", "r1",
                MetroManilaCity.MANILA, "Maria", "m1", "Pedro", NeedCategory.PERSON_WITH_DISABILITY,
                "A long enough justification text.", "", "", NOW));
    }
}
