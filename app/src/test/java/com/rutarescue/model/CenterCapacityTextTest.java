package com.rutarescue.model;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.CenterCapacity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** The headcount text residents see on a center's details page and in center rows. */
public class CenterCapacityTextTest {

    @Test
    public void aPostedHeadcountShowsCountCapacityAndPercent() throws ValidationException {
        CenterCapacity capacity = new CenterCapacity(120, 113);
        assertEquals("113 of 120 people · 94%", capacity.summary());
        assertEquals("113/120 people", capacity.shortSummary());
    }

    @Test
    public void overflowAbove100PercentIsShownAsIs() throws ValidationException {
        assertEquals("130 of 120 people · 108%", new CenterCapacity(120, 130).summary());
    }

    @Test
    public void anUnsetCapacityHasNoNumbers() {
        assertEquals("Headcount not posted yet", CenterCapacity.notSet().summary());
        assertEquals("", CenterCapacity.notSet().shortSummary());
    }
}
