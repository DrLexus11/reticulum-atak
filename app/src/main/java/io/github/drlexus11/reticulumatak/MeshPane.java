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

import java.util.ArrayList;
import java.util.List;

/**
 * The plugin's pane: its pages under a tab each, and swipe between them (after
 * the SDK's helloworld TabViewDropDown, with views rather than fragments -- a
 * pane has no fragment manager of its own).
 *
 * Mesh (MeshPanel) always; Interfaces (InterfacesPage) only when the Columba it
 * is bound to offers switching; Talk (TalkPage, EXPERIMENTAL) only when it
 * offers talk. With one page there are no tabs: the pane looks as it did
 * before pages existed.
 *
 * The Talk page in view -- pane open and the Talk tab current -- is what tells
 * Columba to keep its live talk on for the remote
 * (ColumbaMeshClient.setTalkWanted); anything else lets it go.
 */
final class MeshPane implements MeshSession.View {
    private enum Page { MESH, INTERFACES, TALK }

    private final MeshSession session;
    private final MeshPanel mesh;
    private final InterfacesPage interfaces;
    private final TalkPage talk;
    private final View root;
    private final View tabs;
    private final TextView tabMesh;
    private final TextView tabInterfaces;
    private final TextView tabTalk;
    private final ViewPager pager;
    private final Pages pages = new Pages();
    private boolean inWindow;

    MeshPane(Context pluginContext, MeshSession session) {
        this.session = session;
        mesh = new MeshPanel(pluginContext, session);
        interfaces = new InterfacesPage(pluginContext, session);
        talk = new TalkPage(pluginContext, session);
        root = PluginLayoutInflater.inflate(pluginContext, R.layout.pane_layout, null);
        tabs = root.findViewById(R.id.tabs);
        tabMesh = root.findViewById(R.id.tab_mesh);
        tabInterfaces = root.findViewById(R.id.tab_interfaces);
        tabTalk = root.findViewById(R.id.tab_talk);
        pager = root.findViewById(R.id.pager);
        pager.setAdapter(pages);
        pager.setOffscreenPageLimit(2);
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                markTabs();
                talkInView();
            }
        });
        tabMesh.setOnClickListener(v -> show(Page.MESH));
        tabInterfaces.setOnClickListener(v -> show(Page.INTERFACES));
        tabTalk.setOnClickListener(v -> show(Page.TALK));
        // In ATAK's window means the pane is open; out of it, closed.
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                inWindow = true;
                talkInView();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                inWindow = false;
                talkInView();
            }
        });
        tabs.setVisibility(View.GONE);
        markTabs();
    }

    View view() {
        return root;
    }

    /** The pages before the pane: each draws its own content, then the pane its tabs. */
    void attach() {
        session.addView(mesh);
        session.addView(interfaces);
        session.addView(talk);
        session.addView(this);
    }

    void detach() {
        talk.hidden();
        session.client().setTalkWanted(false);
        session.removeView(this);
        session.removeView(talk);
        session.removeView(interfaces);
        session.removeView(mesh);
    }

    @Override
    public void onMesh(ColumbaMeshClient.State state, MeshSnapshot snapshot) {
        List<Page> want = new ArrayList<>();
        want.add(Page.MESH);
        switch (state) {
            case CONNECTED:
                if (session.client().has(ColumbaMeshClient.CAP_INTERFACES))
                    want.add(Page.INTERFACES);
                if (session.client().has(ColumbaMeshClient.CAP_TALK))
                    want.add(Page.TALK);
                break;
            case CONNECTING:
            case LOST:
                // Kept while Columba is away for a moment -- Apply restarts it:
                // the page says "not connected" rather than vanishing under the
                // operator's hand.
                want = new ArrayList<>(pages.list);
                break;
            default:
                break;
        }
        if (!want.equals(pages.list)) {
            Page current = pages.at(pager.getCurrentItem());
            pages.list.clear();
            pages.list.addAll(want);
            pages.notifyDataSetChanged();
            int keep = pages.list.indexOf(current);
            pager.setCurrentItem(keep >= 0 ? keep : 0, false);
            tabs.setVisibility(pages.list.size() > 1 ? View.VISIBLE : View.GONE);
            tabInterfaces.setVisibility(pages.list.contains(Page.INTERFACES) ? View.VISIBLE : View.GONE);
            tabTalk.setVisibility(pages.list.contains(Page.TALK) ? View.VISIBLE : View.GONE);
            markTabs();
        }
        // On every state: a Columba that came back offers talk again.
        talkInView();
    }

    private void show(Page page) {
        int index = pages.list.indexOf(page);
        if (index >= 0)
            pager.setCurrentItem(index);
    }

    /** Tell Columba whether the Talk page is in view; let go of a held key when it is not. */
    private void talkInView() {
        boolean inView = inWindow && pages.at(pager.getCurrentItem()) == Page.TALK;
        if (!inView)
            talk.hidden();
        session.client().setTalkWanted(inView);
    }

    /** The current tab is selected -- for screen readers too -- and the others dimmed. */
    private void markTabs() {
        Page current = pages.at(pager.getCurrentItem());
        mark(tabMesh, current == Page.MESH);
        mark(tabInterfaces, current == Page.INTERFACES);
        mark(tabTalk, current == Page.TALK);
    }

    private static void mark(TextView tab, boolean on) {
        tab.setSelected(on);
        tab.setAlpha(on ? 1f : 0.45f);
    }

    private View viewOf(Page page) {
        switch (page) {
            case INTERFACES:
                return interfaces.view();
            case TALK:
                return talk.view();
            default:
                return mesh.view();
        }
    }

    private final class Pages extends PagerAdapter {
        final List<Page> list = new ArrayList<>();

        Pages() {
            list.add(Page.MESH);
        }

        Page at(int position) {
            return position >= 0 && position < list.size() ? list.get(position) : Page.MESH;
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View page = viewOf(list.get(position));
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
            for (int i = 0; i < list.size(); i++) {
                if (viewOf(list.get(i)) == object)
                    return i;
            }
            return POSITION_NONE;
        }
    }
}
