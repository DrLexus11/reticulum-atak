package io.github.drlexus11.reticulumatak;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/** Agreement with Columba, against the shared fixture columba_mesh_v1.json. */
public class MeshSnapshotTest {
    private static JSONArray cases() throws Exception {
        try (InputStream in = MeshSnapshotTest.class.getClassLoader()
                .getResourceAsStream("columba_mesh_v1.json");
                Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
            return new JSONObject(scanner.useDelimiter("\\A").next()).getJSONArray("snapshots");
        }
    }

    @Test
    public void everyFixtureParsesAndSaysWhatItExpects() throws Exception {
        JSONArray cases = cases();
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            String name = c.getString("name");
            MeshSnapshot snapshot = MeshSnapshot.parse(c.getJSONObject("snapshot").toString());
            assertNotNull(name, snapshot);
            JSONObject expect = c.getJSONObject("expect");
            assertEquals(name, expect.getInt("peers"), snapshot.peers.size());
            assertEquals(name, expect.getString("propagation_line"), MeshLines.relay(snapshot));
            assertEquals(name, expect.getString("command_post_line"), MeshLines.commandPost(snapshot));
        }
    }

    @Test
    public void nullsStayNull() throws Exception {
        MeshSnapshot snapshot = MeshSnapshot.parse(
                cases().getJSONObject(0).getJSONObject("snapshot").toString());
        MeshSnapshot.Peer unreachable = snapshot.peers.get(2);
        assertNull(unreachable.hops);
        assertNull(unreachable.carrier);
        assertNull(unreachable.iface);
    }

    @Test
    public void anotherVersionOrGarbageIsRefused() throws Exception {
        JSONObject other = new JSONObject(cases().getJSONObject(0).getJSONObject("snapshot").toString());
        other.put("v", 2);
        assertNull(MeshSnapshot.parse(other.toString()));
        assertNull(MeshSnapshot.parse(""));
        assertNull(MeshSnapshot.parse(null));
        assertNull(MeshSnapshot.parse("{not json"));
    }

    @Test
    public void peerLines() throws Exception {
        MeshSnapshot snapshot = MeshSnapshot.parse(
                cases().getJSONObject(0).getJSONObject("snapshot").toString());
        long now = snapshot.at;
        // heard 50 s, 100 s and 10,000 s before "at"
        assertEquals("2 hops · LoRa · now", MeshLines.peer(snapshot.peers.get(0), now));
        assertEquals("1 hop · BLE · 1 min", MeshLines.peer(snapshot.peers.get(1), now));
        assertEquals("no path · 2 h", MeshLines.peer(snapshot.peers.get(2), now));
    }

    @Test
    public void everyAnnounceResultHasALine() {
        assertEquals("Announced", MeshLines.announce(0));
        assertEquals("Refused: ATAK control off in Columba", MeshLines.announce(2));
        assertEquals("Not sent: announced under a minute ago", MeshLines.announce(4));
        assertEquals("Not sent: code 9", MeshLines.announce(9));
    }
}
