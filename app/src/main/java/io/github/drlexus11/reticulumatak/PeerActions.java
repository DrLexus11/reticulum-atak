package io.github.drlexus11.reticulumatak;

import android.content.Context;
import android.widget.Toast;

import com.atakmap.android.chat.ChatManagerMapComponent;
import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.ATAKUtilities;

/**
 * What a peer's buttons do, shared by the panel and the map overlay: pan the
 * map to their marker, or open ATAK's own GeoChat with them. One short line,
 * state first, when it cannot be done.
 */
final class PeerActions {
    interface ChatOpened {
        void onChatOpened();
    }

    private PeerActions() {
    }

    static String name(MeshSnapshot.Peer peer) {
        return peer.callsign != null ? peer.callsign : peer.uid;
    }

    static void locate(Context fallback, MeshSnapshot.Peer peer) {
        MapView map = MapView.getMapView();
        MapItem item = map != null ? map.getRootGroup().deepFindUID(peer.uid) : null;
        if (item == null) {
            say(fallback, "No marker: " + name(peer));
            return;
        }
        ATAKUtilities.scaleToFit(item);
    }

    /** True if GeoChat opened. [opened] runs first, so the caller can mark it. */
    static boolean chat(Context fallback, MeshSnapshot.Peer peer, ChatOpened opened) {
        Contact contact = Contacts.getInstance().getContactByUuid(peer.uid);
        if (!(contact instanceof IndividualContact)) {
            say(fallback, "Not a contact yet: " + name(peer));
            return false;
        }
        if (opened != null)
            opened.onChatOpened();
        ChatManagerMapComponent.getInstance().openConversation((IndividualContact) contact, true);
        return true;
    }

    static void say(Context fallback, String line) {
        MapView map = MapView.getMapView();
        Context context = map != null ? map.getContext() : fallback;
        Toast.makeText(context, line, Toast.LENGTH_SHORT).show();
    }
}
