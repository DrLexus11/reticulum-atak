package io.github.drlexus11.reticulumatak;

/**
 * EXPERIMENTAL. One PTT key's press as the Talk page holds it: the token of the
 * press in flight, or none. Pure, so the rule that matters is tested: an answer
 * for an older press never touches a newer one. A failed press 1 arriving after
 * press 2 began must not clear press 2 -- the finger's lift would then send no
 * release, and the key would stay down in Columba until its hold cap.
 */
final class KeyHold<T> {
    private T token;

    boolean held() {
        return token != null;
    }

    /** Starts a press carrying [t]; false, and nothing changed, while a press is held. */
    boolean press(T t) {
        if (token != null || t == null)
            return false;
        token = t;
        return true;
    }

    /** The press that carried [t] was refused: cleared only if it is still the current one. */
    boolean failed(T t) {
        if (token == null || token != t)
            return false;
        token = null;
        return true;
    }

    /** Ends the current press; its token (kept until the release is answered), or null when none. */
    T release() {
        T t = token;
        token = null;
        return t;
    }

    /** Columba went away: it let go of everything with the binding. */
    void forget() {
        token = null;
    }
}
