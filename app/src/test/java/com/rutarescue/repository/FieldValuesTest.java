package com.rutarescue.repository;

import com.rutarescue.repository.firestore.FieldValues;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Values typed by hand in the Firebase console can have the wrong type (for example a number
 * saved as text). Reading them must never crash the app.
 */
public class FieldValuesTest {

    @Test
    public void numbersReadAsNumbers() {
        assertEquals(1792067118670L, FieldValues.asLong(1792067118670L));
        assertEquals(12L, FieldValues.asLong(12.9));
        assertEquals(14.5899, FieldValues.asDouble(14.5899), 1e-9);
    }

    @Test
    public void aNumberSavedAsTextIsStillRead() {
        assertEquals(1792067118670L, FieldValues.asLong(" 1792067118670 "));
        assertEquals(14.5899, FieldValues.asDouble("14.5899"), 1e-9);
        assertTrue(FieldValues.asBool("TRUE"));
    }

    @Test
    public void anythingElseFallsBackInsteadOfCrashing() {
        assertEquals(0L, FieldValues.asLong("yesterday"));
        assertEquals(0L, FieldValues.asLong(null));
        assertEquals(0.0, FieldValues.asDouble(true), 0);
        assertFalse(FieldValues.asBool("yes please"));
        assertFalse(FieldValues.asBool(null));
    }

    @Test
    public void textIsNeverNull() {
        assertEquals("", FieldValues.asText(null));
        assertEquals("MANILA", FieldValues.asText("MANILA"));
        assertEquals("42", FieldValues.asText(42L));
    }
}
