package io.github.drlexus11.reticulumatak;

import android.os.Bundle;

import com.atakmap.comms.CotServiceRemote;
import com.atakmap.comms.TAKServer;
import com.atakmap.comms.TAKServerListener;

/**
 * ATAK's connection to Columba's CoT endpoint, which carries the team's
 * positions, markers and chat between ATAK and the mesh.
 *
 * Without it ATAK sees nobody, and nothing in ATAK says why: found on the bench
 * phone on 2026-10-02, whose connection went with an ATAK reinstall while
 * Columba logged "no connected clients" for every position it received. So the
 * plugin makes the connection itself, once Columba is known to be serving, and
 * the panel says when it is down.
 *
 * Never a second connection, never over somebody else's: if ATAK already has
 * any connection to the endpoint's port, on any host -- one made by hand, or
 * the command post's link to its bridge -- this leaves it alone.
 */
final class ColumbaLink {
    static final String HOST = "127.0.0.1";
    static final int PORT = 18087;
    static final String CONNECT = HOST + ":" + PORT + ":tcp";
    static final String DESCRIPTION = "Columba";

    interface Listener {
        void onLinkChanged();
    }

    /** How long an add may wait for ATAK's CoT service before it may be tried again. */
    static final long PENDING_TIMEOUT_MS = 30_000;

    private final Listener listener;
    private CotServiceRemote remote;
    /** Whether [remote] is connected: connect() on a connected remote calls nobody back. */
    private boolean serviceUp;
    private long pendingSince;

    ColumbaLink(Listener listener) {
        this.listener = listener;
    }

    /** Whether a connection to the endpoint's port exists, on any host. */
    static boolean exists() {
        for (TAKServer server : servers()) {
            if (sameEndpoint(server.getConnectString()))
                return true;
        }
        return false;
    }

    /** Whether ATAK reports such a connection up. False when there is none. */
    static boolean connected() {
        for (TAKServer server : servers()) {
            if (sameEndpoint(server.getConnectString()) && server.isConnected())
                return true;
        }
        return false;
    }

    /** "host:port:proto" (ATAK's form) naming a TCP stream on the endpoint's port. */
    static boolean sameEndpoint(String connect) {
        if (connect == null)
            return false;
        String[] parts = connect.trim().toLowerCase().split(":");
        return parts.length == 3 && parts[1].equals(String.valueOf(PORT)) && parts[2].equals("tcp");
    }

    /**
     * Add the connection if ATAK has none to the endpoint. Called once Columba
     * has said its endpoint is running; the add itself happens when ATAK's
     * CoT service answers. True if an add was started.
     */
    boolean ensure() {
        if (exists())
            return false;
        long now = System.currentTimeMillis();
        if (pendingSince != 0 && now - pendingSince < PENDING_TIMEOUT_MS)
            return false;
        if (remote != null && serviceUp) {
            // Already connected: a second connect() would be a silent no-op,
            // and a connection removed after the first add would never return.
            addStream();
            listener.onLinkChanged();
            return true;
        }
        pendingSince = now;
        if (remote == null) {
            remote = new CotServiceRemote();
            remote.setOutputsChangedListener(new CotServiceRemote.OutputsChangedListener() {
                @Override
                public void onCotOutputRemoved(Bundle descBundle) {
                    listener.onLinkChanged();
                }

                @Override
                public void onCotOutputUpdated(Bundle descBundle) {
                    listener.onLinkChanged();
                }
            });
        }
        remote.connect(new CotServiceRemote.ConnectionListener() {
            @Override
            public void onCotServiceConnected(Bundle fullServiceState) {
                serviceUp = true;
                if (pendingSince != 0 && !exists())
                    addStream();
                pendingSince = 0;
                listener.onLinkChanged();
            }

            @Override
            public void onCotServiceDisconnected() {
                serviceUp = false;
                pendingSince = 0;
            }
        });
        return true;
    }

    private void addStream() {
        if (remote == null || exists())
            return;
        Bundle stream = new Bundle();
        stream.putString(TAKServer.DESCRIPTION_KEY, DESCRIPTION);
        stream.putBoolean(TAKServer.ENABLED_KEY, true);
        stream.putBoolean(TAKServer.COMPRESSION_KEY, false);
        stream.putBoolean(TAKServer.USEAUTH_KEY, false);
        remote.addStream(CONNECT, stream);
    }

    void stop() {
        if (remote != null) {
            remote.disconnect();
            remote = null;
        }
        serviceUp = false;
        pendingSince = 0;
    }

    private static TAKServer[] servers() {
        TAKServerListener servers = TAKServerListener.getInstance();
        TAKServer[] list = servers != null ? servers.getServers() : null;
        return list != null ? list : new TAKServer[0];
    }
}
