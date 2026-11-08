// Columba's mesh interface, talk capability (EXPERIMENTAL; see IColumbaMesh.aidl).
package network.columba.app.mesh;

interface IColumbaTalkWatcher {
    oneway void changed(String talk);
}
