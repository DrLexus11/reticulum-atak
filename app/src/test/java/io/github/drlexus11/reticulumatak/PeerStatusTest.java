package io.github.drlexus11.reticulumatak;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class PeerStatusTest {
    private static MeshSnapshot full() throws Exception {
        try (InputStream in = PeerStatusTest.class.getClassLoader().getResourceAsStream("columba_mesh_v1.json");
                Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
            JSONObject root = new JSONObject(scanner.useDelimiter("\\A").next());
            return MeshSnapshot.parse(root.getJSONArray("snapshots").getJSONObject(0).getJSONObject("snapshot").toString());
        }
    }

    @Test
    public void levelsFollowPathAndFreshness() throws Exception {
        MeshSnapshot s = full();
        long now = s.at;
        assertEquals(PeerStatus.Level.REACHABLE, PeerStatus.of(s.peers.get(0), now)); // path, heard 50 s ago
        assertEquals(PeerStatus.Level.REACHABLE, PeerStatus.of(s.peers.get(1), now)); // path, 100 s
        assertEquals(PeerStatus.Level.UNREACHABLE, PeerStatus.of(s.peers.get(2), now)); // no path, 2.7 h
        // A path but silent past the window: stale, not gone.
        assertEquals(PeerStatus.Level.STALE, PeerStatus.of(s.peers.get(0), now + PeerStatus.FRESH_MS + 60_000));
    }

    @Test
    public void favouritesComeFirstThenReachableNearestFirst() throws Exception {
        MeshSnapshot s = full();
        // HQ (2 hops), BRAVO-2 (1 hop), CHARLIE-3 (no path)
        List<MeshSnapshot.Peer> plain = PeerStatus.ordered(s.peers, Collections.<String>emptySet(), s.at);
        assertEquals("BRAVO-2", plain.get(0).callsign);
        assertEquals("HQ", plain.get(1).callsign);
        assertEquals("CHARLIE-3", plain.get(2).callsign);
        Set<String> fav = new HashSet<>();
        fav.add(s.peers.get(2).uid); // the unreachable one, favourited
        List<MeshSnapshot.Peer> withFav = PeerStatus.ordered(s.peers, fav, s.at);
        assertEquals("CHARLIE-3", withFav.get(0).callsign);
    }

    @Test
    public void overlayLines() throws Exception {
        MeshSnapshot s = full();
        assertEquals("HQ  2 hops", PeerStatus.overlayLine(s.peers.get(0)));
        assertEquals("BRAVO-2  1 hop", PeerStatus.overlayLine(s.peers.get(1)));
        assertEquals("CHARLIE-3  no path", PeerStatus.overlayLine(s.peers.get(2)));
    }
}
