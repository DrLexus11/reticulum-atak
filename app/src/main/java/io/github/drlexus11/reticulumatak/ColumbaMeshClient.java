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

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService commands = Executors.newSingleThreadExecutor();
    private IColumbaMesh mesh;
    private boolean bound;

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
                mesh = candidate;
                deliver(State.CONNECTED, MeshSnapshot.parse(candidate.snapshot()));
                candidate.watch(watcher);
            } catch (RemoteException e) {
                mesh = null;
                deliver(State.LOST, null);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mesh = null;
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
        if (bound)
            return;
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

    public void stop() {
        IColumbaMesh current = mesh;
        mesh = null;
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

    /** Ask Columba to announce this node. Off the main thread: it can take seconds. */
    public void announce(Result result) {
        final IColumbaMesh current = mesh;
        commands.execute(() -> {
            int code;
            if (current == null) {
                code = 3; // ERR_NOT_READY: nothing bound to ask
            } else {
                try {
                    code = current.announce();
                } catch (RemoteException e) {
                    code = 3;
                }
            }
            final int reply = code;
            main.post(() -> result.onResult(reply));
        });
    }

    private void deliver(State state, MeshSnapshot snapshot) {
        main.post(() -> listener.onMesh(state, snapshot));
    }
}
