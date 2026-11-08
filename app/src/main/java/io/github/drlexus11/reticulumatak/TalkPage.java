package io.github.drlexus11.reticulumatak;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Binder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.TextView;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.maps.MapView;

/**
 * EXPERIMENTAL. The pane's Talk page: a remote for Columba's live talk
 * (docs/ColumbaInterface.md, "Talk"), in the layout language of Columba's own
 * talk screen. Audio stays in Columba; this page keys it and shows what Columba
 * says.
 *
 * A banner, state first ("LISTENING" / "Relay · this phone"); when talk is off,
 * one button that opens Columba's talk screen; a column per key with its
 * selector position, channel, room, floor and last result; and the two keys
 * side by side, filling the rest, coloured by phase as Columba colours them.
 * Every line is pushed by Columba on change, so a press on Columba's screen
 * shows here and the other way round.
 *
 * Built for gloves: a key is held while any finger is down on it, wherever the
 * finger slides -- the pager cannot take the touch over -- and both keys can be
 * held at once. D-pad centre or Enter holds the focused key.
 *
 * A held key is let go on touch-up, on a cancelled touch, when the page leaves
 * the window or view, and when Columba goes away. Columba lets go on its own
 * when ATAK's process dies (the press's token) and after its maximum hold.
 */
final class TalkPage implements MeshSession.View, ColumbaMeshClient.TalkListener {
    /** ACTION_OPEN_TALK in Columba's MainActivityIntentHandler. */
    static final String ACTION_OPEN_TALK = "network.columba.app.ACTION_OPEN_TALK";
    static final String COLUMBA_ACTIVITY = "network.columba.app.MainActivity";

    // Banner: transmitting red, listening slate blue, off dark amber, mock error red.
    private static final int BANNER_TX = 0xFFC62828;
    private static final int BANNER_RX = 0xFF1E3A4C;
    private static final int BANNER_OFF = 0xFF4A3B00;
    private static final int BANNER_MOCK = 0xFF8C1D18;

    private static final String[] NAMES = {"A", "B"};

    /** One key: its column above and its PTT key below. */
    private final class Key {
        final int index;
        final View key;
        final TextView name;
        final TextView line;
        final GradientDrawable background = new GradientDrawable();
        final TextView position;
        final TextView label;
        final TextView room;
        final TextView floor;
        final TextView last;
        boolean held;
        /** Kept from the press until its release is sent: Columba watches it for ATAK's death. */
        Binder token;

        Key(int index, View card, View key) {
            this.index = index;
            this.key = key;
            name = key.findViewById(R.id.key_name);
            line = key.findViewById(R.id.key_line);
            position = card.findViewById(R.id.card_position);
            label = card.findViewById(R.id.card_label);
            room = card.findViewById(R.id.card_room);
            floor = card.findViewById(R.id.card_floor);
            last = card.findViewById(R.id.card_last);
            background.setCornerRadius(24f * density);
            key.setBackground(background);
            name.setText("PTT " + NAMES[index]);
            key.setOnTouchListener(this::onTouch);
            key.setOnKeyListener(this::onKey);
        }

        private boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // Gloved thumbs drift: no parent (the pager, ATAK's pane) may take the touch.
                    ViewParent parent = v.getParent();
                    if (parent != null)
                        parent.requestDisallowInterceptTouchEvent(true);
                    press(this);
                    return true;
                case MotionEvent.ACTION_UP:
                    letGo(this);
                    v.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    letGo(this);
                    return true;
                default:
                    // Moves and extra fingers: still held until the last finger is up.
                    return true;
            }
        }

        private boolean onKey(View v, int code, KeyEvent event) {
            if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER
                    && code != KeyEvent.KEYCODE_NUMPAD_ENTER && code != KeyEvent.KEYCODE_SPACE)
                return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0)
                press(this);
            else if (event.getAction() == KeyEvent.ACTION_UP)
                letGo(this);
            return true;
        }
    }

    private final Context pluginContext;
    private final MeshSession session;
    private final float density;
    private final View root;
    private final View banner;
    private final TextView title;
    private final TextView subtitle;
    private final TextView note;
    private final Button openColumba;
    private final Key[] keys;

    private TalkSnapshot talk;
    private boolean connected;
    private int watchCode;

    TalkPage(Context pluginContext, MeshSession session) {
        this.pluginContext = pluginContext;
        this.session = session;
        density = pluginContext.getResources().getDisplayMetrics().density;
        root = PluginLayoutInflater.inflate(pluginContext, R.layout.talk_page, null);
        banner = root.findViewById(R.id.talk_banner);
        title = root.findViewById(R.id.talk_title);
        subtitle = root.findViewById(R.id.talk_subtitle);
        note = root.findViewById(R.id.talk_note);
        openColumba = root.findViewById(R.id.talk_open_columba);
        keys = new Key[] {
                new Key(0, root.findViewById(R.id.talk_card_a), root.findViewById(R.id.talk_key_a)),
                new Key(1, root.findViewById(R.id.talk_card_b), root.findViewById(R.id.talk_key_b)),
        };
        openColumba.setOnClickListener(v -> openColumbaTalk());
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                hidden();
            }
        });
        session.client().setTalkListener(this);
        render();
    }

    View view() {
        return root;
    }

    /** The page went out of view (pane closed, another tab): let go of both keys. */
    void hidden() {
        for (Key k : keys)
            letGo(k);
    }

    @Override
    public void onMesh(ColumbaMeshClient.State state, MeshSnapshot snapshot) {
        boolean now = state == ColumbaMeshClient.State.CONNECTED;
        if (connected && !now) {
            // Columba went away: whatever it held for us, it let go of with the binding.
            for (Key k : keys) {
                k.held = false;
                k.token = null;
            }
            talk = null;
        }
        connected = now;
        render();
    }

    @Override
    public void onTalk(TalkSnapshot t) {
        talk = t;
        render();
    }

    @Override
    public void onTalkWatch(int code) {
        watchCode = code;
        render();
    }

    private void press(Key k) {
        if (k.held || !canPress())
            return;
        k.held = true;
        k.token = new Binder();
        final int index = k.index;
        session.client().talkPress(index, k.token, code -> {
            if (code != 0) {
                if (k.held) {
                    k.held = false;
                    k.token = null;
                }
                PeerActions.say(pluginContext, "PTT " + NAMES[index] + ": " + TalkLines.result(code));
            }
            render();
        });
        render();
    }

    private void letGo(Key k) {
        if (!k.held)
            return;
        k.held = false;
        // The token is dropped only once the release is answered: it must outlive the press.
        final Binder keep = k.token;
        k.token = null;
        session.client().talkRelease(k.index, code -> {
            if (keep != null)
                render();
        });
        render();
    }

    /** Opens Columba's talk screen, which starts talk; back to ATAK with Home or Recents. */
    private void openColumbaTalk() {
        String pkg = session.client().boundPackage();
        MapView map = MapView.getMapView();
        if (pkg == null || map == null)
            return;
        Intent open = new Intent(ACTION_OPEN_TALK)
                .setClassName(pkg, COLUMBA_ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            map.getContext().startActivity(open);
        } catch (ActivityNotFoundException | SecurityException e) {
            PeerActions.say(pluginContext, "Columba: cannot open Talk");
        }
    }

    private boolean supported() {
        return connected && session.client().has(ColumbaMeshClient.CAP_TALK);
    }

    private boolean canPress() {
        // Live talk running, or the mock on purpose: a press while live talk is
        // still starting would reach the mock's controller, not the air.
        return supported() && talk != null && talk.available
                && (talk.running || "mock".equals(talk.mode));
    }

    private void render() {
        if (!supported()) {
            banner(connected ? "NO TALK" : "NOT CONNECTED",
                    connected ? "This Columba has no talk" : "Columba not connected", BANNER_OFF);
            show(note, null);
            openColumba.setVisibility(View.GONE);
            for (Key k : keys)
                renderKey(k, null);
            return;
        }
        if (talk == null) {
            banner("TALK", "Waiting for Columba", BANNER_OFF);
            show(note, null);
            openColumba.setVisibility(View.GONE);
            for (Key k : keys)
                renderKey(k, null);
            return;
        }
        int color = !talk.available || (!talk.running && !"mock".equals(talk.mode)) ? BANNER_OFF
                : !talk.running ? BANNER_MOCK
                : talk.transmitting ? BANNER_TX
                : BANNER_RX;
        banner(TalkLines.title(talk), TalkLines.subtitle(talk), color);
        String warning = watchCode == 2 ? pluginContext.getString(R.string.talk_control_off) : TalkLines.note(talk);
        show(note, warning);
        openColumba.setVisibility(TalkLines.offerOpen(talk) && session.client().boundPackage() != null
                ? View.VISIBLE : View.GONE);
        for (Key k : keys)
            renderKey(k, talk.key(NAMES[k.index]));
    }

    private void banner(String head, String line, int color) {
        title.setText(head);
        subtitle.setText(line);
        banner.setBackgroundColor(color);
        banner.setContentDescription(head + ". " + line);
    }

    private void renderKey(Key k, TalkSnapshot.Key state) {
        if (state == null) {
            k.position.setText(NAMES[k.index]);
            k.label.setText("");
            k.room.setText("");
            k.floor.setText("");
            k.last.setText("");
            k.line.setText("Talk off");
            paint(k, TalkLines.Style.IDLE, false);
            k.key.setContentDescription("Push to talk " + NAMES[k.index] + ", talk off");
            return;
        }
        k.position.setText(TalkLines.position(state));
        k.label.setText(state.label);
        k.room.setText(TalkLines.room(state));
        k.floor.setText(TalkLines.floor(state));
        k.last.setText(TalkLines.last(state));
        k.line.setText(state.line);
        paint(k, TalkLines.style(state), canPress() || k.held);
        k.key.setContentDescription(TalkLines.keyDescription(state));
    }

    /** Columba's key colours, as a fixed high-contrast set that reads in sunlight and at night. */
    private static void paint(Key k, TalkLines.Style style, boolean enabled) {
        int bg;
        int fg;
        switch (style) {
            case TALKING:
                bg = 0xFFC62828;   // on air
                fg = 0xFFFFFFFF;
                break;
            case CALLING:
                bg = 0xFFFFB300;   // calling, connecting: do not speak yet
                fg = 0xFF000000;
                break;
            case REFUSED:
                bg = 0xFF5C1A1A;   // release to key again
                fg = 0xFFFFB4AB;
                break;
            case RECEIVING:
                bg = 0xFF2E7D32;   // someone else talking on this channel
                fg = 0xFFFFFFFF;
                break;
            default:
                bg = 0xFF37474F;   // idle: hold to talk
                fg = 0xFFFFFFFF;
                break;
        }
        k.background.setColor(bg);
        k.name.setTextColor(fg);
        k.line.setTextColor(fg);
        k.key.setEnabled(enabled);
        k.key.setAlpha(enabled ? 1f : 0.45f);
    }

    private static void show(TextView view, String text) {
        view.setText(text != null ? text : "");
        view.setVisibility(text != null ? View.VISIBLE : View.GONE);
    }
}
