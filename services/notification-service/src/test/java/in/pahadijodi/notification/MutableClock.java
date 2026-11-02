package in.pahadijodi.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock the test can move, to exercise quiet hours, daily caps and retry backoff deterministically. */
public class MutableClock extends Clock {
    private volatile Instant now;

    public MutableClock(Instant start) { this.now = start; }
    public void set(Instant t) { this.now = t; }
    public void advance(Duration d) { this.now = now.plus(d); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
}
