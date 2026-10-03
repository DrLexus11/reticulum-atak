package io.github.drlexus11.reticulumatak;

/**
 * What the Interfaces page says, one short line each, state first. Pure, so it
 * is tested against fixtures/columba_mesh_interfaces.json.
 */
public final class InterfaceLines {
    private InterfaceLines() {
    }

    /** The page's top line: how switches apply here. */
    public static String header(MeshSnapshot snapshot) {
        if (snapshot.interfacesLive)
            return "Interfaces: switches apply at once";
        int pending = 0;
        for (MeshSnapshot.Iface f : snapshot.interfaces)
            if (f.pending)
                pending++;
        if (snapshot.interfacesPending)
            return "Interfaces: " + Math.max(pending, 1) + (pending > 1 ? " changes" : " change")
                    + " pending · Apply restarts the mesh here";
        return "Interfaces: switches apply on Apply";
    }

    /**
     * Apply is offered only when it has something to do: a staging backend with
     * changes waiting, a running node, and ATAK control on in Columba.
     */
    public static boolean canApply(MeshSnapshot snapshot) {
        return snapshot != null && !snapshot.interfacesLive && snapshot.interfacesPending
                && snapshot.node.control && snapshot.node.running;
    }

    /**
     * A row: "Board TCP · TCP · up · carries command post", "… · down: Bluetooth
     * is off", "… · off (pending)", "… · on (pending)". Pending is Columba's
     * word, not guessed here: an interface on but not running may be staged, or
     * left out by the transport, or failed to start.
     */
    public static String row(MeshSnapshot.Iface f, boolean live) {
        StringBuilder line = new StringBuilder(f.name);
        String carrier = MeshLines.carrier(f.carrier);
        if (carrier != null)
            line.append(" · ").append(carrier);
        boolean staged = f.pending && !live;
        if (!f.enabled) {
            line.append(staged ? " · off (pending)" : " · off");
        } else if (f.online) {
            line.append(" · up");
            if (f.carriesCommandPost)
                line.append(" · carries command post");
        } else if (f.reason != null) {
            line.append(" · down: ").append(f.reason);
        } else {
            line.append(staged ? " · on (pending)" : " · down");
        }
        return line.toString();
    }

    /** One line for a switch or apply result code (ColumbaInterface.md). */
    public static String result(int code) {
        switch (code) {
            case 0:
                return "Done";
            case 1:
                return "Refused: Columba does not allow this ATAK";
            case 2:
                return "Refused: ATAK control off in Columba";
            case 3:
                return "Not done: Columba not ready";
            case 5:
                return "Refused: it would cut this phone off";
            case 6:
                return "Not done: no such interface";
            case 7:
                return "Staged: Apply to put it into effect";
            case ColumbaMeshClient.NOT_SUPPORTED:
                return "Not done: this Columba cannot switch interfaces";
            default:
                return "Not done: code " + code;
        }
    }
}
