package com.rutarescue.validation;

import com.rutarescue.exception.InvalidFamilyRecordException;
import com.rutarescue.model.family.FamilyRecord;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Lab 7 cases T01-T11 and T16, now running against the Android project's validator. */
public class RecordValidatorTest {

    @Test
    public void t01_validInputIsNormalized() throws Exception {
        assertEquals("FR-0002", RecordValidator.familyId(" fr-0002 "));
        assertEquals("09171234567", RecordValidator.contactNumber(" 0917-123-4567 "));
        assertEquals(4, InputValidator.parseFamilySize(" 004 "));
        FamilyRecord record = new FamilyRecord(" fr-0002 ", "maria o'neill-santos", "0917-123-4567",
                "12 Sample St., Brgy. Malanday", "School Gym", 4);
        assertEquals("FR-0002|Maria O'Neill-Santos|09171234567|12 Sample St., Brgy. Malanday|School Gym|4",
                record.toFileString());
    }

    @Test
    public void t02_outerWhitespaceStrippedInternalSpacingKept() throws Exception {
        assertEquals("12  Sample St.", RecordValidator.address("  12  Sample St.  "));
    }

    @Test
    public void t03_idUsesLocaleRootUnderTurkishLocale() throws Exception {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("FI-0001", RecordValidator.familyId("fi-0001"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void t04_blankRequiredValuesAreRejected() {
        assertThrows(InvalidFamilyRecordException.class, () -> RecordValidator.headName("   "));
        assertThrows(InvalidFamilyRecordException.class, () -> RecordValidator.address(null));
        assertThrows(NumberFormatException.class, () -> InputValidator.parseFamilySize(" "));
    }

    @Test
    public void t05_familyIdRegexBoundaries() throws Exception {
        assertThrows(InvalidFamilyRecordException.class, () -> RecordValidator.familyId("FR0002"));
        assertThrows(InvalidFamilyRecordException.class, () -> RecordValidator.familyId("FR-002"));
        assertThrows(InvalidFamilyRecordException.class, () -> RecordValidator.familyId("FR-00002"));
        assertThrows(InvalidFamilyRecordException.class, () -> RecordValidator.familyId("FR-٠٠٠١"));
        assertEquals("A-0000", RecordValidator.familyId("A-0000"));
    }

    @Test
    public void t06_mobileNumberRules() throws Exception {
        assertEquals("09171234567", RecordValidator.contactNumber("0917 123 4567"));
        String[] invalid = {"08171234567", "0917123456", "091712345678", "0917123456a", "+639171234567"};
        for (String value : invalid) {
            assertThrows(value, InvalidFamilyRecordException.class, () -> RecordValidator.contactNumber(value));
        }
    }

    @Test
    public void t10_nameCharacterRule() throws Exception {
        InvalidFamilyRecordException letterless = assertThrows(InvalidFamilyRecordException.class,
                () -> RecordValidator.headName("123 ---"));
        assertTrue(letterless.getMessage().contains("at least one letter"));
        assertThrows(InvalidFamilyRecordException.class, () -> RecordValidator.headName("Rhian_123"));
        assertEquals("José Niño", RecordValidator.headName("josé niño"));
        assertEquals("Nguyễn Văn An", RecordValidator.headName("Nguyễn Văn An"));
        assertEquals("Maria O'Neill-Santos", RecordValidator.headName("MARIA O'NEILL-SANTOS"));
        assertEquals("Ma. Clara", RecordValidator.headName("ma. clara"));
        assertEquals("Juan Dela Cruz III", RecordValidator.headName("juan dela cruz 3"));
    }

    @Test
    public void t11_storageBreakingCharactersRejected() {
        String[] unsafe = {"12 Main | St", "12 Main\nSt", "12 Main\rSt", "12 Main\tSt", "12 Main St"};
        for (String value : unsafe) {
            assertThrows(InvalidFamilyRecordException.class, () -> RecordValidator.address(value));
        }
    }

    @Test
    public void t16_constructorRejectsSize101EvenWhenPromptsAreBypassed() {
        InvalidFamilyRecordException error = assertThrows(InvalidFamilyRecordException.class,
                () -> new FamilyRecord("FR-0001", "Ana Cruz", "09171234567", "1 Road", "Gym", 101));
        assertTrue(error.getMessage().contains("between 1 and 100"));
    }
}
