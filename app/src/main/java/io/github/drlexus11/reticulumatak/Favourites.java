package io.github.drlexus11.reticulumatak;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The operator's favourite peers, by ATAK contact UID, kept in ATAK's own
 * preferences so they survive restarts and plugin updates. Favourites lead
 * the list and get a row in the map overlay (FavouritesOverlay).
 */
final class Favourites {
    interface Listener {
        void onFavouritesChanged();
    }

    private static final String PREFS = "reticulumatak";
    private static final String KEY = "favourite_uids";

    private final SharedPreferences prefs;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    Favourites(Context hostContext) {
        prefs = hostContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    Set<String> all() {
        return Collections.unmodifiableSet(new HashSet<>(prefs.getStringSet(KEY, Collections.<String>emptySet())));
    }

    boolean contains(String uid) {
        return all().contains(uid);
    }

    void toggle(String uid) {
        Set<String> next = new HashSet<>(all());
        if (!next.remove(uid))
            next.add(uid);
        prefs.edit().putStringSet(KEY, next).apply();
        for (Listener l : listeners)
            l.onFavouritesChanged();
    }

    void addListener(Listener listener) {
        listeners.add(listener);
    }

    void removeListener(Listener listener) {
        listeners.remove(listener);
    }
}
