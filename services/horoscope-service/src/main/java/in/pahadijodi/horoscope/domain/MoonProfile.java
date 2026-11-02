package in.pahadijodi.horoscope.domain;

/**
 * The only inputs Ashtakoot needs per person.
 *
 * @param rashi           Moon sign 1..12 (Aries..Pisces), sidereal
 * @param nakshatra       1..27 (Ashwini..Revati)
 * @param pada            1..4
 * @param degreeInRashi   0..30, Moon's degree inside its sign (used by Vashya for Sagittarius/Capricorn)
 */
public record MoonProfile(int rashi, int nakshatra, int pada, double degreeInRashi) {
    public MoonProfile {
        if (rashi < 1 || rashi > 12) throw new IllegalArgumentException("rashi must be 1..12");
        if (nakshatra < 1 || nakshatra > 27) throw new IllegalArgumentException("nakshatra must be 1..27");
        if (pada < 1 || pada > 4) throw new IllegalArgumentException("pada must be 1..4");
        if (degreeInRashi < 0 || degreeInRashi >= 30) throw new IllegalArgumentException("degreeInRashi must be in [0,30)");
    }

    /** Build from a sidereal Moon longitude in degrees. */
    public static MoonProfile fromLongitude(double siderealLon) {
        double lon = Zodiac.norm(siderealLon);
        return new MoonProfile(Zodiac.rashi(lon), Zodiac.nakshatra(lon), Zodiac.pada(lon), lon % 30.0);
    }

    /**
     * Build from nakshatra + pada only (stateless API). A sign holds exactly 9 padas, so the rashi is implied;
     * the in-sign degree is taken as the middle of the pada.
     */
    public static MoonProfile fromNakshatraPada(int nakshatra, int pada) {
        if (nakshatra < 1 || nakshatra > 27 || pada < 1 || pada > 4) throw new IllegalArgumentException("invalid nakshatra/pada");
        int padaIndex = (nakshatra - 1) * 4 + (pada - 1);       // 0..107
        int rashi = padaIndex / 9 + 1;
        double deg = (padaIndex % 9) * (30.0 / 9.0) + (30.0 / 18.0);
        return new MoonProfile(rashi, nakshatra, pada, deg);
    }
}
