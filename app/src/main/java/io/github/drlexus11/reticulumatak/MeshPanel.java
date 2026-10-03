package io.github.drlexus11.reticulumatak;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.maps.MapView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The pane's first page (MeshPane): the mesh as this phone sees it.
 *
 * A fixed header -- the mesh, the command post and the relay, one short line
 * each, state first, and Announce -- over a list that scrolls on its own:
 * favourites first, then reachable peers nearest first, then the rest. Each row
 * has a reachability dot (PeerStatus) and ATAK's own icons for favourite,
 * pan-to-marker and chat. A view on the plugin's MeshSession; the session lives
 * on when the panel closes.
 */
final class MeshPanel implements MeshSession.View {
    private final Context pluginContext;
    private final MeshSession session;
    private final View root;
    private final TextView status;
    private final TextView commandPost;
    private final TextView relay;
    private final Button announce;
    private final PeerAdapter adapter = new PeerAdapter();
    private ColumbaMeshClient.State lastState = ColumbaMeshClient.State.CONNECTING;
    private MeshSnapshot lastSnapshot;

    MeshPanel(Context pluginContext, MeshSession session) {
        this.pluginContext = pluginContext;
        this.session = session;
        root = PluginLayoutInflater.inflate(pluginContext, R.layout.mesh_page, null);
        status = root.findViewById(R.id.status_line);
        commandPost = root.findViewById(R.id.command_post_line);
        relay = root.findViewById(R.id.relay_line);
        announce = root.findViewById(R.id.announce_button);
        ((ListView) root.findViewById(R.id.peer_list)).setAdapter(adapter);
        // The map overlay's size and place, here where the operator already is.
        bindSize(R.id.overlay_small, OverlaySettings.Size.SMALL);
        bindSize(R.id.overlay_medium, OverlaySettings.Size.MEDIUM);
        bindSize(R.id.overlay_large, OverlaySettings.Size.LARGE);
        root.findViewById(R.id.overlay_reset).setOnClickListener(v -> session.resetOverlayPosition());
        markSize();
        announce.setOnClickListener(v -> {
            announce.setEnabled(false);
            session.client().announce(code -> {
                // From the state now, not the one the request was made in:
                // Columba may have gone away while it was answering.
                announce.setEnabled(canAnnounce());
                PeerActions.say(pluginContext, MeshLines.announce(code));
            });
        });
    }

    View view() {
        return root;
    }

    private void bindSize(int id, OverlaySettings.Size size) {
        root.findViewById(id).setOnClickListener(v -> {
            session.setOverlaySize(size);
            markSize();
        });
    }

    /** The chosen size is selected -- for screen readers too -- and the others dimmed. */
    private void markSize() {
        OverlaySettings.Size current = session.overlaySize();
        mark(root.findViewById(R.id.overlay_small), current == OverlaySettings.Size.SMALL);
        mark(root.findViewById(R.id.overlay_medium), current == OverlaySettings.Size.MEDIUM);
        mark(root.findViewById(R.id.overlay_large), current == OverlaySettings.Size.LARGE);
    }

    /** Selected and lit, or not selected and dimmed: state that accessibility services read. */
    private static void mark(View view, boolean on) {
        view.setSelected(on);
        view.setAlpha(on ? 1f : 0.45f);
    }

    @Override
    public void onMesh(ColumbaMeshClient.State state, MeshSnapshot snapshot) {
        lastState = state;
        lastSnapshot = snapshot;
        if (state != ColumbaMeshClient.State.CONNECTED || snapshot == null) {
            status.setText(stateLine(state));
            commandPost.setText("");
            relay.setText("");
            announce.setEnabled(false);
            adapter.show(new ArrayList<MeshSnapshot.Peer>(), 0);
            return;
        }
        long now = System.currentTimeMillis();
        status.setText(MeshLines.node(snapshot, session.linked()));
        commandPost.setText(MeshLines.commandPost(snapshot));
        relay.setText(MeshLines.relay(snapshot));
        announce.setEnabled(canAnnounce());
        adapter.show(PeerStatus.ordered(snapshot.peers, session.favourites().all(), now), now);
    }

    private boolean canAnnounce() {
        return lastState == ColumbaMeshClient.State.CONNECTED && lastSnapshot != null
                && lastSnapshot.node.running;
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

    /**
     * The row's action icons at ATAK's own list_item_action_icon_size, centred
     * in a 48dp touch target -- the size ATAK uses for actions in its own lists.
     */
    private static void sizeActions(View row, int... ids) {
        MapView map = MapView.getMapView();
        if (map == null)
            return;
        android.content.res.Resources res = map.getContext().getResources();
        int icon;
        try {
            icon = res.getDimensionPixelSize(com.atakmap.app.R.dimen.list_item_action_icon_size);
        } catch (RuntimeException e) {
            icon = Math.round(24 * res.getDisplayMetrics().density);
        }
        int target = Math.max(icon, Math.round(48 * res.getDisplayMetrics().density));
        int pad = (target - icon) / 2;
        for (int id : ids) {
            View button = row.findViewById(id);
            ViewGroup.LayoutParams lp = button.getLayoutParams();
            lp.width = target;
            lp.height = target;
            button.setLayoutParams(lp);
            button.setPadding(pad, pad, pad, pad);
        }
    }

    /** ATAK's own drawable, from ATAK's resources; null if this ATAK lacks it. */
    private static Drawable atakIcon(int id) {
        MapView map = MapView.getMapView();
        try {
            return map != null ? map.getContext().getResources().getDrawable(id) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private final class PeerAdapter extends BaseAdapter {
        private List<MeshSnapshot.Peer> peers = new ArrayList<>();
        private long now;

        void show(List<MeshSnapshot.Peer> peers, long now) {
            this.peers = peers;
            this.now = now;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return peers.size();
        }

        @Override
        public Object getItem(int position) {
            return peers.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView
                    : PluginLayoutInflater.inflate(pluginContext, R.layout.peer_row, null);
            final MeshSnapshot.Peer peer = peers.get(position);
            Set<String> favourites = session.favourites().all();
            String name = PeerActions.name(peer);
            ((TextView) row.findViewById(R.id.peer_name))
                    .setText(peer.role != null ? name + " · " + peer.role : name);
            ((TextView) row.findViewById(R.id.peer_route)).setText(MeshLines.peer(peer, now));

            sizeActions(row, R.id.peer_favourite, R.id.peer_locate, R.id.peer_chat);

            GradientDrawable dot = (GradientDrawable) row.findViewById(R.id.peer_dot).getBackground().mutate();
            dot.setColor(PeerStatus.color(PeerStatus.of(peer, now)));

            ImageButton star = row.findViewById(R.id.peer_favourite);
            star.setImageDrawable(atakIcon(com.atakmap.app.R.drawable.lpt_white_star_drawable));
            // Lit when chosen, dimmed when not: the same icon, as ATAK does --
            // and selected, which screen readers announce.
            boolean favourite = favourites.contains(peer.uid);
            star.setSelected(favourite);
            star.setAlpha(favourite ? 1f : 0.3f);
            star.setOnClickListener(v -> session.favourites().toggle(peer.uid));

            ImageButton locate = row.findViewById(R.id.peer_locate);
            // ATAK's own "center on" (its navigation toolbar's), not ic_menu_goto,
            // whose plus reads as "add".
            locate.setImageDrawable(atakIcon(com.atakmap.app.R.drawable.nav_center));
            locate.setOnClickListener(v -> PeerActions.locate(pluginContext, peer));

            ImageButton chat = row.findViewById(R.id.peer_chat);
            chat.setImageDrawable(atakIcon(com.atakmap.app.R.drawable.ic_menu_chat));
            chat.setOnClickListener(v -> session.chatFromPanel(peer));
            return row;
        }
    }
}
