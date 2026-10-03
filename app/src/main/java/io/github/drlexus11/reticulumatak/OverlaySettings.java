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
        // Offsets from ATAK's own map label size, so the overlay follows the
        // operator's label-size setting: Medium reads as ATAK's labels do.
        SMALL(-3), MEDIUM(0), LARGE(3);

        /** Added to ATAK's label font size (AtakMapView.getTextFormat). */
        final int offset;

        Size(int offset) {
            this.offset = offset;
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
