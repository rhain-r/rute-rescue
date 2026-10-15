package com.rutarescue.persistence;

import com.rutarescue.model.contact.Hotline;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** Reads the hotline list (PLM SSC post, labels corrected) bundled with the app. */
public class HotlineReaderTest {

    @Test
    public void readsAgencyAndNumbers() throws IOException {
        String json = "{\"source\":\"test\",\"hotlines\":[{\"agency\":\"PAGASA\",\"numbers\":[\"(02) 8284-0800\"]}]}";
        List<Hotline> hotlines = new HotlineReader().read(new StringReader(json));
        assertEquals(1, hotlines.size());
        assertEquals("PAGASA", hotlines.get(0).getAgency());
        assertEquals("(02) 8284-0800", hotlines.get(0).getNumbers().get(0));
    }

    @Test
    public void skipsAnEntryWithoutNumbers() throws IOException {
        String json = "{\"hotlines\":[{\"agency\":\"Empty\",\"numbers\":[]},{\"agency\":\"PNP\",\"numbers\":[\"117\"]}]}";
        List<Hotline> hotlines = new HotlineReader().read(new StringReader(json));
        assertEquals(1, hotlines.size());
        assertEquals("PNP", hotlines.get(0).getAgency());
    }

    @Test
    public void readsTheBundledHotlines() throws IOException {
        File file = new File("src/main/assets/" + HotlineReader.ASSET);
        if (!file.exists()) file = new File("app/src/main/assets/" + HotlineReader.ASSET);
        List<Hotline> hotlines;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            hotlines = new HotlineReader().read(reader);
        }
        assertEquals(9, hotlines.size());
        assertEquals("National Emergency Hotline", hotlines.get(0).getAgency());
        assertEquals("911", hotlines.get(0).getNumbers().get(0));
    }
}
