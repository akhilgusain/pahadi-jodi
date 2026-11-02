package in.pahadijodi.horoscope;

import static org.junit.jupiter.api.Assertions.*;

import in.pahadijodi.horoscope.adapter.ApproxEphemeris;
import in.pahadijodi.horoscope.domain.ManglikEvaluator;
import in.pahadijodi.horoscope.domain.Manglik.Level;
import in.pahadijodi.horoscope.domain.MoonProfile;
import in.pahadijodi.horoscope.domain.Zodiac;
import in.pahadijodi.horoscope.port.EphemerisPort.Chart;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ApproxEphemerisTest {
    static double angDiff(double a, double b) {
        double d = Math.abs(Zodiac.norm(a) - Zodiac.norm(b)) % 360;
        return d > 180 ? 360 - d : d;
    }

    @Test
    void moonMatchesMeeusWorkedExample47a() {
        // Meeus, Astronomical Algorithms ch.47 example: 1992 April 12, 0h TD (JDE 2448724.5): lambda = 133.162655 deg (before nutation)
        assertEquals(0, angDiff(133.162655, ApproxEphemeris.moonTropicalLongitude(2448724.5)), 0.1);
    }

    @Test
    void marsIsOppositeTheSunAtThe2003Opposition() {
        // Mars opposition 2003-08-28 ~ 04:00 UT: Mars' geocentric longitude equals Earth's heliocentric longitude
        double jde = 2452879.5 + 4.0 / 24;      // 2003-08-28 00:00 UT = JD 2452879.5
        double mars = ApproxEphemeris.marsGeocentricJ2000(jde);
        double earthHelio = ApproxEphemeris.earthHeliocentricLongitudeJ2000(jde);
        assertEquals(0, angDiff(mars, earthHelio), 1.0, "mars=" + mars + " earth=" + earthHelio);
    }

    @Test
    void lahiriAyanamsaIsPlausible() {
        assertEquals(23.853, ApproxEphemeris.lahiriAyanamsa(2451545.0), 1e-9);
        // 2026-01-01: Lahiri ayanamsa is about 24 deg 13' (24.22)
        assertEquals(24.22, ApproxEphemeris.lahiriAyanamsa(2461041.5), 0.05);
    }

    @Test
    void ascendantFormulaSanity() {
        // RAMC = 0 at the equator: ascendant is the east point, 90 deg
        double jd = 2451545.0;     // choose longitude so that local sidereal time = 0
        double gmst = Zodiac.norm(280.46061837 + 360.98564736629 * (jd - 2451545.0));
        assertEquals(90.0, ApproxEphemeris.ascendantTropical(jd, 0.0, -gmst), 1e-6);
    }

    @Test
    void chartForKnownBirthIsDeterministicAndConsistent() {
        ApproxEphemeris e = new ApproxEphemeris();
        Chart c1 = e.compute(LocalDate.of(1996, 4, 12), LocalTime.of(6, 30), 30.3165, 78.0322, ZoneId.of("Asia/Kolkata"));
        Chart c2 = e.compute(LocalDate.of(1996, 4, 12), LocalTime.of(6, 30), 30.3165, 78.0322, ZoneId.of("Asia/Kolkata"));
        assertEquals(c1, c2);
        assertFalse(c1.lowConfidence());
        assertNotNull(c1.ascendantLongitude());
        MoonProfile m = MoonProfile.fromLongitude(c1.moonLongitude());
        assertEquals(m.nakshatra(), (int) Math.floor(c1.moonLongitude() / (360.0 / 27)) + 1);
        // the Moon moves ~13 degrees in 24 h, so the next day must be well ahead
        Chart next = e.compute(LocalDate.of(1996, 4, 13), LocalTime.of(6, 30), 30.3165, 78.0322, ZoneId.of("Asia/Kolkata"));
        double move = Zodiac.norm(next.moonLongitude() - c1.moonLongitude());
        assertTrue(move > 11 && move < 15.5, "daily Moon motion " + move);
    }

    @Test
    void unknownBirthTimeGivesNoAscendantAndLowConfidence() {
        Chart c = new ApproxEphemeris().compute(LocalDate.of(1990, 1, 1), null, 29.5971, 79.6591, ZoneId.of("Asia/Kolkata"));
        assertTrue(c.lowConfidence());
        assertNull(c.ascendantLongitude());
    }

    @Test
    void manglikRules() {
        ManglikEvaluator ev = new ManglikEvaluator(true);
        // Mars in Aries (1), Lagna Aries (1): house 1 -> FULL, but own sign -> cancellation listed
        var full = ev.assess(1, 1, 5);
        assertEquals(Level.FULL, full.level());
        assertTrue(full.cancellations().contains("MARS_IN_OWN_OR_EXALTED_SIGN"));
        // Mars in Taurus, Lagna Aries: house 2 -> PARTIAL
        assertEquals(Level.PARTIAL, ev.assess(2, 1, 5).level());
        // Mars in Gemini (3rd): none; from Moon in Gemini house 1 -> PARTIAL via moon, none without moon reference
        assertEquals(Level.PARTIAL, ev.assess(3, 1, 3).level());
        assertEquals(Level.NONE, new ManglikEvaluator(false).assess(3, 1, 3).level());
        // unknown lagna: only the moon reference applies
        assertEquals(Level.PARTIAL, ev.assess(4, null, 4).level());
        assertNull(ev.assess(4, null, 4).marsHouseFromLagna());
        // pair status
        var none = ev.assess(3, 1, 6);
        assertEquals("BOTH_MANGLIK", ManglikEvaluator.pairStatus(full, full));
        assertEquals("NEITHER", ManglikEvaluator.pairStatus(none, none));
        assertEquals("MISMATCH_CANCELLED", ManglikEvaluator.pairStatus(full, none));
        assertEquals("MISMATCH", ManglikEvaluator.pairStatus(ev.assess(7, 1, 1), none));   // Mars in Libra, house 7, no cancellation
    }

    @Test
    void zodiacArithmetic() {
        assertEquals(1, Zodiac.rashi(0));
        assertEquals(12, Zodiac.rashi(359.99));
        assertEquals(1, Zodiac.nakshatra(0));
        assertEquals(27, Zodiac.nakshatra(359.99));
        assertEquals(4, Zodiac.pada(13.3));
        assertEquals(1, Zodiac.pada(13.4));
        assertEquals(2, Zodiac.house(2, 1));
        assertEquals(12, Zodiac.house(12, 1));
        assertEquals(12, Zodiac.house(1, 2));
        // nakshatra+pada <-> rashi mapping: Ashwini 1 is Aries, Revati 4 is Pisces
        assertEquals(1, MoonProfile.fromNakshatraPada(1, 1).rashi());
        assertEquals(12, MoonProfile.fromNakshatraPada(27, 4).rashi());
        assertEquals(2, MoonProfile.fromNakshatraPada(3, 2).rashi());   // Krittika pada 2 = Taurus
    }
}
