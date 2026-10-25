package com.rutarescue.model.priority;

import com.rutarescue.model.ligtas.NeedCategory;

/**
 * The priority groups that get a priority ID at registration (2026-10-22). Each group knows the
 * document and the detail a moderator checks at the evacuation center, so adding a group means
 * adding one constant here (the checklist screen and the rules need no other change).
 */
public enum PriorityGroup {
    SENIOR_CITIZEN("Senior citizen", NeedCategory.SENIOR_CITIZEN,
            new ChecklistItem("senior_id", "Senior citizen ID (OSCA), or a valid ID that shows age 60 or older",
                    ChecklistItem.Kind.DOCUMENT),
            new ChecklistItem("medicines", "Maintenance medicines written down (name and dose)", ChecklistItem.Kind.DETAIL)),
    PERSON_WITH_DISABILITY("Person with disability", NeedCategory.PERSON_WITH_DISABILITY,
            new ChecklistItem("pwd_id", "PWD ID, or a medical certificate of the disability", ChecklistItem.Kind.DOCUMENT),
            new ChecklistItem("assistance", "Help needed written down (wheelchair, sign language, a companion)",
                    ChecklistItem.Kind.DETAIL)),
    PREGNANT("Pregnant", NeedCategory.PREGNANT,
            new ChecklistItem("prenatal_record", "Prenatal record (mother and child booklet) or a medical certificate",
                    ChecklistItem.Kind.DOCUMENT),
            new ChecklistItem("due_date", "Months of pregnancy or due date", ChecklistItem.Kind.DETAIL));

    private final String label;
    private final NeedCategory needCategory;
    private final ChecklistItem document;
    private final ChecklistItem detail;

    PriorityGroup(String label, NeedCategory needCategory, ChecklistItem document, ChecklistItem detail) {
        this.label = label;
        this.needCategory = needCategory;
        this.document = document;
        this.detail = detail;
    }

    public String getLabel() {
        return label;
    }

    /** The matching Ligtas Priority category, so an application can start from the ID. */
    public NeedCategory getNeedCategory() {
        return needCategory;
    }

    /** The proof of belonging to this group. */
    public ChecklistItem getDocument() {
        return document;
    }

    /** The group-specific detail staff need to look after the person. */
    public ChecklistItem getDetail() {
        return detail;
    }

    /** Reads a stored group name; null when it is not a priority group. */
    public static PriorityGroup fromName(String name) {
        if (name == null) return null;
        for (PriorityGroup group : values()) {
            if (group.name().equalsIgnoreCase(name.strip())) return group;
        }
        return null;
    }

    @Override
    public String toString() {
        return label;
    }
}
