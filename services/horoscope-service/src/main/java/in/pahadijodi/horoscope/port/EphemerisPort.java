package in.pahadijodi.horoscope.port;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Anti-corruption layer around whatever computes planetary positions (approximate Java now, Swiss Ephemeris later). */
public interface EphemerisPort {

    /**
     * @param time null = birth time unknown: noon is assumed, the chart is flagged low-confidence and has no ascendant
     */
    Chart compute(LocalDate date, LocalTime time, double latitude, double longitude, ZoneId zone);

    /** @param ascendantLongitude sidereal, null if birth time unknown */
    record Chart(double moonLongitude, double marsLongitude, Double ascendantLongitude,
                 String ayanamsa, String engineVersion, boolean lowConfidence) {}
}
