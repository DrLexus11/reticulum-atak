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
            return "mock".equals(t.mode) ? "Talk not live · mock" : "Talk not running · starting or off";
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

    /** The banner's first line, state first, in capitals as Columba's banner reads at arm's length. */
    static String title(TalkSnapshot t) {
        if (!t.available)
            return "TALK OFF";
        if (!t.running)
            return "mock".equals(t.mode) ? "VOICE: MOCK" : "TALK OFF";
        return t.transmitting ? "TRANSMITTING" : "LISTENING";
    }

    /** The banner's second line: where this phone stands with a relay, and the mode when not live. */
    static String subtitle(TalkSnapshot t) {
        if (!t.available)
            return "Not started in Columba";
        if (!t.running)
            return "mock".equals(t.mode) ? "Not transmitted" : "Starting, or off in Columba";
        StringBuilder line = new StringBuilder(t.status != null ? t.status : "");
        if (t.mode != null && !"live".equals(t.mode))
            line.append(line.length() > 0 ? " · " : "").append(t.mode);
        return line.toString();
    }

    /**
     * Whether to offer "Open Talk in Columba": talk is off, or Android refused to
     * keep it on behind ATAK. Opening Columba's talk screen fixes both.
     */
    static boolean offerOpen(TalkSnapshot t) {
        return !t.available || (!t.running && !"mock".equals(t.mode)) || t.micBlocked
                || (t.remote && !t.background && t.hint != null);
    }

    /** "A03": the key and its selector position, as Columba's dial shows it. */
    static String position(TalkSnapshot.Key k) {
        return String.format(java.util.Locale.ROOT, "%s%02d", k.key, k.position);
    }

    static String room(TalkSnapshot.Key k) {
        return k.room != null ? k.room : "No room";
    }

    /** A key's look, from its phase: the same states Columba's key colours. */
    enum Style { IDLE, RECEIVING, CALLING, TALKING, REFUSED }

    static Style style(TalkSnapshot.Key k) {
        if (k.talking())
            return Style.TALKING;
        if (k.waiting() || "same_as".equals(k.phase))
            return Style.CALLING;
        if ("refused".equals(k.phase))
            return Style.REFUSED;
        return k.floor != null ? Style.RECEIVING : Style.IDLE;
    }

    /** What a screen reader says for a key: the key, its channel and its state. */
    static String keyDescription(TalkSnapshot.Key k) {
        return "Push to talk " + k.key + ", " + k.label + ", " + k.line;
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
            case 10:
                return "Mic blocked · open Columba";
            case ColumbaMeshClient.NOT_SUPPORTED:
                return "This Columba has no talk";
            default:
                return "Code " + code;
        }
    }
}
