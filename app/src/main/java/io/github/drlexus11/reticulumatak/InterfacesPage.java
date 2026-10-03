package io.github.drlexus11.reticulumatak;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.maps.MapView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pane's second page: Columba's configured interfaces, one switch each, so
 * a carrier can be taken out of the mesh and put back from ATAK while
 * diagnosing (docs/ColumbaInterface.md, "Interfaces").
 *
 * Columba decides: its guard refuses a switch that would cut this phone off,
 * and "allow ATAK control" off makes the page view only. On a backend that
 * stages switches, Apply restarts the mesh here, after a confirmation.
 */
final class InterfacesPage implements MeshSession.View {
    private static final int UP = 0xFF2ECC40;      // green, as a reachable peer
    private static final int PENDING = 0xFFFFB000; // amber: staged, not yet in effect
    private static final int DOWN = 0xFFFF4136;    // red: switched on, not running
    private static final int OFF = 0xFF8A8A8A;     // grey
    /** How long a switch that Columba accepted shows as asked, waiting for its snapshot. */
    private static final long ACCEPTED_MS = 10_000L;

    private final Context pluginContext;
    private final MeshSession session;
    private final View root;
    private final TextView header;
    private final TextView controlOff;
    private final Button apply;
    private final IfaceAdapter adapter = new IfaceAdapter();
    /** Switches asked and not yet answered: their switch stays disabled. */
    private final Set<Long> asking = new HashSet<>();
    /** Switches Columba accepted, shown as asked until a snapshot agrees (or ACCEPTED_MS). */
    private final Map<Long, Accepted> accepted = new HashMap<>();
    private MeshSnapshot last;

    private static final class Accepted {
        final boolean on;
        final long until;

        Accepted(boolean on, long until) {
            this.on = on;
            this.until = until;
        }
    }

    InterfacesPage(Context pluginContext, MeshSession session) {
        this.pluginContext = pluginContext;
        this.session = session;
        root = PluginLayoutInflater.inflate(pluginContext, R.layout.interfaces_page, null);
        header = root.findViewById(R.id.iface_header);
        controlOff = root.findViewById(R.id.iface_control);
        apply = root.findViewById(R.id.iface_apply);
        ((ListView) root.findViewById(R.id.iface_list)).setAdapter(adapter);
        apply.setOnClickListener(v -> confirmApply());
    }

    View view() {
        return root;
    }

    @Override
    public void onMesh(ColumbaMeshClient.State state, MeshSnapshot snapshot) {
        if (state != ColumbaMeshClient.State.CONNECTED || snapshot == null) {
            last = null;
            header.setText("Interfaces: Columba not connected");
            controlOff.setVisibility(View.GONE);
            apply.setVisibility(View.GONE);
            adapter.show(new ArrayList<MeshSnapshot.Iface>());
            return;
        }
        last = snapshot;
        long now = System.currentTimeMillis();
        for (MeshSnapshot.Iface f : snapshot.interfaces) {
            Accepted a = accepted.get(f.id);
            if (a != null && (a.on == f.enabled || now >= a.until))
                accepted.remove(f.id);
        }
        header.setText(InterfaceLines.header(snapshot));
        controlOff.setVisibility(snapshot.node.control ? View.GONE : View.VISIBLE);
        apply.setVisibility(InterfaceLines.canApply(snapshot) ? View.VISIBLE : View.GONE);
        adapter.show(snapshot.interfaces);
    }

    private boolean canSwitch() {
        return last != null && last.node.control && last.node.running;
    }

    private void toggle(MeshSnapshot.Iface f, boolean on) {
        asking.add(f.id);
        adapter.notifyDataSetChanged();
        session.client().setInterfaceEnabled(f.id, on, code -> {
            asking.remove(f.id);
            // Accepted (done, or staged for Apply): hold the switch where it was
            // put until Columba's snapshot catches up, so it does not flick back.
            if (code == 0 || code == 7)
                accepted.put(f.id, new Accepted(on, System.currentTimeMillis() + ACCEPTED_MS));
            adapter.notifyDataSetChanged();
            PeerActions.say(pluginContext, f.name + (on ? " on: " : " off: ") + InterfaceLines.result(code));
        });
    }

    private void confirmApply() {
        MapView map = MapView.getMapView();
        if (map == null)
            return;
        new AlertDialog.Builder(map.getContext())
                .setTitle(pluginContext.getString(R.string.iface_apply_title))
                .setMessage(pluginContext.getString(R.string.iface_apply_message))
                .setNegativeButton(pluginContext.getString(R.string.cancel), null)
                .setPositiveButton(pluginContext.getString(R.string.iface_apply), (d, w) -> {
                    apply.setEnabled(false);
                    session.client().applyInterfaces(code -> {
                        apply.setEnabled(true);
                        PeerActions.say(pluginContext,
                                code == 0 ? "Applying: mesh restarting here" : InterfaceLines.result(code));
                    });
                })
                .show();
    }

    private static int color(MeshSnapshot.Iface f, boolean live) {
        if (!live && InterfaceLines.isPending(f))
            return PENDING;
        if (!f.enabled)
            return OFF;
        return f.online ? UP : DOWN;
    }

    private final class IfaceAdapter extends BaseAdapter {
        private List<MeshSnapshot.Iface> ifaces = new ArrayList<>();

        void show(List<MeshSnapshot.Iface> ifaces) {
            this.ifaces = ifaces;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return ifaces.size();
        }

        @Override
        public Object getItem(int position) {
            return ifaces.get(position);
        }

        @Override
        public long getItemId(int position) {
            return ifaces.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView
                    : PluginLayoutInflater.inflate(pluginContext, R.layout.interface_row, null);
            final MeshSnapshot.Iface f = ifaces.get(position);
            boolean live = last != null && last.interfacesLive;
            ((TextView) row.findViewById(R.id.iface_line)).setText(InterfaceLines.row(f, live));
            GradientDrawable dot = (GradientDrawable) row.findViewById(R.id.iface_dot).getBackground().mutate();
            dot.setColor(color(f, live));

            Switch toggle = row.findViewById(R.id.iface_switch);
            // Unhooked while set from the snapshot: only a hand on it is a request.
            toggle.setOnCheckedChangeListener(null);
            Accepted a = accepted.get(f.id);
            toggle.setChecked(a != null ? a.on : f.enabled);
            toggle.setEnabled(canSwitch() && !asking.contains(f.id));
            toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean on) {
                    toggle(f, on);
                }
            });
            return row;
        }
    }
}
