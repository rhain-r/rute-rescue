package com.rutarescue.model.priority;

/**
 * One line of the priority ID checklist a moderator goes through at the evacuation center,
 * e.g. "PWD ID, or a medical certificate of the disability". Immutable.
 */
public final class ChecklistItem {

    /** A paper the person shows, or a detail written on their record. */
    public enum Kind {
        DOCUMENT("Document"),
        DETAIL("Detail");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final String key;
    private final String label;
    private final Kind kind;

    public ChecklistItem(String key, String label, Kind kind) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("A checklist item needs a key.");
        this.key = key;
        this.label = label == null ? key : label;
        this.kind = kind == null ? Kind.DETAIL : kind;
    }

    /** Stable name stored in the database (priorityIds/{id}.checks.{key}). */
    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public Kind getKind() {
        return kind;
    }
}
