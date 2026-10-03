package io.github.drlexus11.reticulumatak;

import android.content.Context;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.view.MotionEvent;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.widgets.TextWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The favourite peers as a squad indicator over the map: a header, then one
 * row each, coloured by mesh reachability (PeerStatus), with name and hops.
 *
 * - Tap a row: the map centres on that peer. Long-press: GeoChat with them. A
 *   stray tap therefore only moves the map; it never opens the wrong chat.
 * - Drag the header to place the column anywhere; the place is remembered
 *   (OverlaySettings). By default it sits at the left edge, between ATAK's zoom
 *   control and its scale bar.
 * - Size S, M or L from the panel; padding scales with the text, so a row is
 *   always a little taller than its text and sizes stay in proportion.
 *
 * Drawn with ATAK's own map widgets in its root layout.
 */
final class FavouritesOverlay {
    /** Translucent black behind the rows, legible over imagery by day or night. */
    private static final int BACKING = 0xB0000000;
    private static final int HEADER_BACKING = 0xD0000000;
    private static final int HEADER_COLOR = 0xFFE0E0E0;
    /** Default place: left edge, below the zoom control, above the scale bar. */
    private static final float DEFAULT_X_DP = 8f;
    private static final float DEFAULT_Y_FRACTION = 0.58f;

    private final Context pluginContext;
    private final Favourites favourites;
    private final OverlaySettings settings;
    private final PeerActions.ChatOpened onChat;
    private RootLayoutWidget root;
    private LinearLayoutWidget column;
    private MeshSnapshot last;
    // The rows on screen, so an update can change them in place.
    private final List<TextWidget> rows = new ArrayList<>();
    private final List<String> rowUids = new ArrayList<>();
    private final List<Integer> rowColors = new ArrayList<>();
    private final List<MeshSnapshot.Peer> rowPeers = new ArrayList<>();

    // Header drag state.
    private float downX, downY, startX, startY;
    private boolean dragging;

    FavouritesOverlay(Context pluginContext, Favourites favourites, OverlaySettings settings,
            PeerActions.ChatOpened onChat) {
        this.pluginContext = pluginContext;
        this.favourites = favourites;
        this.settings = settings;
        this.onChat = onChat;
        MapView map = MapView.getMapView();
        Object widget = map != null ? map.getComponentExtra("rootLayoutWidget") : null;
        if (widget instanceof RootLayoutWidget) {
            root = (RootLayoutWidget) widget;
            column = new LinearLayoutWidget();
            column.setOrientation(LinearLayoutWidget.VERTICAL);
            root.addWidget(column);
            place();
        }
    }

    /**
     * Redraw from a snapshot. Null, or no favourite among the peers: nothing shown.
     *
     * In place when the same favourites are shown in the same order -- only the
     * text and colour of a row that changed are touched. Removing and re-adding
     * every row on each update flickered while the map moved (bench, 2026-10-03):
     * updates arrive every few seconds and with every change to ATAK's links.
     */
    void update(MeshSnapshot snapshot) {
        last = snapshot;
        if (column == null)
            return;
        List<MeshSnapshot.Peer> shown = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (snapshot != null) {
            Set<String> chosen = favourites.all();
            for (MeshSnapshot.Peer peer : PeerStatus.ordered(snapshot.peers, chosen, now)) {
                if (!chosen.contains(peer.uid))
                    break; // favourites come first; the rest are not shown here
                shown.add(peer);
            }
        }
        List<String> uids = new ArrayList<>();
        for (MeshSnapshot.Peer peer : shown)
            uids.add(peer.uid);
        if (!uids.equals(rowUids)) {
            rebuild(shown, now);
            return;
        }
        for (int i = 0; i < shown.size(); i++) {
            MeshSnapshot.Peer peer = shown.get(i);
            TextWidget row = rows.get(i);
            String text = "\u25CF " + PeerStatus.overlayLine(peer);
            int color = PeerStatus.color(PeerStatus.of(peer, now));
            if (!text.equals(row.getText()))
                row.setText(text);
            if (color != rowColors.get(i)) {
                row.setColor(color);
                rowColors.set(i, color);
            }
            rowPeers.set(i, peer); // tap and long-press act on the latest state
        }
    }

    /** Remove everything and lay the column out again: a new set of favourites, or a new size. */
    private void rebuild(List<MeshSnapshot.Peer> shown, long now) {
        for (int i = column.getChildWidgets().size() - 1; i >= 0; i--)
            column.removeWidgetAt(i);
        rows.clear();
        rowUids.clear();
        rowColors.clear();
        rowPeers.clear();
        if (shown.isEmpty())
            return;
        MapTextFormat format = format();
        float pad = padding(format);
        column.addWidget(header(format, pad));
        for (MeshSnapshot.Peer peer : shown) {
            final int index = rows.size();
            int color = PeerStatus.color(PeerStatus.of(peer, now));
            TextWidget row = new TextWidget("\u25CF " + PeerStatus.overlayLine(peer), format);
            row.setColor(color);
            row.setBackground(BACKING);
            row.setPadding(pad, pad, pad * 1.5f, pad);
            row.setMargins(0f, 0f, 0f, 2f);
            row.addOnClickListener(new MapWidget.OnClickListener() {
                @Override
                public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
                    PeerActions.locate(pluginContext, rowPeers.get(index));
                }
            });
            row.addOnLongPressListener(new MapWidget.OnLongPressListener() {
                @Override
                public void onMapWidgetLongPress(MapWidget widget) {
                    PeerActions.chat(pluginContext, rowPeers.get(index), onChat);
                }
            });
            column.addWidget(row);
            rows.add(row);
            rowUids.add(peer.uid);
            rowColors.add(color);
            rowPeers.add(peer);
        }
    }

    /** Redraw at the current size (after a size change in the panel). */
    void refresh() {
        rowUids.clear(); // forces a rebuild with the new format
        update(last);
    }

    /** Back to the default place. */
    void resetPosition() {
        settings.resetPosition();
        place();
    }

    void dispose() {
        if (column == null)
            return;
        for (int i = column.getChildWidgets().size() - 1; i >= 0; i--)
            column.removeWidgetAt(i);
        rows.clear();
        rowUids.clear();
        rowColors.clear();
        rowPeers.clear();
        if (root != null)
            root.removeWidget(column);
        column = null;
    }

    /** The header doubles as the drag handle: press, move, release. */
    private TextWidget header(MapTextFormat rowFormat, float pad) {
        MapTextFormat format = new MapTextFormat(Typeface.DEFAULT_BOLD,
                Math.max(1, Math.round(rowFormat.getFontSize() * 0.8f)));
        TextWidget header = new TextWidget("☰  Mesh peers", format);
        header.setColor(HEADER_COLOR);
        header.setBackground(HEADER_BACKING);
        header.setPadding(pad, pad * 0.6f, pad * 1.5f, pad * 0.6f);
        header.setMargins(0f, 0f, 0f, 2f);
        header.addOnPressListener(new MapWidget.OnPressListener() {
            @Override
            public void onMapWidgetPress(MapWidget widget, MotionEvent event) {
                downX = event.getX();
                downY = event.getY();
                startX = column.getPointX();
                startY = column.getPointY();
                dragging = true;
            }
        });
        header.addOnMoveListener(new MapWidget.OnMoveListener() {
            @Override
            public boolean onMapWidgetMove(MapWidget widget, MotionEvent event) {
                if (!dragging)
                    return false;
                column.setPoint(clampX(startX + event.getX() - downX), clampY(startY + event.getY() - downY));
                return true;
            }
        });
        header.addOnUnpressListener(new MapWidget.OnUnpressListener() {
            @Override
            public void onMapWidgetUnpress(MapWidget widget, MotionEvent event) {
                if (!dragging)
                    return;
                dragging = false;
                MapView map = MapView.getMapView();
                if (map != null && map.getWidth() > 0 && map.getHeight() > 0)
                    settings.setPosition(column.getPointX() / map.getWidth(), column.getPointY() / map.getHeight());
            }
        });
        return header;
    }

    private void place() {
        MapView map = MapView.getMapView();
        if (column == null || map == null)
            return;
        float x = settings.x(), y = settings.y();
        float px = Float.isNaN(x) ? DEFAULT_X_DP * density() : x * map.getWidth();
        float py = Float.isNaN(y) ? DEFAULT_Y_FRACTION * map.getHeight() : y * map.getHeight();
        column.setPoint(clampX(px), clampY(py));
    }

    private float clampX(float x) {
        MapView map = MapView.getMapView();
        float max = map != null ? map.getWidth() - Math.max(column.getWidth(), 48f) : x;
        return Math.max(0f, Math.min(x, max));
    }

    private float clampY(float y) {
        MapView map = MapView.getMapView();
        float max = map != null ? map.getHeight() - Math.max(column.getHeight(), 48f) : y;
        return Math.max(0f, Math.min(y, max));
    }

    private MapTextFormat format() {
        DisplayMetrics dm = pluginContext.getResources().getDisplayMetrics();
        int px = Math.round(settings.size().textSp * dm.scaledDensity);
        return new MapTextFormat(Typeface.DEFAULT_BOLD, px);
    }

    /**
     * Padding in proportion to the text. A fixed minimum row height was tried
     * first and made every size too large on the bench: the widgets' own
     * display scaling applied on top of it.
     */
    private float padding(MapTextFormat format) {
        return format.getFontSize() * 0.45f;
    }

    private float density() {
        return pluginContext.getResources().getDisplayMetrics().density;
    }
}
