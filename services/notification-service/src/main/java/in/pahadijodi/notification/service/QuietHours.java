package in.pahadijodi.notification.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Quiet-hours maths in IST. A window may wrap midnight (22 -> 8). start == end means "no quiet hours". */
public final class QuietHours {
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private QuietHours() {}

    public static boolean isQuiet(Integer start, Integer end, Instant now) {
        if (start == null || end == null || start.equals(end)) return false;
        int h = now.atZone(IST).getHour();
        return start < end ? (h >= start && h < end) : (h >= start || h < end);
    }

    /** First instant at which the quiet window is over (the next {@code end}:00 IST). */
    public static Instant endOfQuiet(Integer end, Instant now) {
        ZonedDateTime t = now.atZone(IST).withHour(end).withMinute(0).withSecond(0).withNano(0);
        if (!t.toInstant().isAfter(now)) t = t.plusDays(1);
        return t.toInstant();
    }
}
