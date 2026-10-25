package com.rutarescue.model;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.advisory.AlertLevel;
import com.rutarescue.model.advisory.CityAlert;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.model.user.ModeratorProfile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** 2026-10-22: only the city's LGU sets its alert level. */
public class CityAlertTest {

    private static final long NOW = 1_800_000_000_000L;
    private final LguProfile lgu = new LguProfile("lgu-1", "Ben Reyes", "", MetroManilaCity.MANILA, "DRRM Officer");
    private final LguProfile pasigLgu = new LguProfile("lgu-2", "Pasig", "", MetroManilaCity.PASIG, "Officer");
    private final ModeratorProfile moderator = new ModeratorProfile("mod-1", "Ana Cruz", "", MetroManilaCity.MANILA,
            "Staff", "c1", "Almario Elementary School");

    @Test
    public void aCityStartsWithNoLevelSet() {
        CityAlert alert = CityAlert.none(MetroManilaCity.MANILA);
        assertFalse(alert.isSet());
        assertEquals(AlertLevel.ADVISORY, alert.getLevel());
        assertEquals("MANILA", alert.getId());
        assertEquals("Advisory • Manila", alert.headline());
    }

    @Test
    public void theCitysLguSetsTheLevelWithANote() throws ValidationException {
        CityAlert alert = CityAlert.none(MetroManilaCity.MANILA);
        alert.set(lgu, AlertLevel.ALERT_2, "Pasig River at 15 m. Prepare to evacuate.", NOW);
        assertTrue(alert.isSet());
        assertEquals(AlertLevel.ALERT_2, alert.getLevel());
        assertEquals("Alert level 2 • Manila", alert.headline());
        assertEquals("Manila LGU", alert.getSetByName());
        assertEquals(NOW, alert.getSetAt());
    }

    @Test
    public void aLevelIsShownForOneWeek() throws ValidationException {
        long day = 24L * 60 * 60 * 1000;
        CityAlert alert = CityAlert.none(MetroManilaCity.MANILA);
        assertFalse("never set", alert.isCurrent(NOW));
        alert.set(lgu, AlertLevel.ALERT_2, "", NOW);
        assertTrue(alert.isCurrent(NOW + 6 * day));
        assertTrue(alert.isCurrent(NOW + 7 * day));
        assertEquals(AlertLevel.ALERT_2, alert.currentLevel(NOW + 7 * day));
        assertFalse("after a week the screens say no level is set", alert.isCurrent(NOW + 7 * day + 1));
        assertEquals("new announcements carry Advisory", AlertLevel.ADVISORY, alert.currentLevel(NOW + 8 * day));
        alert.set(lgu, AlertLevel.ALERT_1, "", NOW + 8 * day);
        assertTrue("a new level starts a new week", alert.isCurrent(NOW + 8 * day));
    }

    @Test
    public void nobodyElseSetsIt() {
        CityAlert alert = CityAlert.none(MetroManilaCity.MANILA);
        assertThrows(IllegalStateException.class, () -> alert.set(moderator, AlertLevel.ALERT_3, "", NOW));
        assertThrows(IllegalStateException.class, () -> alert.set(pasigLgu, AlertLevel.ALERT_3, "", NOW));
        assertThrows(ValidationException.class, () -> alert.set(lgu, null, "", NOW));
        assertFalse(alert.isSet());
    }
}
