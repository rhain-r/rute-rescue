package com.rutarescue.model.common;

/**
 * How current a piece of information is (DOCX sections 4 and 11). The UI shows this
 * label next to every decision-relevant value so cached data is never presented as live.
 */
public enum DataFreshness {
    LIVE("Live", "Just received from the server."),
    CACHED("Cached", "Saved on this phone from the last sync."),
    STALE("Stale", "Last synced a while ago - check for newer information."),
    PREPARED("Prepared", "Prepared dataset bundled with the app - not live conditions.");

    private final String label;
    private final String description;

    DataFreshness(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
