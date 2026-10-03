package io.github.drlexus11.reticulumatak;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import network.columba.app.mesh.IColumbaMesh;
import network.columba.app.mesh.IColumbaMeshWatcher;

/**
 * The plugin's end of Columba's mesh interface (docs/ColumbaInterface.md).
 *
 * Binds by action, trying the release package and then the debug one; checks
 * the version before trusting anything; and hands every snapshot to the
 * listener on the main thread. Columba checks who is calling -- this side only
 * reports what it was told.
 */
public final class ColumbaMeshClient {
    public static final String ACTION = "network.columba.app.mesh.BIND";
    static final String[] PACKAGES = {"network.columba.app", "network.columba.app.debug"};

    public enum State {
        /** Bind sent, no answer yet. */
        CONNECTING,
        /** No Columba with the mesh interface on this phone. */
        NOT_FOUND,
        /** Columba does not allow this ATAK (its package or certificate). */
        REFUSED,
        /** Columba speaks another version of the interface. */
        WRONG_VERSION,
        CONNECTED,
        /** Columba went away; Android rebinds when it comes back. */
        LOST,
    }

    public interface Listener {
        void onMesh(State state, MeshSnapshot snapshot);
    }

    public interface Result {
        void onResult(int code);
    }

    /** capabilities() bit: interface switching (ColumbaInterface.md, "Interfaces"). */
    public static final int CAP_INTERFACES = 1;
    /** Local result: this Columba lacks the capability the call needs. Never sent by Columba. */
    public static final int NOT_SUPPORTED = -1;
    private static final int ERR_NOT_READY = 3;

    private interface Call {
        int run(IColumbaMesh mesh) throws RemoteException;
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService commands = Executors.newSingleThreadExecutor();
    private IColumbaMesh mesh;
    /** What the bound Columba can do beyond v1; 0 until it says, or when unbound. */
    private volatile int capabilities;
    private boolean bound;
    /**
     * Bumped by stop(): a callback or command result posted before the stop
     * must not reach a listener that has been torn down -- it could redraw a
     * dead panel, or re-create ATAK's link after the plugin stopped.
     */
    private volatile int generation;

    private final IColumbaMeshWatcher watcher = new IColumbaMeshWatcher.Stub() {
        @Override
        public void changed(String snapshot) {
            deliver(State.CONNECTED, MeshSnapshot.parse(snapshot));
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            IColumbaMesh candidate = IColumbaMesh.Stub.asInterface(service);
            try {
                int version = candidate.version();
                if (version == -1) {
                    deliver(State.REFUSED, null);
                    return;
                }
                if (version != MeshSnapshot.VERSION) {
                    deliver(State.WRONG_VERSION, null);
                    return;
                }
                capabilities = capabilitiesOf(candidate);
                mesh = candidate;
                deliver(State.CONNECTED, MeshSnapshot.parse(candidate.snapshot()));
                candidate.watch(watcher);
            } catch (RemoteException e) {
                mesh = null;
                capabilities = 0;
                deliver(State.LOST, null);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mesh = null;
            capabilities = 0;
            deliver(State.LOST, null);
        }

        @Override
        public void onBindingDied(ComponentName name) {
            // Columba was updated or stopped for good: bind afresh.
            stop();
            start();
        }
    };

    public ColumbaMeshClient(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void start() {
        if (bound || commands.isShutdown())
            return;   // already bound, or disposed for good
        deliver(State.CONNECTING, null);
        for (String pkg : PACKAGES) {
            Intent intent = new Intent(ACTION).setPackage(pkg);
            boolean ok;
            try {
                ok = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            } catch (SecurityException e) {
                ok = false;
            }
            if (ok) {
                bound = true;
                return;
            }
            // A failed bind still holds the connection until released.
            try {
                context.unbindService(connection);
            } catch (IllegalArgumentException ignored) {
            }
        }
        deliver(State.NOT_FOUND, null);
    }

    /** For good: stop, and end the command thread. start() must not follow. */
    public void dispose() {
        stop();
        commands.shutdownNow();
    }

    public void stop() {
        generation++;
        IColumbaMesh current = mesh;
        mesh = null;
        capabilities = 0;
        if (current != null) {
            try {
                current.unwatch(watcher);
            } catch (RemoteException ignored) {
            }
        }
        if (bound) {
            bound = false;
            try {
                context.unbindService(connection);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /** Whether the bound Columba offers [capability] (a CAP_ bit). */
    public boolean has(int capability) {
        return (capabilities & capability) == capability;
    }

    /** Ask Columba to announce this node. Off the main thread: it can take seconds. */
    public void announce(Result result) {
        command(0, IColumbaMesh::announce, result);
    }

    /** Switch one configured interface on or off; Columba's guard may refuse. */
    public void setInterfaceEnabled(long id, boolean enabled, Result result) {
        command(CAP_INTERFACES, m -> m.setInterfaceEnabled(id, enabled), result);
    }

    /** Put staged switches into effect (restarts the mesh on a staging backend). */
    public void applyInterfaces(Result result) {
        command(CAP_INTERFACES, IColumbaMesh::applyInterfaces, result);
    }

    /**
     * One call to Columba off the main thread, its result back on it -- unless
     * the client was stopped meanwhile.
     *
     * [needs] is checked here, not left to Columba: a Columba that predates a
     * method answers it with an empty reply, which reads as 0, OK. Only the
     * capability bit tells a done switch from one never heard.
     */
    private void command(int needs, Call call, Result result) {
        final IColumbaMesh current = mesh;
        final int asked = generation;
        final boolean supported = has(needs);
        if (commands.isShutdown())
            return;
        commands.execute(() -> {
            int code;
            if (current == null) {
                code = ERR_NOT_READY; // nothing bound to ask
            } else if (!supported) {
                code = NOT_SUPPORTED;
            } else {
                try {
                    code = call.run(current);
                } catch (RemoteException e) {
                    code = ERR_NOT_READY;
                }
            }
            final int reply = code;
            main.post(() -> {
                if (asked == generation)
                    result.onResult(reply);
            });
        });
    }

    /**
     * capabilities() of a Columba that predates it reads as 0 (see command());
     * anything thrown counts as 0 too -- v1 still works without it.
     */
    private static int capabilitiesOf(IColumbaMesh candidate) {
        try {
            return candidate.capabilities();
        } catch (RemoteException | RuntimeException e) {
            return 0;
        }
    }

    private void deliver(State state, MeshSnapshot snapshot) {
        final int posted = generation;
        main.post(() -> {
            if (posted == generation)
                listener.onMesh(state, snapshot);
        });
    }
}
