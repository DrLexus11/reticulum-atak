// Columba's mesh interface, version 1, with the interfaces capability: the contract with the ATAK plugin.
// Defined in reticulum-atak's docs/ColumbaInterface.md; this file and the
// plugin's copy must stay identical. A breaking change bumps the version; an
// additive one appends methods behind a capability bit (capabilities()).
package network.columba.app.mesh;

import network.columba.app.mesh.IColumbaMeshWatcher;
import network.columba.app.mesh.IColumbaTalkWatcher;

interface IColumbaMesh {
    /** 1; -1 for a refused caller. The plugin refuses a version it does not know. */
    int version();

    /** The mesh as this phone sees it, as version 1 snapshot JSON. Empty for a refused caller. */
    String snapshot();

    /** Called with a fresh snapshot when it changes, at most once a second. */
    void watch(IColumbaMeshWatcher watcher);
    void unwatch(IColumbaMeshWatcher watcher);

    /** Commands, each returning a result code (ColumbaInterface.md). */
    int announce();

    // ---- appended (ColumbaInterface.md, "Interfaces"): the version 1 methods'
    // ---- transaction codes are unchanged, so an older client keeps working.

    /** Bitmask of what this service offers: 1 = interfaces, 2 = talk. 0 from a Columba that predates it. */
    int capabilities();

    /** Switch one configured interface; Columba's guard refuses isolating switches. */
    int setInterfaceEnabled(long id, boolean enabled);

    /** Apply staged switches: restarts Columba's Reticulum where it cannot switch live. */
    int applyInterfaces();

    // ---- appended (ColumbaInterface.md, "Talk", EXPERIMENTAL): capability bit 2.
    // ---- Every earlier method's transaction code is unchanged.

    /** Columba's talk now, as talk snapshot JSON. Empty for a refused caller. */
    String talkState();

    /**
     * Pushed the talk snapshot on every change. While a watcher is registered and
     * "allow ATAK control" is on, Columba keeps live talk running in the background.
     * Returns 0, or ERR_CONTROL_OFF (registered, view only), or ERR_CALLER.
     */
    int talkWatch(IColumbaTalkWatcher watcher);
    void talkUnwatch(IColumbaTalkWatcher watcher);

    /**
     * Key [key] (0 = A, 1 = B) down. [token] is a binder of the caller's: if its process
     * dies the key is let go; a key held past Columba's maximum is let go regardless.
     */
    int talkPress(int key, IBinder token);

    /** Key [key] up. Never refused for "allow ATAK control" off: letting go is always allowed. */
    int talkRelease(int key);
}
