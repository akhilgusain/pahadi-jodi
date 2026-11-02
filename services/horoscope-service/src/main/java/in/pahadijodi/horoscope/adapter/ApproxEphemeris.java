package in.pahadijodi.horoscope.adapter;

import in.pahadijodi.horoscope.domain.Zodiac;
import in.pahadijodi.horoscope.port.EphemerisPort;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Pure-Java approximate sidereal (Lahiri) engine. No native code, no licence issues, deterministic.
 *
 * <p><b>Accuracy limits, read before trusting a chart:</b>
 * <ul>
 *   <li>Moon: Meeus ch.47 longitude series truncated to the 20 largest periodic terms plus the 3 additive terms.
 *       Error is typically below 0.05 degrees (about 3 arc-minutes), i.e. a few hours of Moon motion cannot be
 *       confused, but a birth within ~5 minutes of a nakshatra/pada/rashi boundary can land on the wrong side.
 *       The Moon moves ~13 degrees/day, so the answer is very sensitive to the birth <em>time</em>.</li>
 *   <li>Mars: Standish (JPL) approximate Keplerian elements for 1800-2050 (Mars relative to Earth-Moon barycentre,
 *       geometric positions, no light-time/aberration). Error is on the order of 0.1-0.5 degrees. Fine for a sign and
 *       house, not for degree-level work.</li>
 *   <li>Lahiri ayanamsa: 23.853 degrees at J2000 + mean precession 50.29"/yr (linear). Good to ~0.01 degree for
 *       1900-2100. No nutation correction.</li>
 *   <li>Ascendant: standard spherical formula from local sidereal time; sensitive to birth time (4 minutes = 1 degree).</li>
 *   <li>Delta-T is a constant 69 s (valid ~2010-2030); UT is used for the clock, TD for the series.</li>
 *   <li>Time zone offsets come from java.time (IANA tz database), which handles historic India standard time
 *       but not hand-set local mean time before 1906.</li>
 * </ul>
 * The production adapter is Swiss Ephemeris (see {@link SwissEphemerisAdapter}).
 */
@Component
@ConditionalOnProperty(name = "pj.horoscope.provider", havingValue = "fake", matchIfMissing = true)
public class ApproxEphemeris implements EphemerisPort {
    public static final String ENGINE_VERSION = "approx-meeus47t20-standish-1";
    static final double DELTA_T_SECONDS = 69.0;
    static final double LAHIRI_AT_J2000 = 23.853;
    static final double PRECESSION_DEG_PER_YEAR = 50.2907 / 3600.0;

    // Meeus table 47.A (longitude), 20 largest terms: D, M, M', F, coefficient in 1e-6 degrees
    private static final int[][] TERMS = {
            {0, 0, 1, 0, 6288774}, {2, 0, -1, 0, 1274027}, {2, 0, 0, 0, 658314}, {0, 0, 2, 0, 213618},
            {0, 1, 0, 0, -185116}, {0, 0, 0, 2, -114332}, {2, 0, -2, 0, 58793}, {2, -1, -1, 0, 57066},
            {2, 0, 1, 0, 53322}, {2, -1, 0, 0, 45758}, {0, 1, -1, 0, -40923}, {1, 0, 0, 0, -34720},
            {0, 1, 1, 0, -30383}, {2, 0, 0, -2, 15327}, {0, 0, 1, 2, -12528}, {0, 0, 1, -2, 10980},
            {4, 0, -1, 0, 10675}, {0, 0, 3, 0, 10034}, {4, 0, -2, 0, 8548}, {2, 1, -1, 0, -7888}};

    @Override
    public Chart compute(LocalDate date, LocalTime time, double lat, double lon, ZoneId zone) {
        boolean unknown = time == null;
        ZonedDateTime local = ZonedDateTime.of(date, unknown ? LocalTime.NOON : time, zone);
        double jd = local.toInstant().getEpochSecond() / 86400.0 + 2440587.5;     // UT
        double jde = jd + DELTA_T_SECONDS / 86400.0;                              // TD

        double ayan = lahiriAyanamsa(jd);
        double moon = Zodiac.norm(moonTropicalLongitude(jde) - ayan);
        double mars = Zodiac.norm(marsGeocentricJ2000(jde) - LAHIRI_AT_J2000);
        Double asc = unknown ? null : Zodiac.norm(ascendantTropical(jd, lat, lon) - ayan);
        return new Chart(moon, mars, asc, "LAHIRI", ENGINE_VERSION, unknown);
    }

    // ---------------------------------------------------------------- ayanamsa

    /** Lahiri (Chitra-paksha) ayanamsa in degrees at the given Julian Day. */
    public static double lahiriAyanamsa(double jd) {
        double years = (jd - 2451545.0) / 365.25;
        return LAHIRI_AT_J2000 + PRECESSION_DEG_PER_YEAR * years;
    }

    // ---------------------------------------------------------------- Moon

    /** Geocentric tropical (mean equinox of date) Moon longitude in degrees; truncated Meeus ch.47. */
    public static double moonTropicalLongitude(double jde) {
        double t = (jde - 2451545.0) / 36525.0;
        double lp = Zodiac.norm(218.3164477 + 481267.88123421 * t - 0.0015786 * t * t + t * t * t / 538841.0 - t * t * t * t / 65194000.0);
        double d = Zodiac.norm(297.8501921 + 445267.1114034 * t - 0.0018819 * t * t + t * t * t / 545868.0);
        double m = Zodiac.norm(357.5291092 + 35999.0502909 * t - 0.0001536 * t * t);
        double mp = Zodiac.norm(134.9633964 + 477198.8675055 * t + 0.0087414 * t * t + t * t * t / 69699.0);
        double f = Zodiac.norm(93.2720950 + 483202.0175233 * t - 0.0036539 * t * t);
        double a1 = Zodiac.norm(119.75 + 131.849 * t);
        double a2 = Zodiac.norm(53.09 + 479264.290 * t);
        double e = 1.0 - 0.002516 * t - 0.0000074 * t * t;

        double sum = 0;
        for (int[] c : TERMS) {
            double arg = Math.toRadians(c[0] * d + c[1] * m + c[2] * mp + c[3] * f);
            double ef = c[1] == 0 ? 1.0 : (Math.abs(c[1]) == 1 ? e : e * e);
            sum += c[4] * ef * Math.sin(arg);
        }
        sum += 3958 * Math.sin(Math.toRadians(a1)) + 1962 * Math.sin(Math.toRadians(lp - f)) + 318 * Math.sin(Math.toRadians(a2));
        return Zodiac.norm(lp + sum / 1_000_000.0);
    }

    // ---------------------------------------------------------------- Mars (Standish 1800-2050)

    // {a0, aRate, e0, eRate, I0, IRate, L0, LRate, peri0, periRate, node0, nodeRate}; angles in degrees, rates per century
    private static final double[] MARS = {1.52371034, 0.00001847, 0.09339410, 0.00007882, 1.84969142, -0.00813131,
            -4.55343205, 19140.30268499, -23.94362959, 0.44441088, 49.55953891, -0.29257343};
    private static final double[] EARTH = {1.00000261, 0.00000562, 0.01671123, -0.00004392, -0.00001531, -0.01294668,
            100.46457166, 35999.37244981, 102.93768193, 0.32327364, 0.0, 0.0};

    /** Heliocentric ecliptic J2000 position (AU) from approximate Keplerian elements. */
    static double[] heliocentric(double[] el, double t) {
        double a = el[0] + el[1] * t, e = el[2] + el[3] * t, inc = Math.toRadians(el[4] + el[5] * t);
        double l = el[6] + el[7] * t, peri = el[8] + el[9] * t, node = el[10] + el[11] * t;
        double omega = Math.toRadians(peri - node), bigOmega = Math.toRadians(node);
        double m = Math.toRadians(normSigned(l - peri));
        double ea = m + e * Math.sin(m);                       // Kepler: Newton iterations
        for (int i = 0; i < 12; i++) ea -= (ea - e * Math.sin(ea) - m) / (1 - e * Math.cos(ea));
        double xp = a * (Math.cos(ea) - e), yp = a * Math.sqrt(1 - e * e) * Math.sin(ea);
        double cw = Math.cos(omega), sw = Math.sin(omega), co = Math.cos(bigOmega), so = Math.sin(bigOmega);
        double ci = Math.cos(inc), si = Math.sin(inc);
        double x = (cw * co - sw * so * ci) * xp + (-sw * co - cw * so * ci) * yp;
        double y = (cw * so + sw * co * ci) * xp + (-sw * so + cw * co * ci) * yp;
        double z = (sw * si) * xp + (cw * si) * yp;
        return new double[]{x, y, z};
    }

    private static double normSigned(double deg) {
        double d = Zodiac.norm(deg);
        return d > 180 ? d - 360 : d;
    }

    /** Geocentric ecliptic longitude of Mars in the J2000 frame, degrees. */
    public static double marsGeocentricJ2000(double jde) {
        double t = (jde - 2451545.0) / 36525.0;
        double[] mars = heliocentric(MARS, t), earth = heliocentric(EARTH, t);
        return Zodiac.norm(Math.toDegrees(Math.atan2(mars[1] - earth[1], mars[0] - earth[0])));
    }

    /** Heliocentric ecliptic J2000 longitude of Earth, degrees (Sun's geocentric longitude is this + 180). */
    public static double earthHeliocentricLongitudeJ2000(double jde) {
        double[] earth = heliocentric(EARTH, (jde - 2451545.0) / 36525.0);
        return Zodiac.norm(Math.toDegrees(Math.atan2(earth[1], earth[0])));
    }

    // ---------------------------------------------------------------- Ascendant

    /** Tropical ascendant longitude in degrees for UT Julian Day, geographic latitude and east longitude. */
    public static double ascendantTropical(double jd, double latDeg, double lonEastDeg) {
        double t = (jd - 2451545.0) / 36525.0;
        double gmst = Zodiac.norm(280.46061837 + 360.98564736629 * (jd - 2451545.0) + 0.000387933 * t * t - t * t * t / 38710000.0);
        double ramc = Math.toRadians(Zodiac.norm(gmst + lonEastDeg));
        double eps = Math.toRadians(23.439291 - 0.0130042 * t);
        double lat = Math.toRadians(latDeg);
        double y = Math.cos(ramc);
        double x = -(Math.sin(ramc) * Math.cos(eps) + Math.tan(lat) * Math.sin(eps));
        return Zodiac.norm(Math.toDegrees(Math.atan2(y, x)));
    }
}
