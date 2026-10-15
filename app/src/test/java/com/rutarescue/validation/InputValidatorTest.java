package com.rutarescue.validation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Lab 6 input-parsing cases plus Lab 7 T07-T09. */
public class InputValidatorTest {

    @Test
    public void validPositiveInteger() {
        assertEquals(4, InputValidator.parsePositiveInteger("4"));
    }

    @Test
    public void nonNumericIsNumberFormatException() {
        assertThrows(NumberFormatException.class, () -> InputValidator.parsePositiveInteger("four"));
    }

    @Test
    public void zeroIsADomainErrorNotAFormatError() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.parsePositiveInteger("0"));
        // Lab 6 keeps the two cases apart: this must NOT be a NumberFormatException.
        assertSame(IllegalArgumentException.class, error.getClass());
    }

    @Test
    public void t07_countSyntaxRejectsSignsDecimalsAndForeignDigits() {
        String[] invalid = {"four", "4.0", "+4", "1e1", "٤"};
        for (String value : invalid) {
            NumberFormatException error = assertThrows(NumberFormatException.class,
                    () -> InputValidator.parseFamilySize(value));
            assertTrue(error.getMessage().contains("digits 0-9"));
        }
    }

    @Test
    public void t08_overflowIsReportedAndRecoverable() {
        NumberFormatException error = assertThrows(NumberFormatException.class,
                () -> InputValidator.parseFamilySize("99999999999999999999"));
        assertTrue(error.getMessage().contains("too large"));
        assertEquals(4, InputValidator.parseFamilySize("4"));
    }

    @Test
    public void t09_familySizeBoundaries() {
        assertEquals(1, InputValidator.parseFamilySize("1"));
        assertEquals(100, InputValidator.parseFamilySize("100"));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.parseFamilySize("0"));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.parseFamilySize("101"));
    }

    @Test
    public void ageRange() {
        assertEquals(0, InputValidator.parseAge("0"));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.parseAge("121"));
    }
}
