package com.rutarescue.model.hazard;

/** Dataset versions are drafted, previewed and then published (DOCX section 3). */
public enum DatasetStatus {
    DRAFT("Draft"),
    PUBLISHED("Published"),
    PREPARED("Prepared (bundled)"),
    ARCHIVED("Archived");

    private final String label;

    DatasetStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static DatasetStatus fromName(String name) {
        if (name != null) {
            for (DatasetStatus status : values()) {
                if (status.name().equalsIgnoreCase(name.strip())) return status;
            }
        }
        return DRAFT;
    }
}
