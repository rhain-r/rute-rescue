package com.rutarescue.model;

import com.rutarescue.exception.InvalidFamilyRecordException;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.family.FamilyMember;
import com.rutarescue.model.family.FamilyRecord;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FamilyRecordTest {

    private static FamilyRecord imported() throws InvalidFamilyRecordException {
        return new FamilyRecord("FR-0001", "Maria Santos", "09171234567", "Riverside Zone 3", "San Isidro Court", 3);
    }

    @Test
    public void importedSizeIsFlaggedUntilMembersAreEntered() throws Exception {
        FamilyRecord record = imported();
        assertEquals(3, record.getFamilySize());
        assertTrue(record.needsMemberReview());

        record.replaceMembersAndContacts(Arrays.asList(
                new FamilyMember(null, "Maria Santos", 40, false, false, ""),
                new FamilyMember(null, "Ana Santos", 8, false, false, ""),
                new FamilyMember(null, "Pedro Santos", 67, false, false, "Needs medication")),
                Collections.emptyList(), "uid", 1L);
        assertEquals(3, record.getFamilySize());
        assertFalse(record.needsMemberReview());
        assertTrue(record.hasPriorityMember());
    }

    @Test
    public void failedUpdateChangesNothing() throws Exception {
        FamilyRecord record = imported();
        assertThrows(InvalidFamilyRecordException.class, () -> record.updateDetails("Maria Santos", "12345",
                "New Address", MetroManilaCity.MANILA, "New Center", "uid", 1L));
        assertEquals("Riverside Zone 3", record.getAddress());
        assertEquals("San Isidro Court", record.getPreferredCenter());
    }

    @Test
    public void childAndSeniorFlagsComeFromAge() throws Exception {
        FamilyMember child = new FamilyMember(null, "Ana", 8, false, false, "");
        FamilyMember senior = new FamilyMember(null, "Pedro", 67, false, false, "");
        assertTrue(child.isChild());
        assertTrue(senior.isSenior());
        assertEquals("Child • 8 years", child.describe());
    }
}
