package in.pahadijodi.horoscope.guna;

import in.pahadijodi.horoscope.domain.MoonProfile;
import in.pahadijodi.horoscope.guna.RuleSet.Friendship;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Ashtakoot Guna Milan, 36 points (docs/04 section 14.5). Pure and stateless: all tables come from the RuleSet.
 * "Boy" and "girl" are roles chosen by the caller (the algorithm is directional for Varna, Tara-by-direction and Gana).
 */
public class GunaMilanCalculator {
    private static final Set<Integer> TARA_INAUSPICIOUS = Set.of(3, 5, 7);
    private static final Set<Integer> BHAKOOT_BAD = Set.of(2, 12, 5, 9, 6, 8);

    private final RuleSet rules;
    private final boolean nadiCancellation;

    public GunaMilanCalculator(RuleSet rules, boolean nadiCancellation) {
        this.rules = rules;
        this.nadiCancellation = nadiCancellation;
    }

    public GunaMilanCalculator(RuleSet rules) { this(rules, true); }

    public GunaMilanReport compute(MoonProfile boy, MoonProfile girl) {
        List<KootaScore> s = new ArrayList<>(8);
        s.add(varna(boy, girl));
        s.add(vashya(boy, girl));
        s.add(tara(boy, girl));
        s.add(yoni(boy, girl));
        s.add(grahaMaitri(boy, girl));
        s.add(gana(boy, girl));
        KootaScore bhakoot = bhakoot(boy, girl);
        s.add(bhakoot);
        KootaScore nadi = nadi(boy, girl);
        s.add(nadi);

        double total = s.stream().mapToDouble(KootaScore::points).sum();
        List<String> flags = new ArrayList<>();
        if (nadi.points() == 0) flags.add("NADI_DOSHA");
        if (bhakoot.points() == 0) flags.add("BHAKOOT_DOSHA");
        return new GunaMilanReport(rules.version(), List.copyOf(s), total, 36.0, band(total), List.copyOf(flags));
    }

    public static String band(double total) {
        if (total < 18) return "NOT_RECOMMENDED";
        if (total < 25) return "AVERAGE";
        if (total < 33) return "VERY_GOOD";
        return "EXCELLENT";
    }

    // 1. Varna (1): boy's varna must be >= girl's
    KootaScore varna(MoonProfile b, MoonProfile g) {
        int vb = rules.varnaByRashi().get(b.rashi() - 1), vg = rules.varnaByRashi().get(g.rashi() - 1);
        return new KootaScore("VARNA", vb >= vg ? 1 : 0, 1, false, "boy varna " + vb + " vs girl varna " + vg);
    }

    // 2. Vashya (2): group matrix; Sagittarius and Capricorn split on the Moon's degree
    KootaScore vashya(MoonProfile b, MoonProfile g) {
        String gb = vashyaGroup(b), gg = vashyaGroup(g);
        double pts = rules.vashyaMatrix().get(rules.vashyaOrder().indexOf(gb)).get(rules.vashyaOrder().indexOf(gg));
        return new KootaScore("VASHYA", pts, 2, false, gb + " / " + gg);
    }

    private String vashyaGroup(MoonProfile m) {
        String base = rules.vashyaGroupByRashi().get(m.rashi() - 1);
        if (!"SPLIT".equals(base)) return base;
        List<String> halves = rules.vashyaSplit().get(String.valueOf(m.rashi()));
        return m.degreeInRashi() < rules.vashyaSplitDegree() ? halves.get(0) : halves.get(1);
    }

    // 3. Tara (3): count each way, remainder mod 9; 3, 5, 7 are inauspicious; 1.5 per direction
    KootaScore tara(MoonProfile b, MoonProfile g) {
        int r1 = taraRemainder(g.nakshatra(), b.nakshatra());   // girl -> boy
        int r2 = taraRemainder(b.nakshatra(), g.nakshatra());   // boy -> girl
        double pts = (TARA_INAUSPICIOUS.contains(r1) ? 0 : 1.5) + (TARA_INAUSPICIOUS.contains(r2) ? 0 : 1.5);
        return new KootaScore("TARA", pts, 3, false, "remainders " + r1 + " / " + r2);
    }

    public static int taraRemainder(int from, int to) {
        int count = Math.floorMod(to - from, 27) + 1;           // inclusive count
        int r = count % 9;
        return r == 0 ? 9 : r;
    }

    // 4. Yoni (4): animal-pair matrix (same = 4, sworn enemies = 0)
    KootaScore yoni(MoonProfile b, MoonProfile g) {
        String ab = rules.yoniAnimalByNakshatra().get(b.nakshatra() - 1), ag = rules.yoniAnimalByNakshatra().get(g.nakshatra() - 1);
        int pts = rules.yoniMatrix().get(rules.yoniOrder().indexOf(ab)).get(rules.yoniOrder().indexOf(ag));
        return new KootaScore("YONI", pts, 4, false, ab + " / " + ag);
    }

    // 5. Graha Maitri (5): friendship between the two rashi lords
    KootaScore grahaMaitri(MoonProfile b, MoonProfile g) {
        String lb = lord(b), lg = lord(g);
        double pts;
        if (lb.equals(lg)) pts = 5;
        else pts = maitriPoints(relation(lb, lg), relation(lg, lb));
        return new KootaScore("GRAHA_MAITRI", pts, 5, false, lb + " / " + lg);
    }

    private String lord(MoonProfile m) { return rules.lordByRashi().get(m.rashi() - 1); }

    /** F = friend, N = neutral, E = enemy of the other planet. */
    private char relation(String from, String to) {
        Friendship f = rules.friendship().get(from);
        if (f.friends().contains(to)) return 'F';
        if (f.enemies().contains(to)) return 'E';
        return 'N';
    }

    public static double maitriPoints(char x, char y) {
        String k = x <= y ? "" + x + y : "" + y + x;     // order-insensitive: EF -> EF, FF, FN -> FN, ...
        return switch (k) {
            case "FF" -> 5;
            case "FN" -> 4;
            case "NN" -> 3;
            case "EF" -> 1;
            case "EN" -> 0.5;
            default -> 0;                                  // EE
        };
    }

    // 6. Gana (6): boy-row x girl-column matrix
    KootaScore gana(MoonProfile b, MoonProfile g) {
        String gb = rules.ganaByNakshatra().get(b.nakshatra() - 1), gg = rules.ganaByNakshatra().get(g.nakshatra() - 1);
        int pts = rules.ganaMatrixBoyRowGirlCol().get(rules.ganaOrder().indexOf(gb)).get(rules.ganaOrder().indexOf(gg));
        return new KootaScore("GANA", pts, 6, false, gb + " / " + gg);
    }

    // 7. Bhakoot (7): 2/12, 5/9, 6/8 relations score 0, cancelled if same lord or mutual friends
    KootaScore bhakoot(MoonProfile b, MoonProfile g) {
        int count = Math.floorMod(g.rashi() - b.rashi(), 12) + 1;     // boy -> girl, inclusive
        if (!BHAKOOT_BAD.contains(count)) return new KootaScore("BHAKOOT", 7, 7, false, "rashi distance " + count);
        String lb = lord(b), lg = lord(g);
        boolean cancel = lb.equals(lg) || (relation(lb, lg) == 'F' && relation(lg, lb) == 'F');
        return cancel
                ? new KootaScore("BHAKOOT", 7, 7, true, "dosha (distance " + count + ") cancelled: same lord or mutual friends")
                : new KootaScore("BHAKOOT", 0, 7, false, "dosha, rashi distance " + count);
    }

    // 8. Nadi (8): same nadi = 0, with the two traditional cancellations
    KootaScore nadi(MoonProfile b, MoonProfile g) {
        String nb = rules.nadiByNakshatra().get(b.nakshatra() - 1), ng = rules.nadiByNakshatra().get(g.nakshatra() - 1);
        if (!nb.equals(ng)) return new KootaScore("NADI", 8, 8, false, nb + " / " + ng);
        if (nadiCancellation) {
            boolean sameNakshatraDifferentPada = b.nakshatra() == g.nakshatra() && b.pada() != g.pada();
            boolean sameRashiDifferentNakshatra = b.rashi() == g.rashi() && b.nakshatra() != g.nakshatra();
            if (sameNakshatraDifferentPada || sameRashiDifferentNakshatra) {
                return new KootaScore("NADI", 8, 8, true, "same nadi " + nb + " cancelled: "
                        + (sameNakshatraDifferentPada ? "same nakshatra, different pada" : "same rashi, different nakshatra"));
            }
        }
        return new KootaScore("NADI", 0, 8, false, "same nadi " + nb);
    }
}
