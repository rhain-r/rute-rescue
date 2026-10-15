package com.rutarescue.model;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.CapacityChange;
import com.rutarescue.model.center.CenterCapacity;
import com.rutarescue.model.center.CenterStatus;
import com.rutarescue.model.center.OccupancyLevel;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Capacity rules from the Phase 1 "Center capacity" screen (gauge colours, 120% limit, status). */
public class CenterCapacityTest {

    @Test
    public void gaugeIsGreenBelowSeventyPercent() throws ValidationException {
        assertEquals(OccupancyLevel.GREEN, new CenterCapacity(300, 209).getLevel());
    }

    @Test
    public void gaugeIsAmberFromSeventyToNinetyPercent() throws ValidationException {
        assertEquals(OccupancyLevel.AMBER, new CenterCapacity(300, 210).getLevel());
        assertEquals(OccupancyLevel.AMBER, new CenterCapacity(300, 270).getLevel());
    }

    @Test
    public void gaugeIsRedAboveNinetyPercent() throws ValidationException {
        assertEquals(OccupancyLevel.RED, new CenterCapacity(300, 271).getLevel());
    }

    @Test
    public void limitIsOneHundredTwentyPercentOfCapacity() throws ValidationException {
        assertEquals(144, new CenterCapacity(120, 0).getLimit());
        assertEquals(360, new CenterCapacity(300, 0).getLimit());
    }

    @Test
    public void headcountAtTheLimitIsAllowed() throws ValidationException {
        assertEquals(144, new CenterCapacity(120, 144).getCurrentOccupancy());
    }

    @Test
    public void headcountAboveTheLimitIsRejectedWithTheLimitInTheMessage() {
        try {
            new CenterCapacity(120, 150);
            fail("150 people is above 120% of 120");
        } catch (ValidationException expected) {
            assertEquals("occupancy", expected.getField());
            assertTrue(expected.getMessage().contains("144"));
        }
    }

    @Test(expected = ValidationException.class)
    public void negativeHeadcountIsRejected() throws ValidationException {
        new CenterCapacity(100, -1);
    }

    @Test(expected = ValidationException.class)
    public void headcountNeedsACapacityFirst() throws ValidationException {
        new CenterCapacity(0, 5);
    }

    @Test
    public void quickButtonsNeverGoBelowZero() throws ValidationException {
        assertEquals(0, new CenterCapacity(100, 3).adjustedBy(-5).getCurrentOccupancy());
    }

    @Test(expected = ValidationException.class)
    public void quickButtonsCannotPassTheLimit() throws ValidationException {
        new CenterCapacity(120, 140).adjustedBy(5);
    }

    @Test
    public void statusIsSuggestedFromTheHeadcount() throws ValidationException {
        assertEquals(CenterStatus.OPEN, new CenterCapacity(100, 69).suggestedStatus());
        assertEquals(CenterStatus.NEAR_CAPACITY, new CenterCapacity(100, 70).suggestedStatus());
        assertEquals(CenterStatus.NEAR_CAPACITY, new CenterCapacity(100, 99).suggestedStatus());
        assertEquals(CenterStatus.FULL, new CenterCapacity(100, 100).suggestedStatus());
        assertEquals(CenterStatus.FULL, new CenterCapacity(100, 110).suggestedStatus());
    }

    @Test
    public void percentIsRounded() throws ValidationException {
        assertEquals(71, new CenterCapacity(300, 212).getPercent());
        assertEquals(94, new CenterCapacity(120, 113).getPercent());
    }

    @Test
    public void changeRecordDescribesTheDeltaAndWho() {
        CapacityChange change = new CapacityChange(207, 212, "Manila DRRM Demo", 1_000L, "barangay field check");
        assertEquals(5, change.getDelta());
        assertEquals("+5 by Manila DRRM Demo", change.describe());
        assertEquals("-3 by Pasig DRRM", new CapacityChange(10, 7, "Pasig DRRM", 1L, "").describe());
    }
}
