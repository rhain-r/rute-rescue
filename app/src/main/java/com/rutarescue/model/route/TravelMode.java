package com.rutarescue.model.route;

/** Travel modes offered to residents (proposal + DOCX section 3). */
public enum TravelMode {
    WALKING("Walking", "pedestrian", false),
    MOTORCYCLE("Motorcycle", "motorcycle", true),
    VEHICLE("Vehicle", "car", true);

    private final String label;
    private final String tomTomValue;
    private final boolean usesTraffic;

    TravelMode(String label, String tomTomValue, boolean usesTraffic) {
        this.label = label;
        this.tomTomValue = tomTomValue;
        this.usesTraffic = usesTraffic;
    }

    public String getLabel() {
        return label;
    }

    /** Value of TomTom's travelMode parameter. */
    public String getTomTomValue() {
        return tomTomValue;
    }

    /** Walking routes do not use live road traffic. */
    public boolean usesTraffic() {
        return usesTraffic;
    }

    public static TravelMode fromName(String name) {
        if (name != null) {
            for (TravelMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name.strip())) return mode;
            }
        }
        return WALKING;
    }
}
