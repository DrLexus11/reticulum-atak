package io.github.drlexus11.reticulumatak;

/**
 * EXPERIMENTAL. The PTT page's lines, one short line each, state first. Pure,
 * so fixtures/columba_talk_v1.json's "expect" block pins them.
 */
final class TalkLines {
    private TalkLines() {
    }

    /** The page's first line: listening or transmitting, and where this phone stands with a relay. */
    static String header(TalkSnapshot t) {
        if (!t.available)
            return t.hint != null ? t.hint : "Talk off in Columba";
        if (!t.running)
            return "Talk not live" + (t.mode != null ? " · " + t.mode : "");
        StringBuilder line = new StringBuilder(t.transmitting ? "Transmitting" : "Listening");
        if (t.status != null)
            line.append(" · ").append(t.status);
        if (t.mode != null && !"live".equals(t.mode))
            line.append(" · ").append(t.mode);
        return line.toString();
    }

    /** Columba's warning, when it has one and the header has not already said it. */
    static String note(TalkSnapshot t) {
        return t.available ? t.hint : null;
    }

    /** "A3 Search 1 · #search · 0a1b2c3d": the key, its selector position, the channel and its room. */
    static String channel(TalkSnapshot.Key k) {
        String line = k.key + k.position + " " + k.label;
        return k.room != null ? line + " · " + k.room : line;
    }

    static String floor(TalkSnapshot.Key k) {
        if (k.floor == null)
            return "Floor free";
        return "Floor: " + k.floor;
    }

    static String last(TalkSnapshot.Key k) {
        return k.last != null ? k.last : "";
    }

    /** What a result code from a talk command says, one line. */
    static String result(int code) {
        switch (code) {
            case 0:
                return "OK";
            case 1:
                return "Refused: this ATAK not allowed";
            case 2:
                return "ATAK control off in Columba";
            case 3:
                return "Columba not ready";
            case 8:
                return "Talk off · open Talk in Columba";
            case 9:
                return "Refused: bad request";
            case ColumbaMeshClient.NOT_SUPPORTED:
                return "This Columba has no talk";
            default:
                return "Code " + code;
        }
    }
}
