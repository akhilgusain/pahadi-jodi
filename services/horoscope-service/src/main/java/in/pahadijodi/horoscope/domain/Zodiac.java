package in.pahadijodi.horoscope.domain;

/** Sidereal zodiac arithmetic. */
public final class Zodiac {
    public static final double NAKSHATRA_SPAN = 360.0 / 27.0;   // 13°20'
    public static final double PADA_SPAN = NAKSHATRA_SPAN / 4.0; // 3°20'

    private Zodiac() {}

    public static double norm(double deg) {
        double d = deg % 360.0;
        return d < 0 ? d + 360.0 : d;
    }

    public static int rashi(double lon) { return (int) Math.floor(norm(lon) / 30.0) + 1; }

    public static int nakshatra(double lon) { return Math.min(27, (int) Math.floor(norm(lon) / NAKSHATRA_SPAN) + 1); }

    public static int pada(double lon) {
        double within = norm(lon) % NAKSHATRA_SPAN;
        return Math.min(4, (int) Math.floor(within / PADA_SPAN) + 1);
    }

    /** Whole-sign house (1..12) of a planet in {@code planetRashi}, counted from {@code referenceRashi}. */
    public static int house(int planetRashi, int referenceRashi) {
        return Math.floorMod(planetRashi - referenceRashi, 12) + 1;
    }
}
