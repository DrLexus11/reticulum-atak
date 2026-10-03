package io.github.drlexus11.reticulumatak;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The favourites overlay's size and position, in ATAK's preferences so they
 * survive restarts. Position is a fraction of the map's width and height, so it
 * stays put across rotation; NaN means "not moved yet": the default place.
 */
final class OverlaySettings {
    enum Size {
        // Operator, on the bench: the first Small (14) was already too large, so
        // it became Large and the others scale down from it.
        SMALL(9f), MEDIUM(11f), LARGE(14f);

        /** Text size in sp; rows grow and shrink with it (FavouritesOverlay). */
        final float textSp;

        Size(float textSp) {
            this.textSp = textSp;
        }
    }

    private static final String PREFS = "reticulumatak";
    private static final String KEY_SIZE = "overlay_size";
    private static final String KEY_X = "overlay_x";
    private static final String KEY_Y = "overlay_y";

    private final SharedPreferences prefs;

    OverlaySettings(Context hostContext) {
        prefs = hostContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    Size size() {
        try {
            return Size.valueOf(prefs.getString(KEY_SIZE, Size.MEDIUM.name()));
        } catch (IllegalArgumentException e) {
            return Size.MEDIUM;
        }
    }

    void setSize(Size size) {
        prefs.edit().putString(KEY_SIZE, size.name()).apply();
    }

    float x() {
        return prefs.getFloat(KEY_X, Float.NaN);
    }

    float y() {
        return prefs.getFloat(KEY_Y, Float.NaN);
    }

    void setPosition(float xFraction, float yFraction) {
        prefs.edit().putFloat(KEY_X, xFraction).putFloat(KEY_Y, yFraction).apply();
    }

    void resetPosition() {
        prefs.edit().remove(KEY_X).remove(KEY_Y).apply();
    }
}
