package io.github.drlexus11.reticulumatak;

import java.util.Locale;

/**
 * What the panel says, one short line each, state first (the programme's rule
 * for anything shown in ATAK). Pure, so the lines are tested against the
 * fixture's expectations without ATAK.
 */
public final class MeshLines {
    private MeshLines() {
    }

    /** "Command post: reachable · 2 hops · LoRa", "…: no path", "…: not heard". */
    public static String commandPost(MeshSnapshot snapshot) {
        MeshSnapshot.Peer best = null;
        boolean heard = false;
        for (MeshSnapshot.Peer peer : snapshot.peers) {
            if (!peer.isCommandPost())
                continue;
            heard = true;
            if (peer.path && (best == null || hops(peer.hops) < hops(best.hops)))
                best = peer;
        }
        if (best != null)
            return "Command post: reachable" + route(best.hops, best.carrier);
        return heard ? "Command post: no path" : "Command post: not heard";
    }

    /** "Relay: Command post · 2 hops · LoRa", "Relay: <name> · no path", "Relay: none". */
    public static String relay(MeshSnapshot snapshot) {
        MeshSnapshot.Propagation p = snapshot.propagation;
        if (p == null)
            return "Relay: none";
        String name = p.name != null ? p.name : p.hash.substring(0, Math.min(8, p.hash.length()));
        return "Relay: " + name + (p.path ? route(p.hops, p.carrier) : " · no path");
    }

    /** A peer's second line: "2 hops · LoRa · 3 min" or "no path · 2 h". */
    public static String peer(MeshSnapshot.Peer peer, long now) {
        String route;
        if (!peer.path) {
            route = "no path";
        } else {
            String known = route(peer.hops, peer.carrier);
            // route() leads with " · "; a path with nothing more known is just "path".
            route = known.isEmpty() ? "path" : known.substring(3);
        }
        return route + " · " + age(now - peer.heard);
    }

    /**
     * The node line: "Mesh: 3 peers", "Mesh: TAK endpoint off in Columba",
     * "Mesh: ATAK not linked to Columba" -- the last outranks the peer count,
     * because with no link ATAK shows none of those peers.
     */
    public static String node(MeshSnapshot snapshot, boolean atakLinked) {
        if (!snapshot.node.running)
            return "Mesh: TAK endpoint off in Columba";
        if (!atakLinked)
            return "Mesh: ATAK not linked to Columba";
        int n = snapshot.peers.size();
        return "Mesh: " + n + (n == 1 ? " peer" : " peers");
    }

    /** One line for an announce result code (ColumbaInterface.md). */
    public static String announce(int code) {
        switch (code) {
            case 0:
                return "Announced";
            case 1:
                return "Refused: Columba does not allow this ATAK";
            case 2:
                return "Refused: ATAK control off in Columba";
            case 3:
                return "Not sent: Columba not ready";
            case 4:
                return "Not sent: announced under a minute ago";
            default:
                return "Not sent: code " + code;
        }
    }

    public static String carrier(String carrier) {
        if (carrier == null)
            return null;
        switch (carrier) {
            case "lora":
                return "LoRa";
            case "ble":
                return "BLE";
            case "tcp":
                return "TCP";
            case "udp":
                return "UDP";
            case "auto":
                return "Auto";
            case "local":
                return "Local";
            default:
                return null; // "unknown" says nothing worth a word on the line
        }
    }

    /** " · 2 hops · LoRa", or less of it when less is known. */
    private static String route(Integer hops, String carrier) {
        StringBuilder line = new StringBuilder();
        if (hops != null)
            line.append(" · ").append(hops).append(hops == 1 ? " hop" : " hops");
        String c = carrier(carrier);
        if (c != null)
            line.append(" · ").append(c);
        return line.toString();
    }

    private static int hops(Integer hops) {
        return hops == null ? Integer.MAX_VALUE : hops;
    }

    static String age(long ms) {
        long s = Math.max(0, ms) / 1000;
        if (s < 60)
            return "now";
        if (s < 3600)
            return String.format(Locale.US, "%d min", s / 60);
        return String.format(Locale.US, "%d h", s / 3600);
    }
}
