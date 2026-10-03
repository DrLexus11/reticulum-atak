package io.github.drlexus11.reticulumatak;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.atak.plugins.impl.PluginLayoutInflater;

/**
 * The plugin's pane: its pages under a tab each, and swipe between them (after
 * the SDK's helloworld TabViewDropDown, with views rather than fragments -- a
 * pane has no fragment manager of its own).
 *
 * Mesh (MeshPanel) always; Interfaces (InterfacesPage) only when the Columba it
 * is bound to offers switching. With one page there are no tabs: the pane looks
 * as it did before pages existed.
 */
final class MeshPane implements MeshSession.View {
    private final MeshSession session;
    private final MeshPanel mesh;
    private final InterfacesPage interfaces;
    private final View root;
    private final View tabs;
    private final TextView tabMesh;
    private final TextView tabInterfaces;
    private final ViewPager pager;
    private final Pages pages = new Pages();

    MeshPane(Context pluginContext, MeshSession session) {
        this.session = session;
        mesh = new MeshPanel(pluginContext, session);
        interfaces = new InterfacesPage(pluginContext, session);
        root = PluginLayoutInflater.inflate(pluginContext, R.layout.pane_layout, null);
        tabs = root.findViewById(R.id.tabs);
        tabMesh = root.findViewById(R.id.tab_mesh);
        tabInterfaces = root.findViewById(R.id.tab_interfaces);
        pager = root.findViewById(R.id.pager);
        pager.setAdapter(pages);
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                markTab(position);
            }
        });
        tabMesh.setOnClickListener(v -> pager.setCurrentItem(0));
        tabInterfaces.setOnClickListener(v -> pager.setCurrentItem(1));
        tabs.setVisibility(View.GONE);
        markTab(0);
    }

    View view() {
        return root;
    }

    /** The pages before the pane: each draws its own content, then the pane its tabs. */
    void attach() {
        session.addView(mesh);
        session.addView(interfaces);
        session.addView(this);
    }

    void detach() {
        session.removeView(this);
        session.removeView(interfaces);
        session.removeView(mesh);
    }

    @Override
    public void onMesh(ColumbaMeshClient.State state, MeshSnapshot snapshot) {
        boolean both;
        switch (state) {
            case CONNECTED:
                both = session.client().has(ColumbaMeshClient.CAP_INTERFACES);
                break;
            case CONNECTING:
            case LOST:
                // Kept while Columba is away for a moment -- Apply restarts it:
                // the page says "not connected" rather than vanishing under the
                // operator's hand.
                both = pages.count == 2;
                break;
            default:
                both = false;
        }
        if (both == (pages.count == 2))
            return;
        pages.count = both ? 2 : 1;
        pages.notifyDataSetChanged();
        tabs.setVisibility(both ? View.VISIBLE : View.GONE);
        markTab(pager.getCurrentItem());
    }

    /** The current tab is selected -- for screen readers too -- and the other dimmed. */
    private void markTab(int position) {
        tabMesh.setSelected(position == 0);
        tabMesh.setAlpha(position == 0 ? 1f : 0.45f);
        tabInterfaces.setSelected(position == 1);
        tabInterfaces.setAlpha(position == 1 ? 1f : 0.45f);
    }

    private final class Pages extends PagerAdapter {
        int count = 1;

        @Override
        public int getCount() {
            return count;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View page = position == 0 ? mesh.view() : interfaces.view();
            ViewParent parent = page.getParent();
            if (parent instanceof ViewGroup)
                ((ViewGroup) parent).removeView(page);
            container.addView(page);
            return page;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            if (object == mesh.view())
                return 0;
            return count == 2 ? 1 : POSITION_NONE;
        }
    }
}
