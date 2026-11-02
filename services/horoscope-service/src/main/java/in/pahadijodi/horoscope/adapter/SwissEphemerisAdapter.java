package in.pahadijodi.horoscope.adapter;

import in.pahadijodi.horoscope.port.EphemerisPort;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production adapter placeholder (pj.horoscope.provider=real). Swiss Ephemeris is AGPL / commercial: a Professional
 * licence is required for closed-source SaaS (see docs/04 section 14.3, ADR pending). Until that decision is made and
 * the .se1 files are bundled, selecting this provider fails fast instead of silently returning wrong charts.
 */
@Component
@ConditionalOnProperty(name = "pj.horoscope.provider", havingValue = "real")
public class SwissEphemerisAdapter implements EphemerisPort {
    @Override
    public Chart compute(LocalDate date, LocalTime time, double latitude, double longitude, ZoneId zone) {
        throw new UnsupportedOperationException(
                "Swiss Ephemeris adapter is not bundled yet; use pj.horoscope.provider=fake (approximate engine)");
    }
}
