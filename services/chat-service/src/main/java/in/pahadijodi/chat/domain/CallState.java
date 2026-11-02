package in.pahadijodi.chat.domain;

/**
 * REQUESTED -> ACCEPTED | DECLINED | CANCELLED (caller hangs up while ringing) | MISSED (ring timeout)
 * ACCEPTED  -> ENDED
 */
public enum CallState {
    REQUESTED, ACCEPTED, DECLINED, CANCELLED, MISSED, ENDED;

    public boolean active() { return this == REQUESTED || this == ACCEPTED; }
}
