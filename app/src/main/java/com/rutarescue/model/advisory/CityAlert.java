package com.rutarescue.model.advisory;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.AuditableEntity;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.validation.TextRules;

/**
 * The alert level of one city (2026-10-22), e.g. "Alert level 2 in Manila". Only that city's LGU
 * sets it; every account of the city sees it at the top of its first screen, and advisories
 * published while it is in force carry the same level. Stored as cityAlerts/{CITY CODE}.
 *
 * Encapsulation: the level only changes through set(), which checks who is setting it.
 */
public class CityAlert extends AuditableEntity {

    private final MetroManilaCity city;
    private AlertLevel level;
    private String note;
    private String setByName;
    private long setAt;

    public CityAlert(MetroManilaCity city, AlertLevel level, String note, String setByName, long setAt) {
        super(city == null ? null : city.code(), null, false);
        if (city == null) throw new IllegalArgumentException("A city alert needs its city.");
        this.city = city;
        this.level = level == null ? AlertLevel.ADVISORY : level;
        this.note = note == null ? "" : note.strip();
        this.setByName = setByName == null ? "" : setByName.strip();
        this.setAt = setAt;
    }

    /** No level set yet: residents see "Advisory" (normal conditions). */
    public static CityAlert none(MetroManilaCity city) {
        return new CityAlert(city, AlertLevel.ADVISORY, "", "", 0);
    }

    @Override
    public String getEntityType() {
        return "cityAlert";
    }

    /** The city's LGU sets a new level, with an optional short note for residents (up to 200 characters). */
    public void set(StaffProfile lgu, AlertLevel newLevel, String newNote, long now) throws ValidationException {
        if (lgu == null || !lgu.canSetAlertLevel() || !lgu.canManage(city)) {
            throw new IllegalStateException("Only the " + city.getDisplayName() + " LGU sets its alert level.");
        }
        if (newLevel == null) throw new ValidationException("level", "Choose an alert level.");
        String checkedNote = TextRules.optionalText("note", newNote);
        TextRules.checkLength("note", checkedNote, 0, 200);
        level = newLevel;
        note = checkedNote;
        setByName = lgu.getJurisdiction().getDisplayName() + " LGU";
        setAt = now;
        markUpdated(lgu.getUid(), now);
    }

    /** True once the LGU has set a level at least once. */
    public boolean isSet() {
        return setAt > 0;
    }

    /** A level is shown for one week after the LGU set it (2026-10-25). */
    public static final int CURRENT_DAYS = 7;

    /**
     * True when the LGU set the level within the last 7 days. After that every screen says
     * "No alert level set" again until the LGU sets a new one.
     */
    public boolean isCurrent(long now) {
        return isSet() && now - setAt <= CURRENT_DAYS * 24L * 60 * 60 * 1000;
    }

    /** The level in force now: the LGU's level for 7 days, then "Advisory" (normal conditions). */
    public AlertLevel currentLevel(long now) {
        return isCurrent(now) ? level : AlertLevel.ADVISORY;
    }

    /** "Alert level 2 • Manila", for the top of the screens. */
    public String headline() {
        return level.getLabel() + " • " + city.getDisplayName();
    }

    public MetroManilaCity getCity() { return city; }
    public AlertLevel getLevel() { return level; }
    public String getNote() { return note; }
    public String getSetByName() { return setByName; }
    public long getSetAt() { return setAt; }
}
