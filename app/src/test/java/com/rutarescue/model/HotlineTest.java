package com.rutarescue.model;

import com.rutarescue.model.contact.Hotline;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/** Emergency hotlines: what the phone dials for each printed number. */
public class HotlineTest {

    @Test
    public void shortCodesAreDialledAsWritten() {
        assertEquals("911", Hotline.dialDigits("911"));
        assertEquals("143", Hotline.dialDigits("143"));
    }

    @Test
    public void aNumberRangeDialsItsFirstNumber() {
        assertEquals("0285278385", Hotline.dialDigits("(02) 8527-8385 to 95"));
    }

    @Test
    public void theLocalExtensionIsLeftForTheCaller() {
        assertEquals("0289115061", Hotline.dialDigits("(02) 8911-5061 to 65 loc. 100"));
    }

    @Test
    public void numbersAreJoinedForTheRow() {
        Hotline redCross = new Hotline("Philippine Red Cross", List.of("143", "(02) 8527-8385 to 95"));
        assertEquals("143 • (02) 8527-8385 to 95", redCross.getNumbersText());
    }

    @Test(expected = IllegalArgumentException.class)
    public void anAgencyNeedsAName() {
        new Hotline(" ", List.of("911"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void anAgencyNeedsANumber() {
        new Hotline("PAGASA", List.of());
    }
}
