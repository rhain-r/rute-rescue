package com.rutarescue.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.contact.Hotline;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Reads each Metro Manila city's own emergency hotline, bundled with the app. Home shows the
 * resident's sign-up city first, above the national hotlines.
 * File shape: {"cities": [{"city": "MANILA", "agency": "...", "numbers": ["..."], "source": "..."}]}
 * The source and url of every entry are kept in the file so the numbers can be re-checked.
 */
public final class CityHotlineReader {

    public static final String ASSET = "seed/hotlines_city_2026.json";

    /** Matches the JSON file; Gson fills these fields. */
    private static final class FileShape {
        List<Entry> cities;
    }

    private static final class Entry {
        String city;
        String agency;
        List<String> numbers;
    }

    /**
     * Returns the hotlines of each city, in file order. An entry with an unknown city, or
     * without a name or number, is skipped.
     */
    public Map<MetroManilaCity, List<Hotline>> read(Reader reader) throws IOException {
        FileShape file;
        try {
            file = new Gson().fromJson(reader, FileShape.class);
        } catch (JsonParseException badJson) {
            throw new IOException("The city hotline file is not valid JSON.", badJson);
        }
        Map<MetroManilaCity, List<Hotline>> byCity = new EnumMap<>(MetroManilaCity.class);
        if (file == null || file.cities == null) return byCity;
        for (Entry entry : file.cities) {
            MetroManilaCity city = MetroManilaCity.fromCode(entry.city);
            if (city == null) continue;
            Hotline hotline;
            try {
                hotline = new Hotline(entry.agency, entry.numbers);
            } catch (IllegalArgumentException incomplete) {
                continue; // skipped: nothing to call
            }
            byCity.computeIfAbsent(city, ignored -> new ArrayList<>()).add(hotline);
        }
        return byCity;
    }
}
