package com.rutarescue.model.advisory;

/** Alert level chosen by the LGU when publishing an advisory. */
public enum AlertLevel {
    ADVISORY("Advisory"),
    ALERT_1("Alert level 1"),
    ALERT_2("Alert level 2"),
    ALERT_3("Alert level 3");

    private final String label;

    AlertLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static AlertLevel fromName(String name) {
        if (name == null) return ADVISORY;
        for (AlertLevel level : values()) {
            if (level.name().equalsIgnoreCase(name.strip())) return level;
        }
        return ADVISORY;
    }
}
