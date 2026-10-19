package com.rutarescue.model.map;

import com.rutarescue.model.hazard.FloodScenario;

import java.util.EnumSet;

/**
 * What the map shows (Phase 1 "Map layers" sheet): one base map, any of the three NOAH flood
 * scenarios, and the evacuation-center, bridge/tunnel, traffic and train-line overlays.
 *
 * Routes are checked against the largest flood scenario that is switched on, so the map and
 * the route assessment never disagree; with every flood switched off they use the DOCX default
 * (5-year). encode()/decode() store the choices as one short line on the phone.
 */
public final class MapLayerSettings {

    private BaseMap baseMap = BaseMap.STANDARD;
    private final EnumSet<FloodScenario> floods = EnumSet.of(FloodScenario.DEFAULT);
    private boolean centersVisible = true;
    private boolean structuresVisible = true;
    private boolean trafficVisible = false;
    /** LRT-1, LRT-2 and MRT-3 lines and stations for train commuters (off until switched on). */
    private boolean transitVisible = false;

    public static MapLayerSettings defaults() {
        return new MapLayerSettings();
    }

    public MapLayerSettings copy() {
        return decode(encode());
    }

    /** The scenario used to assess routes: the largest flood switched on, else the 5-year default. */
    public FloodScenario getAssessmentScenario() {
        FloodScenario largest = null;
        for (FloodScenario scenario : floods) {
            if (largest == null || scenario.getReturnPeriodYears() > largest.getReturnPeriodYears()) largest = scenario;
        }
        return largest == null ? FloodScenario.DEFAULT : largest;
    }

    /** One line such as "base=STANDARD;floods=FIVE_YEAR,HUNDRED_YEAR;centers=1;structures=1;traffic=0;transit=0". */
    public String encode() {
        StringBuilder floodNames = new StringBuilder();
        for (FloodScenario scenario : floods) {
            if (floodNames.length() > 0) floodNames.append(',');
            floodNames.append(scenario.name());
        }
        return "base=" + baseMap.name() + ";floods=" + floodNames + ";centers=" + flag(centersVisible)
                + ";structures=" + flag(structuresVisible) + ";traffic=" + flag(trafficVisible)
                + ";transit=" + flag(transitVisible);
    }

    /** Reads a line written by encode(); anything damaged gives the defaults. */
    public static MapLayerSettings decode(String text) {
        MapLayerSettings settings = defaults();
        if (text == null || !text.contains("base=")) return settings;
        settings.floods.clear();
        for (String part : text.split(";")) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length != 2) continue;
            String value = keyValue[1];
            switch (keyValue[0]) {
                case "base":
                    settings.baseMap = BaseMap.fromName(value);
                    break;
                case "floods":
                    for (String name : value.split(",")) {
                        for (FloodScenario scenario : FloodScenario.values()) {
                            if (scenario.name().equals(name)) settings.floods.add(scenario);
                        }
                    }
                    break;
                case "centers":
                    settings.centersVisible = "1".equals(value);
                    break;
                case "structures":
                    settings.structuresVisible = "1".equals(value);
                    break;
                case "traffic":
                    settings.trafficVisible = "1".equals(value);
                    break;
                case "transit":
                    settings.transitVisible = "1".equals(value);
                    break;
                default:
                    break;
            }
        }
        return settings;
    }

    private static String flag(boolean value) {
        return value ? "1" : "0";
    }

    public BaseMap getBaseMap() { return baseMap; }
    public void setBaseMap(BaseMap baseMap) { this.baseMap = baseMap == null ? BaseMap.STANDARD : baseMap; }
    public boolean isFloodVisible(FloodScenario scenario) { return floods.contains(scenario); }
    public void setFloodVisible(FloodScenario scenario, boolean visible) {
        if (visible) floods.add(scenario); else floods.remove(scenario);
    }
    public boolean isCentersVisible() { return centersVisible; }
    public void setCentersVisible(boolean visible) { centersVisible = visible; }
    public boolean isStructuresVisible() { return structuresVisible; }
    public void setStructuresVisible(boolean visible) { structuresVisible = visible; }
    public boolean isTrafficVisible() { return trafficVisible; }
    public void setTrafficVisible(boolean visible) { trafficVisible = visible; }
    public boolean isTransitVisible() { return transitVisible; }
    public void setTransitVisible(boolean visible) { transitVisible = visible; }
}
