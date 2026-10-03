package io.github.drlexus11.reticulumatak;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Columba's mesh snapshot, version 1 (docs/ColumbaInterface.md), as the panel
 * reads it. Pinned by fixtures/columba_mesh_v1.json, which Columba tests
 * against too. A JSON null stays null here: "unknown" is not "none".
 */
public final class MeshSnapshot {
    public static final int VERSION = 1;
    /** ATAK's own role for a command post. */
    public static final String COMMAND_POST_ROLE = "HQ";

    public static final class Node {
        public final String uid;
        public final String callsign;
        public final boolean control;
        public final boolean running;

        Node(String uid, String callsign, boolean control, boolean running) {
            this.uid = uid;
            this.callsign = callsign;
            this.control = control;
            this.running = running;
        }
    }

    public static final class Propagation {
        public final String hash;
        public final String name;
        public final boolean path;
        public final Integer hops;
        public final String carrier;
        public final Boolean isCommandPost;
        public final Long lastSync;

        Propagation(String hash, String name, boolean path, Integer hops, String carrier,
                Boolean isCommandPost, Long lastSync) {
            this.hash = hash;
            this.name = name;
            this.path = path;
            this.hops = hops;
            this.carrier = carrier;
            this.isCommandPost = isCommandPost;
            this.lastSync = lastSync;
        }
    }

    public static final class Peer {
        public final String uid;
        public final String callsign;
        public final String role;
        public final long heard;
        public final boolean path;
        public final Integer hops;
        public final String carrier;
        public final String iface;

        Peer(String uid, String callsign, String role, long heard, boolean path, Integer hops,
                String carrier, String iface) {
            this.uid = uid;
            this.callsign = callsign;
            this.role = role;
            this.heard = heard;
            this.path = path;
            this.hops = hops;
            this.carrier = carrier;
            this.iface = iface;
        }

        public boolean isCommandPost() {
            return COMMAND_POST_ROLE.equals(role);
        }
    }

    /** A configured interface and its running state (ColumbaInterface.md, "Interfaces"). */
    public static final class Iface {
        public final long id;
        public final String name;
        public final String type;
        public final String carrier;
        public final boolean enabled;
        public final boolean online;
        public final Long rxBytes;
        public final Long txBytes;
        public final String reason;
        public final boolean carriesCommandPost;

        Iface(long id, String name, String type, String carrier, boolean enabled, boolean online,
                Long rxBytes, Long txBytes, String reason, boolean carriesCommandPost) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.carrier = carrier;
            this.enabled = enabled;
            this.online = online;
            this.rxBytes = rxBytes;
            this.txBytes = txBytes;
            this.reason = reason;
            this.carriesCommandPost = carriesCommandPost;
        }
    }

    public final long at;
    public final Node node;
    public final Propagation propagation;
    public final List<Peer> peers;
    /** Interfaces capability; empty from a Columba without it. */
    public final List<Iface> interfaces;
    public final boolean interfacesLive;
    public final boolean interfacesPending;

    private MeshSnapshot(long at, Node node, Propagation propagation, List<Peer> peers,
            List<Iface> interfaces, boolean interfacesLive, boolean interfacesPending) {
        this.at = at;
        this.node = node;
        this.propagation = propagation;
        this.peers = Collections.unmodifiableList(peers);
        this.interfaces = Collections.unmodifiableList(interfaces);
        this.interfacesLive = interfacesLive;
        this.interfacesPending = interfacesPending;
    }

    /** Null for anything that is not a version 1 snapshot. */
    public static MeshSnapshot parse(String json) {
        if (json == null || json.isEmpty())
            return null;
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("v", -1) != VERSION)
                return null;
            JSONObject n = root.getJSONObject("node");
            Node node = new Node(string(n, "uid"), string(n, "callsign"),
                    n.getBoolean("control"), n.getBoolean("running"));
            Propagation propagation = null;
            if (!root.isNull("propagation")) {
                JSONObject p = root.getJSONObject("propagation");
                propagation = new Propagation(p.getString("hash"), string(p, "name"),
                        p.getBoolean("path"), integer(p, "hops"), string(p, "carrier"),
                        p.isNull("is_command_post") ? null : p.getBoolean("is_command_post"),
                        p.isNull("last_sync") ? null : p.getLong("last_sync"));
            }
            JSONArray array = root.getJSONArray("peers");
            List<Peer> peers = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject p = array.getJSONObject(i);
                peers.add(new Peer(p.getString("uid"), string(p, "callsign"), string(p, "role"),
                        p.getLong("heard"), p.getBoolean("path"), integer(p, "hops"),
                        string(p, "carrier"), string(p, "interface")));
            }
            // Optional (the interfaces capability): absent from an older Columba.
            List<Iface> interfaces = new ArrayList<>();
            JSONArray ifaces = root.optJSONArray("interfaces");
            if (ifaces != null) {
                for (int i = 0; i < ifaces.length(); i++) {
                    JSONObject f = ifaces.getJSONObject(i);
                    interfaces.add(new Iface(f.getLong("id"), f.getString("name"), f.getString("type"),
                            string(f, "carrier"), f.getBoolean("enabled"), f.getBoolean("online"),
                            f.isNull("rx_bytes") ? null : f.getLong("rx_bytes"),
                            f.isNull("tx_bytes") ? null : f.getLong("tx_bytes"),
                            string(f, "reason"), f.getBoolean("carries_command_post")));
                }
            }
            return new MeshSnapshot(root.getLong("at"), node, propagation, peers, interfaces,
                    root.optBoolean("interfaces_live", false), root.optBoolean("interfaces_pending", false));
        } catch (JSONException e) {
            return null;
        }
    }

    private static String string(JSONObject o, String key) throws JSONException {
        return o.isNull(key) ? null : o.getString(key);
    }

    private static Integer integer(JSONObject o, String key) throws JSONException {
        return o.isNull(key) ? null : o.getInt(key);
    }
}
