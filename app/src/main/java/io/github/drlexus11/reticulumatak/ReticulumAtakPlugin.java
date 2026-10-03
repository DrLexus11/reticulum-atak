package io.github.drlexus11.reticulumatak;

import android.content.Context;

import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.maps.MapView;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

/**
 * The plugin's entry point. On start it opens the mesh session (MeshSession:
 * Columba's mesh interface, ATAK's link to Columba, favourites and their map
 * overlay), which runs as long as the plugin does; the toolbar button opens the
 * pane (MeshPane: the mesh, and Columba's interfaces), a view on that session.
 */
public class ReticulumAtakPlugin implements IPlugin {

    private final IServiceController serviceController;
    private Context pluginContext;
    private final IHostUIService uiService;
    private ToolbarItem toolbarItem;
    private Pane pane;
    private MeshPane panel;
    private MeshSession session;

    public ReticulumAtakPlugin(IServiceController serviceController) {
        this.serviceController = serviceController;
        final PluginContextProvider ctxProvider = serviceController
                .getService(PluginContextProvider.class);
        if (ctxProvider != null) {
            pluginContext = ctxProvider.getPluginContext();
            pluginContext.setTheme(R.style.ATAKPluginTheme);
        }
        uiService = serviceController.getService(IHostUIService.class);
        if (pluginContext == null)
            return;

        toolbarItem = new ToolbarItem.Builder(
                pluginContext.getString(R.string.app_name),
                MarshalManager.marshal(
                        pluginContext.getResources().getDrawable(R.drawable.ic_mesh),
                        android.graphics.drawable.Drawable.class,
                        gov.tak.api.commons.graphics.Bitmap.class))
                // stable across restarts, so a toolbar the user rearranged
                // finds this button again
                .setIdentifier(pluginContext.getPackageName())
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        showPane();
                    }
                })
                .build();
    }

    @Override
    public void onStart() {
        if (uiService == null || toolbarItem == null)
            return;
        uiService.addToolbarItem(toolbarItem);
        // The session starts with the plugin, not the panel: the favourites
        // overlay is on the map whether or not the panel is open.
        MapView map = MapView.getMapView();
        session = new MeshSession(pluginContext, map != null ? map.getContext() : pluginContext,
                this::showPane);
        session.start();
    }

    @Override
    public void onStop() {
        if (session != null) {
            if (panel != null)
                panel.detach();
            session.stop();
            session = null;
        }
        panel = null;
        pane = null;
        if (uiService == null || toolbarItem == null)
            return;
        uiService.removeToolbarItem(toolbarItem);
    }

    private void showPane() {
        if (session == null)
            return;
        if (pane == null) {
            panel = new MeshPane(pluginContext, session);
            panel.attach();
            pane = new PaneBuilder(panel.view())
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                    .setMetaValue(Pane.PANE_NAME, pluginContext.getString(R.string.pane_name))
                    // Kept on ATAK's pane stack: a chat opened from here returns
                    // here when it closes (with MeshSession's chat-closed reopen
                    // as the fallback).
                    .setMetaValue(Pane.RETAIN, true)
                    .build();
        }
        if (!uiService.isPaneVisible(pane))
            uiService.showPane(pane, null);
    }
}
