package com.rutarescue.model.ligtas;

/** The kind of priority support a resident asks for. */
public enum NeedCategory {
    SENIOR_CITIZEN("Senior citizen"),
    PERSON_WITH_DISABILITY("Person with disability"),
    PREGNANT("Pregnant"),
    INFANT_OR_YOUNG_CHILD("Infant or young child"),
    MEDICAL_NEED("Medical need"),
    OTHER("Other support need");

    private final String label;

    NeedCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static NeedCategory fromName(String name) {
        if (name == null) return OTHER;
        for (NeedCategory category : values()) {
            if (category.name().equalsIgnoreCase(name.strip())) return category;
        }
        return OTHER;
    }

    @Override
    public String toString() {
        return label;
    }
}
