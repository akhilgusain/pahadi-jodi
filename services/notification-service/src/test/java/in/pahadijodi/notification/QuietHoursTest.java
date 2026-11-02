package in.pahadijodi.notification;

import static org.assertj.core.api.Assertions.assertThat;

import in.pahadijodi.notification.service.QuietHours;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuietHoursTest {
    // IST = UTC+5:30. 2026-06-10T18:00Z = 23:30 IST; 2026-06-10T06:30Z = 12:00 IST
    static final Instant NIGHT = Instant.parse("2026-06-10T18:00:00Z");
    static final Instant NOON = Instant.parse("2026-06-10T06:30:00Z");

    @Test
    void wrapsMidnight() {
        assertThat(QuietHours.isQuiet(22, 8, NIGHT)).isTrue();
        assertThat(QuietHours.isQuiet(22, 8, NOON)).isFalse();
        assertThat(QuietHours.isQuiet(22, 8, Instant.parse("2026-06-10T21:00:00Z"))).isTrue();   // 02:30 IST
        assertThat(QuietHours.isQuiet(22, 8, Instant.parse("2026-06-10T16:29:00Z"))).isFalse();  // 21:59 IST
        assertThat(QuietHours.isQuiet(22, 8, Instant.parse("2026-06-10T16:30:00Z"))).isTrue();   // 22:00 IST
        assertThat(QuietHours.isQuiet(22, 8, Instant.parse("2026-06-10T02:30:00Z"))).isFalse();  // 08:00 IST (end exclusive)
    }

    @Test
    void sameDayWindowAndDisabled() {
        assertThat(QuietHours.isQuiet(12, 14, NOON)).isTrue();
        assertThat(QuietHours.isQuiet(13, 14, NOON)).isFalse();
        assertThat(QuietHours.isQuiet(null, null, NIGHT)).isFalse();
        assertThat(QuietHours.isQuiet(5, 5, NIGHT)).isFalse();
    }

    @Test
    void endOfQuietIsNextOccurrenceOfEndHourInIst() {
        assertThat(QuietHours.endOfQuiet(8, NIGHT)).isEqualTo(Instant.parse("2026-06-11T02:30:00Z"));       // 08:00 IST next day
        assertThat(QuietHours.endOfQuiet(8, Instant.parse("2026-06-10T21:00:00Z"))).isEqualTo(Instant.parse("2026-06-11T02:30:00Z"));  // 02:30 IST -> same morning
    }
}
