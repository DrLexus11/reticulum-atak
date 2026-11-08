package io.github.drlexus11.reticulumatak;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** EXPERIMENTAL. A key's press: an older press's answer never clears a newer one. */
public class KeyHoldTest {
    private final Object first = new Object();
    private final Object second = new Object();

    @Test
    public void aFailedPressClearsItself() {
        KeyHold<Object> k = new KeyHold<>();
        assertTrue(k.press(first));
        assertTrue(k.failed(first));
        assertFalse(k.held());
    }

    @Test
    public void anOlderFailureDoesNotClearANewerPress() {
        KeyHold<Object> k = new KeyHold<>();
        k.press(first);
        assertSame(first, k.release());   // finger up before Columba answered
        assertTrue(k.press(second));      // pressed again
        assertFalse(k.failed(first));     // press 1's refusal arrives late
        assertTrue(k.held());
        assertSame(second, k.release());  // the lift still sends press 2's release
    }

    @Test
    public void aSecondPressWhileHeldIsRefused() {
        KeyHold<Object> k = new KeyHold<>();
        k.press(first);
        assertFalse(k.press(second));
        assertSame(first, k.release());
    }

    @Test
    public void releaseWithNothingHeldGivesNothing() {
        KeyHold<Object> k = new KeyHold<>();
        assertNull(k.release());
        assertFalse(k.failed(first));
        assertFalse(k.press(null));
    }

    @Test
    public void forgetClearsWithoutARelease() {
        KeyHold<Object> k = new KeyHold<>();
        k.press(first);
        k.forget();
        assertFalse(k.held());
        assertNull(k.release());
    }
}
