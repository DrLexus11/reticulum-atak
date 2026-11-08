package io.github.drlexus11.reticulumatak;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Binder;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.atak.plugins.impl.PluginLayoutInflater;

/**
 * EXPERIMENTAL. The pane's PTT page: a remote for Columba's live talk
 * (docs/ColumbaInterface.md, "Talk"). Audio stays in Columba; this page only
 * keys it and shows what Columba says.
 *
 * One big hold-to-talk button for the selected key, an A/B selector, the
 * selected key's channel and room, who has the floor, and Columba's status
 * line. Every line is pushed by Columba on change, so a press on Columba's own
 * talk screen shows here and the other way round.
 *
 * A held key is let go on touch-up, on a cancelled touch (another window took
 * it), when the page leaves the window, and when Columba goes away. Columba
 * lets go on its own when ATAK's process dies (the press's token) and after its
 * maximum hold, so a lost release here cannot leave a floor open.
 */
final class TalkPage implements MeshSession.View, ColumbaMeshClient.TalkListener {
    private static final int IDLE = 0xFF3A3A3A;
    private static final int WAIT = 0xFFFFB000;   // amber: calling, connecting
    private static final int TALK = 0xFFD7263D;   // red: on air
    private static final int RX = 0xFF2E7D32;     // green: someone else talking here
    private static final int OFF = 0xFF202020;

    private static final String[] NAMES = {"A", "B"};

    private final Context pluginContext;
    private final MeshSession session;
    private final View root;
    private final TextView header;
    private final TextView note;
    private final TextView control;
    private final Button keyA;
    private final Button keyB;
    private final TextView channel;
    private final TextView floor;
    private final TextView last;
    private final Button ptt;
    private final GradientDrawable pttBackground = new GradientDrawable();

    /** 0 = A, 1 = B. */
    private int selected;
    /** The key this page holds down in Columba, or -1. */
    private int held = -1;
    private Binder token;
    private TalkSnapshot talk;
    private boolean connected;
    private int watchCode;

    TalkPage(Context pluginContext, MeshSession session) {
        this.pluginContext = pluginContext;
        this.session = session;
        root = PluginLayoutInflater.inflate(pluginContext, R.layout.talk_page, null);
        header = root.findViewById(R.id.talk_header);
        note = root.findViewById(R.id.talk_note);
        control = root.findViewById(R.id.talk_control);
        keyA = root.findViewById(R.id.talk_key_a);
        keyB = root.findViewById(R.id.talk_key_b);
        channel = root.findViewById(R.id.talk_channel);
        floor = root.findViewById(R.id.talk_floor);
        last = root.findViewById(R.id.talk_last);
        ptt = root.findViewById(R.id.talk_ptt);
        pttBackground.setCornerRadius(18f * pluginContext.getResources().getDisplayMetrics().density);
        ptt.setBackground(pttBackground);
        keyA.setOnClickListener(v -> select(0));
        keyB.setOnClickListener(v -> select(1));
        ptt.setOnTouchListener(this::onPttTouch);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                letGo();
            }
        });
        session.client().setTalkListener(this);
        render();
    }

    View view() {
        return root;
    }

    /** The page went out of view (pane closed, another tab): let go. */
    void hidden() {
        letGo();
    }

    @Override
    public void onMesh(ColumbaMeshClient.State state, MeshSnapshot snapshot) {
        boolean now = state == ColumbaMeshClient.State.CONNECTED;
        if (connected && !now) {
            // Columba went away: whatever it held for us, it let go of with the binding.
            held = -1;
            token = null;
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

    private void select(int key) {
        if (held >= 0)
            return;   // never switch keys mid-transmission
        selected = key;
        render();
    }

    private boolean onPttTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                press();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                letGo();
                v.performClick();
                return true;
            default:
                return true;
        }
    }

    private void press() {
        if (held >= 0 || !canPress())
            return;
        final int key = selected;
        held = key;
        token = new Binder();
        session.client().talkPress(key, token, code -> {
            if (code != 0) {
                if (held == key) {
                    held = -1;
                    token = null;
                }
                PeerActions.say(pluginContext, "PTT " + NAMES[key] + ": " + TalkLines.result(code));
            }
            render();
        });
        render();
    }

    private void letGo() {
        if (held < 0)
            return;
        final int key = held;
        held = -1;
        // The token is dropped only after the release is sent: it must outlive the press.
        final Binder keep = token;
        token = null;
        session.client().talkRelease(key, code -> {
            if (keep == null)
                return;
            render();
        });
        render();
    }

    private boolean canPress() {
        return connected && talk != null && talk.available
                && session.client().has(ColumbaMeshClient.CAP_TALK);
    }

    private void render() {
        boolean supported = connected && session.client().has(ColumbaMeshClient.CAP_TALK);
        if (!supported) {
            header.setText(connected ? "Talk: this Columba has no talk" : "Talk: Columba not connected");
            show(note, null);
            show(control, null);
            keyA.setText("A");
            keyB.setText("B");
            channel.setText("");
            floor.setText("");
            last.setText("");
            pttState("PTT unavailable", OFF, false);
            return;
        }
        if (talk == null) {
            header.setText("Talk: waiting for Columba");
            pttState("PTT", OFF, false);
            return;
        }
        header.setText(TalkLines.header(talk));
        show(note, TalkLines.note(talk));
        show(control, watchCode == 2 ? pluginContext.getString(R.string.talk_control_off) : null);
        TalkSnapshot.Key a = talk.key("A");
        TalkSnapshot.Key b = talk.key("B");
        keyA.setText(a != null ? "A · " + a.label : "A");
        keyB.setText(b != null ? "B · " + b.label : "B");
        mark(keyA, selected == 0);
        mark(keyB, selected == 1);
        keyA.setEnabled(held < 0);
        keyB.setEnabled(held < 0);
        TalkSnapshot.Key k = selected == 0 ? a : b;
        if (k == null) {
            channel.setText("");
            floor.setText("");
            last.setText("");
            pttState("PTT", OFF, false);
            return;
        }
        channel.setText(TalkLines.channel(k));
        floor.setText(TalkLines.floor(k));
        last.setText(TalkLines.last(k));
        int color = k.talking() ? TALK
                : k.waiting() ? WAIT
                : k.floor != null ? RX
                : IDLE;
        // Columba's own words for the key: a press here shows there and the other way round.
        pttState(k.line, color, canPress() || held >= 0);
    }

    private void pttState(String text, int color, boolean enabled) {
        ptt.setText(text);
        pttBackground.setColor(color);
        ptt.setEnabled(enabled);
        ptt.setAlpha(enabled ? 1f : 0.5f);
    }

    private static void show(TextView view, String text) {
        view.setText(text != null ? text : "");
        view.setVisibility(text != null ? View.VISIBLE : View.GONE);
    }

    /** Selected and lit, or not selected and dimmed: state that accessibility services read. */
    private static void mark(View view, boolean on) {
        view.setSelected(on);
        view.setAlpha(on ? 1f : 0.45f);
    }
}
