package com.rutarescue.model.priority;

/** What the moderator found for one checklist item. */
public enum CheckResult {
    NOT_CHECKED("Not checked"),
    OK("OK"),
    MISSING("Missing");

    private final String label;

    CheckResult(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static CheckResult fromName(String name) {
        if (name == null) return NOT_CHECKED;
        for (CheckResult result : values()) {
            if (result.name().equalsIgnoreCase(name.strip())) return result;
        }
        return NOT_CHECKED;
    }
}
