package io.github.drlexus11.reticulumatak;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.chat.ChatManagerMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The plugin's connection to the mesh, for its whole lifetime -- not the
 * panel's: the favourites overlay stays on the map while the panel is closed.
 * Owns the Columba client, ATAK's link to Columba's endpoint, the favourites and
 * the overlay, and tells whoever is listening (the panel) what changed.
 */
final class MeshSession implements ColumbaMeshClient.Listener, Favourites.Listener {
    interface View {
        void onMesh(ColumbaMeshClient.State state, MeshSnapshot snapshot);
    }

    /** Re-opens the panel: back from a chat it dispatched returns to it. */
    interface PanelOpener {
        void reopenPanel();
    }

    private final Context pluginContext;
    private final ColumbaMeshClient client;
    private final ColumbaLink link;
    private final Favourites favourites;
    private final OverlaySettings overlaySettings;
    private final FavouritesOverlay overlay;
    private final PanelOpener opener;
    private final CopyOnWriteArrayList<View> views = new CopyOnWriteArrayList<>();
    private ColumbaMeshClient.State state = ColumbaMeshClient.State.CONNECTING;
    private MeshSnapshot snapshot;
    /** Set when the panel opened a chat; the chat's close then reopens the panel. */
    private boolean chatFromPanel;

    private final BroadcastReceiver chatClosed = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!chatFromPanel)
                return;
            chatFromPanel = false;
            opener.reopenPanel();
        }
    };

    MeshSession(Context pluginContext, Context hostContext, PanelOpener opener) {
        this.pluginContext = pluginContext;
        this.opener = opener;
        // Bound through ATAK's own context: it is ATAK that Columba sees calling.
        client = new ColumbaMeshClient(hostContext.getApplicationContext(), this);
        // Redraw when ATAK's connections change: the link line must not wait
        // for the next mesh change to say the link went down.
        link = new ColumbaLink(new ColumbaLink.Listener() {
            @Override
            public void onLinkChanged() {
                publish();
            }
        });
        favourites = new Favourites(hostContext);
        favourites.addListener(this);
        overlaySettings = new OverlaySettings(hostContext);
        overlay = new FavouritesOverlay(pluginContext, favourites, overlaySettings, null);
    }

    void start() {
        client.start();
        AtakBroadcast.DocumentedIntentFilter filter = new AtakBroadcast.DocumentedIntentFilter();
        filter.addAction(ChatManagerMapComponent.CHAT_ROOM_DROPDOWN_CLOSED);
        AtakBroadcast.getInstance().registerReceiver(chatClosed, filter);
    }

    void stop() {
        AtakBroadcast.getInstance().unregisterReceiver(chatClosed);
        favourites.removeListener(this);
        overlay.dispose();
        client.dispose();
        link.stop();
        views.clear();
    }

    void addView(View view) {
        views.add(view);
        view.onMesh(state, snapshot);
    }

    void removeView(View view) {
        views.remove(view);
    }

    Favourites favourites() {
        return favourites;
    }

    OverlaySettings.Size overlaySize() {
        return overlaySettings.size();
    }

    void setOverlaySize(OverlaySettings.Size size) {
        overlaySettings.setSize(size);
        overlay.refresh();
    }

    void resetOverlayPosition() {
        overlay.resetPosition();
    }

    ColumbaMeshClient client() {
        return client;
    }

    boolean linked() {
        return ColumbaLink.connected();
    }

    /** The panel's Chat: mark it, so closing the chat brings the panel back. */
    void chatFromPanel(MeshSnapshot.Peer peer) {
        PeerActions.chat(pluginContext, peer, new PeerActions.ChatOpened() {
            @Override
            public void onChatOpened() {
                chatFromPanel = true;
            }
        });
    }

    @Override
    public void onMesh(ColumbaMeshClient.State state, MeshSnapshot snapshot) {
        this.state = state;
        this.snapshot = state == ColumbaMeshClient.State.CONNECTED ? snapshot : null;
        // Columba is serving: make sure ATAK is connected to it (ColumbaLink).
        if (this.snapshot != null && this.snapshot.node.running && link.ensure())
            PeerActions.say(pluginContext, "Linking ATAK to Columba");
        publish();
    }

    @Override
    public void onFavouritesChanged() {
        publish();
    }

    private void publish() {
        overlay.update(snapshot);
        for (View view : views)
            view.onMesh(state, snapshot);
    }
}
