package com.rutarescue.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.rutarescue.model.contact.Hotline;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the emergency hotlines bundled with the app. They come from the PLM SSC post of
 * 24 Jul 2024, with the labels corrected (the post printed them one line out of step).
 * File shape: {"source": "...", "hotlines": [{"agency": "...", "numbers": ["..."]}]}
 */
public final class HotlineReader {

    public static final String ASSET = "seed/hotlines_plmssc_2024.json";

    /** Matches the JSON file; Gson fills these fields. */
    private static final class FileShape {
        List<Entry> hotlines;
    }

    private static final class Entry {
        String agency;
        List<String> numbers;
    }

    /** Returns the valid entries in file order; an entry without a name or number is skipped. */
    public List<Hotline> read(Reader reader) throws IOException {
        FileShape file;
        try {
            file = new Gson().fromJson(reader, FileShape.class);
        } catch (JsonParseException badJson) {
            throw new IOException("The hotline file is not valid JSON.", badJson);
        }
        List<Hotline> hotlines = new ArrayList<>();
        if (file == null || file.hotlines == null) return hotlines;
        for (Entry entry : file.hotlines) {
            try {
                hotlines.add(new Hotline(entry.agency, entry.numbers));
            } catch (IllegalArgumentException incomplete) {
                // skipped: nothing to call
            }
        }
        return hotlines;
    }
}
