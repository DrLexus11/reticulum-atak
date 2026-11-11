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
                assertEquals(name + " " + key, k.getBoolean("selectable"), TalkLines.selectable(parsed));
                assertEquals(name + " " + key, 12, parsed.channels.size());
                assertEquals(name + " " + key, k.getString("picked"), TalkLines.option(parsed, parsed.position));
                assertEquals(name + " " + key, k.getString("prev"), TalkLines.option(parsed, TalkLines.step(parsed, -1)));
                assertEquals(name + " " + key, k.getString("next"), TalkLines.option(parsed, TalkLines.step(parsed, 1)));
            }
        }
    }

    @Test
    public void phasesSayWhetherTheKeyIsOnAir() throws Exception {
        TalkSnapshot t = fixture(0);
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

    private static TalkSnapshot fixture(int i) throws Exception {
        return TalkSnapshot.parse(cases().getJSONObject(i).getJSONObject("snapshot").toString());
    }

    @Test
    public void theBannerSaysTheStateFirst() throws Exception {
        assertEquals("TRANSMITTING", TalkLines.title(fixture(0)));
        assertEquals("Relay · this phone", TalkLines.subtitle(fixture(0)));
        assertEquals("TALK OFF", TalkLines.title(fixture(1)));
        assertEquals("LISTENING", TalkLines.title(fixture(2)));
        assertEquals("loopback", TalkLines.subtitle(fixture(2)));
        assertEquals("VOICE: MOCK", TalkLines.title(fixture(3)));
    }

    @Test
    public void openColumbaIsOfferedWhenTalkIsOffOrRefusedBehindAtak() throws Exception {
        assertFalse(TalkLines.offerOpen(fixture(0)));
        assertTrue(TalkLines.offerOpen(fixture(1)));   // no talk yet
        assertTrue(TalkLines.offerOpen(fixture(2)));   // background refused
        assertFalse(TalkLines.offerOpen(fixture(3)));  // the mock, on purpose
        assertTrue(TalkLines.offerOpen(fixture(4)));   // microphone blocked
    }

    @Test
    public void aBlockedMicrophoneIsSaidAndOlderSnapshotsAreNotBlocked() throws Exception {
        assertTrue(fixture(4).micBlocked);
        assertEquals("Mic blocked · open Columba", TalkLines.note(fixture(4)));
        assertEquals("Mic blocked · open Columba", TalkLines.result(10));
        assertFalse(TalkSnapshot.parse("{\"v\":1,\"available\":true,\"running\":true,\"mode\":\"live\","
                + "\"status\":null,\"transmitting\":false,\"remote\":false,\"background\":false,"
                + "\"hint\":null,\"keys\":[]}").micBlocked);
    }

    @Test
    public void keysTakeColumbasStates() throws Exception {
        TalkSnapshot talking = fixture(0);
        assertEquals(TalkLines.Style.TALKING, TalkLines.style(talking.key("A")));
        assertEquals(TalkLines.Style.RECEIVING, TalkLines.style(talking.key("B")));
        TalkSnapshot refused = fixture(2);
        assertEquals(TalkLines.Style.IDLE, TalkLines.style(refused.key("A")));
        assertEquals(TalkLines.Style.CALLING, TalkLines.style(refused.key("B")));
        assertEquals("A03", TalkLines.position(talking.key("A")));
        assertEquals("B12", TalkLines.position(refused.key("B")));
        assertEquals("No room", TalkLines.room(talking.key("B")));
        assertEquals("Push to talk A, Search 1, Talk · 0:04/0:30 · 2/3", TalkLines.keyDescription(talking.key("A")));
    }

    @Test
    public void codesSayWhatWentWrong() {
        assertEquals("ATAK control off in Columba", TalkLines.result(2));
        assertEquals("Talk off · open Talk in Columba", TalkLines.result(8));
        assertEquals("This Columba has no talk", TalkLines.result(ColumbaMeshClient.NOT_SUPPORTED));
    }

    @Test
    public void theSelectorWrapsRoundAsColumbasEncoderDoes() throws Exception {
        TalkSnapshot refused = fixture(2);
        TalkSnapshot.Key b = refused.key("B");      // B12
        assertEquals(1, TalkLines.step(b, 1));
        assertEquals(11, TalkLines.step(b, -1));
        TalkSnapshot.Key a = refused.key("A");      // A1
        assertEquals(12, TalkLines.step(a, -1));
        assertEquals(3, TalkLines.step(a, 2));
        assertEquals(12, TalkLines.options(a).length);
        assertEquals("01 Command", TalkLines.options(a)[0]);
    }

    @Test
    public void aSnapshotWithoutChannelsOffersNoSelector() {
        TalkSnapshot old = TalkSnapshot.parse("{\"v\":1,\"available\":true,\"running\":true,\"mode\":\"live\","
                + "\"status\":null,\"transmitting\":false,\"remote\":false,\"background\":false,\"hint\":null,"
                + "\"keys\":[{\"key\":\"A\",\"position\":2,\"label\":\"Medical\",\"room\":null,\"phase\":\"idle\","
                + "\"line\":\"Hold to talk\",\"floor\":null,\"last\":null}]}");
        assertNotNull(old);
        assertFalse(old.key("A").locked);
        assertTrue(old.key("A").channels.isEmpty());
        assertFalse(TalkLines.selectable(old.key("A")));
    }

    @Test
    public void aDeviceHeldSelectorIsSaid() throws Exception {
        TalkSnapshot shared = fixture(5);
        assertTrue(shared.key("A").locked);
        assertEquals("Selector on PTT device", TalkLines.result(11));
        // A shared bank: both keys list the same channels.
        assertEquals(TalkLines.option(shared.key("A"), 4), TalkLines.option(shared.key("B"), 4));
    }
}
