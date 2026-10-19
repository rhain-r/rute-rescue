package com.rutarescue.model;

import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.map.BaseMap;
import com.rutarescue.model.map.MapLayerSettings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The Phase 1 "Map layers" sheet choices, how routes pick a scenario, and saving them. */
public class MapLayerSettingsTest {

    @Test
    public void defaultsMatchTheSheetDesign() {
        MapLayerSettings settings = MapLayerSettings.defaults();
        assertEquals(BaseMap.STANDARD, settings.getBaseMap());
        assertTrue(settings.isFloodVisible(FloodScenario.FIVE_YEAR));
        assertFalse(settings.isFloodVisible(FloodScenario.TWENTY_FIVE_YEAR));
        assertFalse(settings.isFloodVisible(FloodScenario.HUNDRED_YEAR));
        assertTrue(settings.isCentersVisible());
        assertTrue(settings.isStructuresVisible());
        assertFalse(settings.isTrafficVisible());
        assertFalse("Train lines start off so the map stays clean", settings.isTransitVisible());
    }

    @Test
    public void routesUseTheLargestFloodThatIsSwitchedOn() {
        MapLayerSettings settings = MapLayerSettings.defaults();
        settings.setFloodVisible(FloodScenario.HUNDRED_YEAR, true);
        assertEquals(FloodScenario.HUNDRED_YEAR, settings.getAssessmentScenario());
    }

    @Test
    public void routesFallBackToTheFiveYearFloodWhenAllAreOff() {
        MapLayerSettings settings = MapLayerSettings.defaults();
        settings.setFloodVisible(FloodScenario.FIVE_YEAR, false);
        assertEquals(FloodScenario.FIVE_YEAR, settings.getAssessmentScenario());
    }

    @Test
    public void choicesSurviveSavingAndLoading() {
        MapLayerSettings settings = MapLayerSettings.defaults();
        settings.setBaseMap(BaseMap.TOPOGRAPHIC);
        settings.setFloodVisible(FloodScenario.TWENTY_FIVE_YEAR, true);
        settings.setCentersVisible(false);
        settings.setTrafficVisible(true);
        MapLayerSettings loaded = MapLayerSettings.decode(settings.encode());
        assertEquals(BaseMap.TOPOGRAPHIC, loaded.getBaseMap());
        assertTrue(loaded.isFloodVisible(FloodScenario.TWENTY_FIVE_YEAR));
        assertFalse(loaded.isCentersVisible());
        assertTrue(loaded.isTrafficVisible());
    }

    @Test
    public void trainLinesSurviveSavingAndLoading() {
        MapLayerSettings settings = MapLayerSettings.defaults();
        settings.setTransitVisible(true);
        assertTrue(MapLayerSettings.decode(settings.encode()).isTransitVisible());
    }

    @Test
    public void choicesSavedBeforeTrainLinesExistedStillLoad() {
        MapLayerSettings old = MapLayerSettings.decode("base=SATELLITE;floods=FIVE_YEAR;centers=1;structures=0;traffic=1");
        assertEquals(BaseMap.SATELLITE, old.getBaseMap());
        assertFalse(old.isStructuresVisible());
        assertFalse(old.isTransitVisible());
    }

    @Test
    public void damagedSavedTextGivesTheDefaults() {
        assertEquals(BaseMap.STANDARD, MapLayerSettings.decode("not-valid").getBaseMap());
    }
}
