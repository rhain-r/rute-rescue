package com.rutarescue.model.route;

/**
 * One line in the Phase 1 warning drawer, e.g. "High flood hazard on Recto Ave" /
 * "350 m in NOAH high hazard (over 1.5 m deep)". Immutable.
 *
 * Serious warnings (a closure or high flood hazard) change "Start guidance" to "Start anyway".
 */
public final class RouteWarning {

    /** Declared in the order the drawer lists them. */
    public enum Kind {
        CLOSURE(true),
        HIGH_FLOOD(true),
        MEDIUM_FLOOD(false),
        LOW_FLOOD(false),
        UNVERIFIED_STRUCTURE(false),
        OUTSIDE_FLOOD_MAP(false),
        TRAFFIC(false);

        private final boolean serious;

        Kind(boolean serious) {
            this.serious = serious;
        }

        public boolean isSerious() {
            return serious;
        }
    }

    private final Kind kind;
    private final String title;
    private final String detail;

    public RouteWarning(Kind kind, String title, String detail) {
        this.kind = kind;
        this.title = title;
        this.detail = detail;
    }

    public Kind getKind() { return kind; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
    public boolean isSerious() { return kind.isSerious(); }
}
