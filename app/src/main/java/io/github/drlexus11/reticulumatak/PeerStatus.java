package io.github.drlexus11.reticulumatak;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * How reachable a peer is over the mesh, for the list's dot and the map
 * overlay. Pure, so it is tested without ATAK.
 *
 * This is mesh reachability, not ATAK's "online": ATAK shows a contact online
 * from fresh positions (TAKDeliveryPlan, "Online in ATAK means a fresh
 * position"). Green here says the mesh can carry a message to them now.
 */
public final class PeerStatus {
    /** Heard within this, a peer with a path is green. */
    public static final long FRESH_MS = 15 * 60 * 1000L;

    public enum Level {
        /** A path, and heard recently: a message will go now. */
        REACHABLE,
        /** A path but not heard lately, or heard lately but no path yet. */
        STALE,
        /** No path and not heard lately. */
        UNREACHABLE,
    }

    private PeerStatus() {
    }

    public static Level of(MeshSnapshot.Peer peer, long now) {
        boolean fresh = now - peer.heard < FRESH_MS;
        if (peer.path && fresh)
            return Level.REACHABLE;
        if (peer.path || fresh)
            return Level.STALE;
        return Level.UNREACHABLE;
    }

    /** ARGB for a level: the colours ATAK operators read at a glance. */
    public static int color(Level level) {
        switch (level) {
            case REACHABLE:
                return 0xFF2ECC40; // green
            case STALE:
                return 0xFFFFB000; // amber
            default:
                return 0xFF8A8A8A; // grey
        }
    }

    /**
     * The level as a shape -- full, half, empty -- so the overlay reads without
     * colour: green and amber look alike to a colour-blind operator.
     */
    public static String glyph(Level level) {
        switch (level) {
            case REACHABLE:
                return "\u25CF"; // ●
            case STALE:
                return "\u25D0"; // ◐
            default:
                return "\u25CB"; // ○
        }
    }

    /**
     * Milliseconds until the next peer's freshness runs out, or -1 when none
     * will: a level changes with time alone, so the views are redrawn then,
     * not only when a snapshot arrives.
     */
    public static long nextChange(List<MeshSnapshot.Peer> peers, long now) {
        long next = -1;
        for (MeshSnapshot.Peer peer : peers) {
            long left = peer.heard + FRESH_MS - now;
            if (left > 0 && (next < 0 || left < next))
                next = left;
        }
        return next;
    }

    /**
     * The list's order: favourites first, then by reachability, then nearest
     * first, then most recently heard. Stable for equal peers.
     */
    public static List<MeshSnapshot.Peer> ordered(List<MeshSnapshot.Peer> peers,
            final Set<String> favourites, final long now) {
        List<MeshSnapshot.Peer> out = new ArrayList<>(peers);
        Collections.sort(out, new Comparator<MeshSnapshot.Peer>() {
            @Override
            public int compare(MeshSnapshot.Peer a, MeshSnapshot.Peer b) {
                boolean fa = favourites.contains(a.uid), fb = favourites.contains(b.uid);
                if (fa != fb)
                    return fa ? -1 : 1;
                int la = of(a, now).ordinal(), lb = of(b, now).ordinal();
                if (la != lb)
                    return Integer.compare(la, lb);
                int ha = a.hops == null ? Integer.MAX_VALUE : a.hops;
                int hb = b.hops == null ? Integer.MAX_VALUE : b.hops;
                if (ha != hb)
                    return Integer.compare(ha, hb);
                return Long.compare(b.heard, a.heard);
            }
        });
        return out;
    }

    /** The overlay's line for a favourite: "BRAVO-2  2 hops", or "  no path". */
    public static String overlayLine(MeshSnapshot.Peer peer) {
        String name = peer.callsign != null ? peer.callsign : peer.uid;
        if (!peer.path)
            return name + "  no path";
        if (peer.hops == null)
            return name;
        return name + "  " + peer.hops + (peer.hops == 1 ? " hop" : " hops");
    }
}
