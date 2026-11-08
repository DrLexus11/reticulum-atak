package io.github.drlexus11.reticulumatak;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Scanner;

/** EXPERIMENTAL. Agreement with Columba, against fixtures/columba_talk_v1.json. */
public class TalkLinesTest {
    private static JSONArray cases() throws Exception {
        try (InputStream in = TalkLinesTest.class.getClassLoader().getResourceAsStream("columba_talk_v1.json");
                Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
            return new JSONObject(scanner.useDelimiter("\\A").next()).getJSONArray("snapshots");
        }
    }

    @Test
    public void everyFixtureSaysWhatItExpects() throws Exception {
        JSONArray cases = cases();
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            String name = c.getString("name");
            TalkSnapshot t = TalkSnapshot.parse(c.getJSONObject("snapshot").toString());
            assertNotNull(name, t);
            JSONObject expect = c.getJSONObject("expect");
            assertEquals(name, expect.getString("header"), TalkLines.header(t));
            assertEquals(name, expect.isNull("note") ? null : expect.getString("note"), TalkLines.note(t));
            JSONObject keys = expect.getJSONObject("keys");
            assertEquals(name, keys.length(), t.keys.size());
            for (Iterator<String> it = keys.keys(); it.hasNext();) {
                String key = it.next();
                JSONObject k = keys.getJSONObject(key);
                TalkSnapshot.Key parsed = t.key(key);
                assertNotNull(name + " " + key, parsed);
                assertEquals(name + " " + key, k.getString("channel"), TalkLines.channel(parsed));
                assertEquals(name + " " + key, k.getString("floor"), TalkLines.floor(parsed));
                assertEquals(name + " " + key, k.getString("button"), parsed.line);
                assertEquals(name + " " + key, k.getString("last"), TalkLines.last(parsed));
            }
        }
    }

    @Test
    public void phasesSayWhetherTheKeyIsOnAir() throws Exception {
        TalkSnapshot t = TalkSnapshot.parse(cases().getJSONObject(0).getJSONObject("snapshot").toString());
        assertTrue(t.key("A").talking());
        assertFalse(t.key("B").talking());
        assertFalse(t.key("B").waiting());
        TalkSnapshot connecting = TalkSnapshot.parse(cases().getJSONObject(2).getJSONObject("snapshot").toString());
        assertTrue(connecting.key("B").waiting());
    }

    @Test
    public void anotherVersionOrNothingIsRefused() {
        assertNull(TalkSnapshot.parse(null));
        assertNull(TalkSnapshot.parse(""));
        assertNull(TalkSnapshot.parse("{\"v\":2,\"keys\":[]}"));
        assertNull(TalkSnapshot.parse("not json"));
    }

    @Test
    public void codesSayWhatWentWrong() {
        assertEquals("ATAK control off in Columba", TalkLines.result(2));
        assertEquals("Talk off · open Talk in Columba", TalkLines.result(8));
        assertEquals("This Columba has no talk", TalkLines.result(ColumbaMeshClient.NOT_SUPPORTED));
    }
}
