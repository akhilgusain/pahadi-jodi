package in.pahadijodi.horoscope.domain;

import in.pahadijodi.horoscope.domain.Manglik.Assessment;
import in.pahadijodi.horoscope.domain.Manglik.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Manglik (Kuja) dosha per docs/04 section 14.4: Mars in houses 1,4,7,8,12 from Lagna = FULL, house 2 = PARTIAL.
 * The Moon-reference check (Chandra Lagna) can only ever yield PARTIAL. Without a birth time there is no Lagna,
 * so only the Moon reference is available.
 *
 * <p>Cancellations implemented: Mars in own/exalted sign (Aries, Scorpio, Capricorn); Mars in Leo/Aquarius in the 4th
 * or 7th house. NOT implemented: Jupiter aspect (needs Jupiter), and the age-28 rule (time dependent, so it would make
 * a stored result go stale). The cancellation list is informational, the level is never silently lowered.
 */
public final class ManglikEvaluator {
    private static final Set<Integer> STRONG = Set.of(1, 4, 7, 8, 12);
    private static final Set<Integer> OWN_OR_EXALTED = Set.of(1, 8, 10);

    private final boolean includeMoonReference;

    public ManglikEvaluator(boolean includeMoonReference) { this.includeMoonReference = includeMoonReference; }

    /**
     * @param marsRashi  Mars sign 1..12
     * @param lagnaRashi ascendant sign, or null if unknown
     * @param moonRashi  Moon sign 1..12
     */
    public Assessment assess(int marsRashi, Integer lagnaRashi, int moonRashi) {
        Integer fromLagna = lagnaRashi == null ? null : Zodiac.house(marsRashi, lagnaRashi);
        int fromMoon = Zodiac.house(marsRashi, moonRashi);

        Level level = Level.NONE;
        if (fromLagna != null && STRONG.contains(fromLagna)) level = Level.FULL;
        else if (fromLagna != null && fromLagna == 2) level = Level.PARTIAL;
        else if (includeMoonReference && STRONG.contains(fromMoon)) level = Level.PARTIAL;

        List<String> cancellations = new ArrayList<>();
        if (level != Level.NONE) {
            if (OWN_OR_EXALTED.contains(marsRashi)) cancellations.add("MARS_IN_OWN_OR_EXALTED_SIGN");
            Integer house = fromLagna != null ? fromLagna : fromMoon;
            if ((marsRashi == 5 || marsRashi == 11) && (house == 4 || house == 7)) cancellations.add("MARS_IN_LEO_OR_AQUARIUS_IN_4_OR_7");
        }
        return new Assessment(level, fromLagna, fromMoon, List.copyOf(cancellations));
    }

    /** Pair compatibility: both Manglik or neither is fine; a one-sided Manglik is a warning unless cancelled. */
    public static String pairStatus(Assessment a, Assessment b) {
        boolean am = a.level() != Level.NONE, bm = b.level() != Level.NONE;
        if (am && bm) return "BOTH_MANGLIK";
        if (!am && !bm) return "NEITHER";
        Assessment mangli = am ? a : b;
        return mangli.cancellations().isEmpty() ? "MISMATCH" : "MISMATCH_CANCELLED";
    }
}
