package io.github.drlexus11.reticulumatak;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * EXPERIMENTAL. Columba's talk snapshot, version 1 (docs/ColumbaInterface.md,
 * "Talk"), as the PTT page reads it. Pinned by fixtures/columba_talk_v1.json,
 * which Columba builds against too. The lines are Columba's own words: the
 * page shows them and guesses nothing.
 */
public final class TalkSnapshot {
    public static final int VERSION = 1;
    /** Key.floor when this phone has the floor. */
    public static final String YOU = "you";

    public static final class Key {
        /** "A" or "B". */
        public final String key;
        public final int position;
        public final String label;
        public final String room;
        /** idle, calling, connecting, ready, talking, same_as, refused. */
        public final String phase;
        /** What the key says on Columba's talk screen. */
        public final String line;
        public final String floor;
        public final String last;

        Key(String key, int position, String label, String room, String phase, String line,
                String floor, String last) {
            this.key = key;
            this.position = position;
            this.label = label;
            this.room = room;
            this.phase = phase;
            this.line = line;
            this.floor = floor;
            this.last = last;
        }

        public boolean talking() {
            return "talking".equals(phase);
        }

        /** Keyed and not yet on air: the rendezvous or the path is still running. */
        public boolean waiting() {
            return "calling".equals(phase) || "connecting".equals(phase) || "ready".equals(phase);
        }
    }

    /** A talk view model exists in Columba: there is talk to drive. */
    public final boolean available;
    public final boolean running;
    public final String mode;
    public final String status;
    public final boolean transmitting;
    /** Columba counts this remote as in use (a watcher, with ATAK control on). */
    public final boolean remote;
    /** Columba holds talk on in the background (its microphone foreground service runs). */
    public final boolean background;
    /**
     * Android silenced Columba's microphone (service refused, Columba behind ATAK):
     * Columba refuses presses until it is opened. Absent from older snapshots: false.
     */
    public final boolean micBlocked;
    public final String hint;
    public final List<Key> keys;

    private TalkSnapshot(boolean available, boolean running, String mode, String status,
            boolean transmitting, boolean remote, boolean background, boolean micBlocked, String hint,
            List<Key> keys) {
        this.available = available;
        this.running = running;
        this.mode = mode;
        this.status = status;
        this.transmitting = transmitting;
        this.remote = remote;
        this.background = background;
        this.micBlocked = micBlocked;
        this.hint = hint;
        this.keys = Collections.unmodifiableList(keys);
    }

    /** The key named "A" or "B"; null when Columba sent none (no talk yet). */
    public Key key(String name) {
        for (Key k : keys) {
            if (k.key.equals(name))
                return k;
        }
        return null;
    }

    /** Null for anything that is not a version 1 talk snapshot. */
    public static TalkSnapshot parse(String json) {
        if (json == null || json.isEmpty())
            return null;
        try {
            JSONObject root = new JSONObject(json);
            if (root.getInt("v") != VERSION)
                return null;
            List<Key> keys = new ArrayList<>();
            JSONArray list = root.getJSONArray("keys");
            for (int i = 0; i < list.length(); i++) {
                JSONObject k = list.getJSONObject(i);
                keys.add(new Key(k.getString("key"), k.getInt("position"), k.getString("label"),
                        string(k, "room"), k.getString("phase"), k.getString("line"),
                        string(k, "floor"), string(k, "last")));
            }
            return new TalkSnapshot(root.getBoolean("available"), root.getBoolean("running"),
                    string(root, "mode"), string(root, "status"), root.getBoolean("transmitting"),
                    root.getBoolean("remote"), root.getBoolean("background"),
                    root.optBoolean("mic_blocked", false), string(root, "hint"), keys);
        } catch (JSONException e) {
            return null;
        }
    }

    private static String string(JSONObject o, String key) throws JSONException {
        return o.isNull(key) ? null : o.getString(key);
    }
}
