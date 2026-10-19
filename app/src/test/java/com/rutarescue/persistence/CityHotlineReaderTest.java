package com.rutarescue.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.contact.Hotline;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/** Reads each city's own emergency hotline, bundled with the app. */
public class CityHotlineReaderTest {

    @Test
    public void readsOneCityEntry() throws IOException {
        String json = "{\"cities\":[{\"city\":\"MANILA\",\"agency\":\"Manila DRRM Office\",\"numbers\":[\"(02) 8527-0972\"]}]}";
        Map<MetroManilaCity, List<Hotline>> byCity = new CityHotlineReader().read(new StringReader(json));
        assertEquals(1, byCity.size());
        assertEquals("Manila DRRM Office", byCity.get(MetroManilaCity.MANILA).get(0).getAgency());
    }

    @Test
    public void skipsUnknownCitiesAndEntriesWithoutNumbers() throws IOException {
        String json = "{\"cities\":[{\"city\":\"CEBU\",\"agency\":\"Elsewhere\",\"numbers\":[\"911\"]},"
                + "{\"city\":\"PASIG\",\"agency\":\"Empty\",\"numbers\":[]}]}";
        assertTrue(new CityHotlineReader().read(new StringReader(json)).isEmpty());
    }

    @Test
    public void everyMetroManilaCityHasADialableHotline() throws IOException {
        File file = new File("src/main/assets/" + CityHotlineReader.ASSET);
        if (!file.exists()) file = new File("app/src/main/assets/" + CityHotlineReader.ASSET);
        Map<MetroManilaCity, List<Hotline>> byCity;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            byCity = new CityHotlineReader().read(reader);
        }
        for (MetroManilaCity city : MetroManilaCity.values()) {
            List<Hotline> hotlines = byCity.get(city);
            assertTrue(city + " has no hotline", hotlines != null && !hotlines.isEmpty());
            for (Hotline hotline : hotlines) {
                for (String number : hotline.getNumbers()) {
                    String digits = Hotline.dialDigits(number);
                    // short codes (122, 161, 168, 137-175) or full PH numbers (landline 02 + 8 digits, mobile 09 + 9 digits)
                    assertTrue(city + ": cannot dial " + number, digits.length() >= 3 && digits.length() <= 11);
                    assertFalse(city + ": " + number + " is a 7-digit landline without the 8 prefix",
                            digits.startsWith("02") && digits.length() != 10);
                }
            }
        }
        assertEquals("(02) 8527-0972", byCity.get(MetroManilaCity.MANILA).get(0).getNumbers().get(0));
        assertEquals("122", Hotline.dialDigits(byCity.get(MetroManilaCity.QUEZON_CITY).get(0).getNumbers().get(0)));
    }
}
