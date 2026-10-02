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
 * The plugin's entry point: a toolbar button that opens the mesh panel
 * ([MeshPanel]), which reads the phone's node through Columba's mesh interface
 * (docs/ColumbaInterface.md).
 */
public class ReticulumAtakPlugin implements IPlugin {

    private final IServiceController serviceController;
    private Context pluginContext;
    private final IHostUIService uiService;
    private ToolbarItem toolbarItem;
    private Pane pane;
    private MeshPanel panel;

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
                        pluginContext.getResources().getDrawable(R.drawable.ic_launcher),
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
    }

    @Override
    public void onStop() {
        if (panel != null) {
            panel.stop();
            panel = null;
            pane = null;
        }
        if (uiService == null || toolbarItem == null)
            return;
        uiService.removeToolbarItem(toolbarItem);
    }

    private void showPane() {
        if (pane == null) {
            MapView map = MapView.getMapView();
            panel = new MeshPanel(pluginContext, map != null ? map.getContext() : pluginContext);
            panel.start();
            pane = new PaneBuilder(panel.view())
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                    .build();
        }
        if (!uiService.isPaneVisible(pane))
            uiService.showPane(pane, null);
    }
}
