package io.github.drlexus11.reticulumatak;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.chat.ChatManagerMapComponent;
import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.ATAKUtilities;

/**
 * The plugin's main window: the mesh as this phone sees it.
 *
 * Three status lines -- the mesh, the command post, the relay -- an announce
 * button, and one row per team peer with its route and two buttons: Map pans
 * to the peer's marker, Chat opens ATAK's own GeoChat with them, which already
 * travels over the mesh through Columba. Every line is one short statement,
 * state first.
 */
final class MeshPanel implements ColumbaMeshClient.Listener {
    private final Context pluginContext;
    private final View root;
    private final TextView status;
    private final TextView commandPost;
    private final TextView relay;
    private final Button announce;
    private final LinearLayout peers;
    private final ColumbaMeshClient client;

    MeshPanel(Context pluginContext, Context hostContext) {
        this.pluginContext = pluginContext;
        root = PluginLayoutInflater.inflate(pluginContext, R.layout.main_layout, null);
        status = root.findViewById(R.id.status_line);
        commandPost = root.findViewById(R.id.command_post_line);
        relay = root.findViewById(R.id.relay_line);
        announce = root.findViewById(R.id.announce_button);
        peers = root.findViewById(R.id.peer_list);
        // Bound through ATAK's own context: it is ATAK that Columba sees calling.
        client = new ColumbaMeshClient(hostContext.getApplicationContext(), this);
        announce.setOnClickListener(v -> {
            announce.setEnabled(false);
            client.announce(code -> {
                announce.setEnabled(true);
                say(MeshLines.announce(code));
            });
        });
    }

    View view() {
        return root;
    }

    void start() {
        client.start();
    }

    void stop() {
        client.stop();
    }

    @Override
    public void onMesh(ColumbaMeshClient.State state, MeshSnapshot snapshot) {
        if (state != ColumbaMeshClient.State.CONNECTED || snapshot == null) {
            status.setText(stateLine(state));
            commandPost.setText("");
            relay.setText("");
            announce.setEnabled(false);
            peers.removeAllViews();
            return;
        }
        long now = System.currentTimeMillis();
        status.setText(MeshLines.node(snapshot));
        commandPost.setText(MeshLines.commandPost(snapshot));
        relay.setText(MeshLines.relay(snapshot));
        announce.setEnabled(snapshot.node.running);
        peers.removeAllViews();
        for (MeshSnapshot.Peer peer : snapshot.peers)
            peers.addView(row(peer, now));
    }

    private View row(MeshSnapshot.Peer peer, long now) {
        View row = PluginLayoutInflater.inflate(pluginContext, R.layout.peer_row, null);
        String name = peer.callsign != null ? peer.callsign : peer.uid;
        ((TextView) row.findViewById(R.id.peer_name))
                .setText(peer.role != null ? name + " · " + peer.role : name);
        ((TextView) row.findViewById(R.id.peer_route)).setText(MeshLines.peer(peer, now));
        row.findViewById(R.id.peer_locate).setOnClickListener(v -> locate(peer, name));
        row.findViewById(R.id.peer_chat).setOnClickListener(v -> chat(peer, name));
        return row;
    }

    private void locate(MeshSnapshot.Peer peer, String name) {
        MapView map = MapView.getMapView();
        MapItem item = map != null ? map.getRootGroup().deepFindUID(peer.uid) : null;
        if (item == null) {
            say("No marker: " + name);
            return;
        }
        ATAKUtilities.scaleToFit(item);
    }

    private void chat(MeshSnapshot.Peer peer, String name) {
        Contact contact = Contacts.getInstance().getContactByUuid(peer.uid);
        if (!(contact instanceof IndividualContact)) {
            say("Not a contact yet: " + name);
            return;
        }
        ChatManagerMapComponent.getInstance().openConversation((IndividualContact) contact, true);
    }

    private static String stateLine(ColumbaMeshClient.State state) {
        switch (state) {
            case CONNECTING:
                return "Mesh: connecting to Columba";
            case NOT_FOUND:
                return "Mesh: Columba not installed";
            case REFUSED:
                return "Mesh: Columba refused this ATAK";
            case WRONG_VERSION:
                return "Mesh: Columba version mismatch";
            case LOST:
                return "Mesh: Columba stopped";
            default:
                return "Mesh: no state";
        }
    }

    private void say(String line) {
        MapView map = MapView.getMapView();
        Context context = map != null ? map.getContext() : pluginContext;
        Toast.makeText(context, line, Toast.LENGTH_SHORT).show();
    }
}
