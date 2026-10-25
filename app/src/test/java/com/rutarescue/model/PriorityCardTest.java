package com.rutarescue.model;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.priority.CheckResult;
import com.rutarescue.model.priority.ChecklistItem;
import com.rutarescue.model.priority.PriorityCard;
import com.rutarescue.model.priority.PriorityGroup;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.model.user.ModeratorProfile;
import com.rutarescue.model.user.ResidentProfile;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** 2026-10-22: the priority ID issued at registration, its QR, and the moderator's checklist. */
public class PriorityCardTest {

    private static final long NOW = 1_800_000_000_000L;
    private final ResidentProfile resident = new ResidentProfile("res-1", "Lola Santos", "", MetroManilaCity.MANILA, NOW);
    private final ModeratorProfile moderator = new ModeratorProfile("mod-1", "Ana Cruz", "", MetroManilaCity.PASIG,
            "Staff", "c1", "Pasig Sports Center");
    private final LguProfile lgu = new LguProfile("lgu-1", "Ben Reyes", "", MetroManilaCity.MANILA, "DRRM Officer");

    private PriorityCard card(PriorityGroup group) throws ValidationException {
        return PriorityCard.issue("ABCDEFGHJKLM", resident, group, NOW);
    }

    private static Map<String, CheckResult> allOk(PriorityCard card) {
        Map<String, CheckResult> results = new HashMap<>();
        for (ChecklistItem item : card.getChecklist()) results.put(item.getKey(), CheckResult.OK);
        return results;
    }

    @Test
    public void aNewIdIsUncheckedAndHoldsOnlyACodeInItsQr() throws ValidationException {
        PriorityCard card = card(PriorityGroup.SENIOR_CITIZEN);
        assertFalse(card.isChecked());
        assertEquals("Not checked yet", card.getStatusLabel());
        assertEquals("RUTARESCUE:PRIORITY:ABCDEFGHJKLM", card.getQrPayload());
        assertFalse("no name in the QR", card.getQrPayload().contains("Santos"));
        assertEquals("ABCD-EFGH-JKLM", card.getDisplayCode());
        assertEquals("res-1", card.getOwnerUid());
        assertEquals(MetroManilaCity.MANILA, card.getCity());
    }

    @Test
    public void newCodesAreTwelveEasyToReadLetters() {
        for (int i = 0; i < 50; i++) {
            String code = PriorityCard.newCode();
            assertEquals(12, code.length());
            assertTrue(code, code.matches("[A-HJ-NP-Z2-9]{12}"));
        }
    }

    @Test
    public void scannedOrTypedCodesAreRecognised() {
        assertEquals("ABCDEFGHJKLM", PriorityCard.parseQrPayload("RUTARESCUE:PRIORITY:ABCDEFGHJKLM"));
        assertEquals("typed with dashes and small letters", "ABCDEFGHJKLM", PriorityCard.parseQrPayload(" abcd-efgh-jklm "));
        assertNull("a Ligtas QR is not a priority ID", PriorityCard.parseQrPayload("RUTARESCUE:LIGTAS:AbCdEf1234567890"));
        assertNull("O and 0 are never used", PriorityCard.parseQrPayload("ABCDEFGHJKL0"));
        assertNull(PriorityCard.parseQrPayload("hello"));
        assertNull(PriorityCard.parseQrPayload(null));
    }

    @Test
    public void eachGroupHasItsOwnDocumentAndDetail() throws ValidationException {
        List<ChecklistItem> senior = card(PriorityGroup.SENIOR_CITIZEN).getChecklist();
        List<ChecklistItem> pwd = card(PriorityGroup.PERSON_WITH_DISABILITY).getChecklist();
        List<ChecklistItem> pregnant = card(PriorityGroup.PREGNANT).getChecklist();
        assertEquals(5, senior.size());
        assertEquals("valid_id", senior.get(0).getKey());
        assertEquals("senior_id", senior.get(1).getKey());
        assertEquals("pwd_id", pwd.get(1).getKey());
        assertEquals("prenatal_record", pregnant.get(1).getKey());
        assertEquals(ChecklistItem.Kind.DOCUMENT, pregnant.get(1).getKind());
        assertEquals(ChecklistItem.Kind.DETAIL, pregnant.get(4).getKind());
    }

    @Test
    public void aModeratorFlagsWhatIsMissing() throws ValidationException {
        PriorityCard card = card(PriorityGroup.PERSON_WITH_DISABILITY);
        Map<String, CheckResult> results = allOk(card);
        results.put("pwd_id", CheckResult.MISSING);
        results.put("emergency_contact", CheckResult.MISSING);
        card.recordCheck(moderator, results, "Bring your PWD ID tomorrow.", "Pasig Sports Center", NOW + 1);
        assertTrue(card.isChecked());
        assertEquals("2 items missing", card.getStatusLabel());
        assertEquals(2, card.getMissingItems().size());
        assertEquals("pwd_id", card.getMissingItems().get(0).getKey());
        assertEquals("Bring your PWD ID tomorrow.", card.getFlagNote());
        assertEquals("Ana Cruz", card.getCheckedByName());
        assertEquals("Pasig Sports Center", card.getCheckedAtCenter());

        card.recordCheck(moderator, allOk(card), "", "Pasig Sports Center", NOW + 2);
        assertEquals("Complete", card.getStatusLabel());
    }

    @Test
    public void everyItemMustBeMarkedAndOnlyModeratorsCheck() throws ValidationException {
        PriorityCard card = card(PriorityGroup.PREGNANT);
        Map<String, CheckResult> partly = allOk(card);
        partly.put("due_date", CheckResult.NOT_CHECKED);
        assertThrows(ValidationException.class, () -> card.recordCheck(moderator, partly, "", "", NOW));
        assertThrows("the LGU does not check IDs at the center", IllegalStateException.class,
                () -> card.recordCheck(lgu, allOk(card), "", "", NOW));
        assertFalse(card.isChecked());
    }

    @Test
    public void anIdNeedsAGroupAndACity() {
        ResidentProfile noCity = new ResidentProfile("res-2", "No City", "", null, NOW);
        assertThrows(ValidationException.class, () -> PriorityCard.issue("ABCDEFGHJKLM", resident, null, NOW));
        assertThrows(ValidationException.class, () -> PriorityCard.issue("ABCDEFGHJKLM", noCity, PriorityGroup.PREGNANT, NOW));
        assertEquals(PriorityGroup.SENIOR_CITIZEN, PriorityGroup.fromName("senior_citizen"));
        assertNull(PriorityGroup.fromName("INFANT"));
    }
}
