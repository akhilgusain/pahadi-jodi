package in.pahadijodi.horoscope;

import static org.junit.jupiter.api.Assertions.*;

import in.pahadijodi.horoscope.domain.MoonProfile;
import in.pahadijodi.horoscope.guna.GunaMilanCalculator;
import in.pahadijodi.horoscope.guna.GunaMilanReport;
import in.pahadijodi.horoscope.guna.KootaScore;
import in.pahadijodi.horoscope.guna.RuleSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GunaMilanCalculatorTest {
    static final RuleSet RULES = RuleSet.load("rules/guna-v1.json");
    static final GunaMilanCalculator CALC = new GunaMilanCalculator(RULES);

    static MoonProfile moon(int nakshatra, int pada) { return MoonProfile.fromNakshatraPada(nakshatra, pada); }
    static KootaScore score(GunaMilanReport r, String koota) {
        return r.scores().stream().filter(s -> s.koota().equals(koota)).findFirst().orElseThrow();
    }

    @Test
    void ruleTablesAreInternallyConsistent() {
        var m = RULES.yoniMatrix();
        for (int i = 0; i < 14; i++) {
            assertEquals(4, m.get(i).get(i), "same animal scores 4");
            for (int j = 0; j < 14; j++) assertEquals(m.get(i).get(j), m.get(j).get(i), "yoni matrix symmetric");
        }
        // sworn enemies score 0
        String[][] enemies = {{"Cow", "Tiger"}, {"Elephant", "Lion"}, {"Horse", "Buffalo"}, {"Dog", "Deer"},
                {"Serpent", "Mongoose"}, {"Monkey", "Sheep"}, {"Rat", "Cat"}};
        for (String[] e : enemies) {
            assertEquals(0, m.get(RULES.yoniOrder().indexOf(e[0])).get(RULES.yoniOrder().indexOf(e[1])), e[0] + "-" + e[1]);
        }
        // every nadi and gana has exactly 9 nakshatras
        for (String nadi : new String[]{"ADI", "MADHYA", "ANTYA"}) assertEquals(9, RULES.nadiByNakshatra().stream().filter(nadi::equals).count());
        for (String g : new String[]{"DEVA", "MANUSHYA", "RAKSHASA"}) assertEquals(9, RULES.ganaByNakshatra().stream().filter(g::equals).count());
    }

    @Test
    void sameNadiScoresZero_differentNadiScoresEight() {
        // Ashwini (Adi) vs Hasta (Adi): same nadi, different rashi and nakshatra -> no cancellation
        GunaMilanReport same = CALC.compute(moon(1, 1), moon(13, 1));
        assertEquals(0, score(same, "NADI").points());
        assertTrue(same.doshaFlags().contains("NADI_DOSHA"));
        // Ashwini (Adi) vs Bharani (Madhya)
        GunaMilanReport diff = CALC.compute(moon(1, 1), moon(2, 1));
        assertEquals(8, score(diff, "NADI").points());
        assertFalse(diff.doshaFlags().contains("NADI_DOSHA"));
    }

    @Test
    void nadiCancellation_sameNakshatraDifferentPada_andSameRashiDifferentNakshatra() {
        KootaScore c1 = score(CALC.compute(moon(4, 1), moon(4, 2)), "NADI");          // Rohini pada 1 vs 2
        assertEquals(8, c1.points());
        assertTrue(c1.cancelled());
        KootaScore c2 = score(CALC.compute(moon(4, 3), moon(5, 1)), "NADI");          // Rohini p3 (Taurus) vs Mrigashira p1 (Taurus)? different nadi, so no cancel needed
        assertEquals(8, c2.points());
        // same rashi, different nakshatra, same nadi: Hasta (Adi, Virgo) vs ... Uttara Phalguni p2-4 (Adi) is Virgo, Hasta p1-4 is Virgo
        KootaScore c3 = score(CALC.compute(moon(12, 2), moon(13, 1)), "NADI");
        assertEquals(8, c3.points());
        assertTrue(c3.cancelled(), "UP-2 (Virgo) and Hasta-1 (Virgo) share nadi ADI and rashi");
        // cancellation can be switched off by configuration
        KootaScore off = score(new GunaMilanCalculator(RULES, false).compute(moon(4, 1), moon(4, 2)), "NADI");
        assertEquals(0, off.points());
    }

    @Test
    void sameNakshatraSamePada_isMaximumExceptNadi() {
        GunaMilanReport r = CALC.compute(moon(8, 2), moon(8, 2));   // Pushya vs Pushya
        assertEquals(1, score(r, "VARNA").points());
        assertEquals(2, score(r, "VASHYA").points());
        assertEquals(3, score(r, "TARA").points(), "same nakshatra: count 1 each way, auspicious");
        assertEquals(4, score(r, "YONI").points());
        assertEquals(5, score(r, "GRAHA_MAITRI").points());
        assertEquals(6, score(r, "GANA").points());
        assertEquals(7, score(r, "BHAKOOT").points());
        assertEquals(0, score(r, "NADI").points(), "same nakshatra and pada: nadi dosha stands");
        assertEquals(28, r.total());
        assertEquals("VERY_GOOD", r.band());
    }

    @Test
    void perfectScoreWhenNadiIsCancelled() {
        GunaMilanReport r = CALC.compute(moon(8, 1), moon(8, 2));    // same nakshatra, different pada
        assertEquals(36, r.total());
        assertEquals("EXCELLENT", r.band());
        assertTrue(r.doshaFlags().isEmpty());
    }

    @Test
    void taraCountsInclusiveAndUsesRemainderMod9() {
        assertEquals(1, GunaMilanCalculator.taraRemainder(5, 5));
        assertEquals(3, GunaMilanCalculator.taraRemainder(1, 3));      // count 3 -> Vipat
        assertEquals(9, GunaMilanCalculator.taraRemainder(1, 9));      // count 9 -> remainder 0 -> 9
        assertEquals(2, GunaMilanCalculator.taraRemainder(27, 1));     // wraps around: Revati -> Ashwini is count 2
        // Ashwini(1) -> Krittika(3) = 3 (bad) and Krittika -> Ashwini = 26 -> 8 (ok): 1.5 only
        assertEquals(1.5, score(CALC.compute(moon(1, 1), moon(3, 1)), "TARA").points());
    }

    @Test
    void varnaIsDirectional() {
        // Cancer (Brahmin 4) boy, Aries (Kshatriya 3) girl -> 1; reversed -> 0
        MoonProfile cancer = MoonProfile.fromLongitude(100), aries = MoonProfile.fromLongitude(10);
        assertEquals(1, score(CALC.compute(cancer, aries), "VARNA").points());
        assertEquals(0, score(CALC.compute(aries, cancer), "VARNA").points());
    }

    @Test
    void vashyaUsesDegreeForSagittariusAndCapricorn() {
        MoonProfile sag10 = MoonProfile.fromLongitude(240 + 10), sag20 = MoonProfile.fromLongitude(240 + 20);   // Manava / Chatushpada
        MoonProfile gemini = MoonProfile.fromLongitude(70);                                                      // Manava
        assertEquals(2, score(CALC.compute(sag10, gemini), "VASHYA").points());
        assertEquals(1, score(CALC.compute(sag20, gemini), "VASHYA").points());
        MoonProfile leo = MoonProfile.fromLongitude(130), scorpio = MoonProfile.fromLongitude(230);
        assertEquals(0, score(CALC.compute(leo, scorpio), "VASHYA").points(), "Vanachara x Keeta");
    }

    @Test
    void grahaMaitriPointTable() {
        assertEquals(5, GunaMilanCalculator.maitriPoints('F', 'F'));
        assertEquals(4, GunaMilanCalculator.maitriPoints('F', 'N'));
        assertEquals(4, GunaMilanCalculator.maitriPoints('N', 'F'));
        assertEquals(3, GunaMilanCalculator.maitriPoints('N', 'N'));
        assertEquals(1, GunaMilanCalculator.maitriPoints('F', 'E'));
        assertEquals(0.5, GunaMilanCalculator.maitriPoints('E', 'N'));
        assertEquals(0, GunaMilanCalculator.maitriPoints('E', 'E'));
        // Sun lord (Leo) vs Saturn lord (Capricorn): Sun->Saturn enemy, Saturn->Sun enemy -> 0
        assertEquals(0, score(CALC.compute(MoonProfile.fromLongitude(130), MoonProfile.fromLongitude(280)), "GRAHA_MAITRI").points());
        // same lord (Aries and Scorpio are both Mars) -> 5
        assertEquals(5, score(CALC.compute(MoonProfile.fromLongitude(10), MoonProfile.fromLongitude(220)), "GRAHA_MAITRI").points());
    }

    @Test
    void bhakootDoshaAndItsCancellation() {
        // Aries boy, Virgo girl: girl is 6th from boy -> Shadashtak (6/8). Mars vs Mercury: Mars->Mercury enemy -> no cancellation
        KootaScore bad = score(CALC.compute(MoonProfile.fromLongitude(10), MoonProfile.fromLongitude(160)), "BHAKOOT");
        assertEquals(0, bad.points());
        // Aries and Scorpio (8th from Aries) share lord Mars -> cancelled
        KootaScore cancelled = score(CALC.compute(MoonProfile.fromLongitude(10), MoonProfile.fromLongitude(220)), "BHAKOOT");
        assertEquals(7, cancelled.points());
        assertTrue(cancelled.cancelled());
        // Aries boy, Gemini girl: 3rd -> fine
        assertEquals(7, score(CALC.compute(MoonProfile.fromLongitude(10), MoonProfile.fromLongitude(70)), "BHAKOOT").points());
    }

    @Test
    void ganaMatrixIsDirectional() {
        // boy Deva (Ashwini) x girl Rakshasa (Krittika) = 1; boy Rakshasa x girl Deva = 0
        assertEquals(1, score(CALC.compute(moon(1, 1), moon(3, 1)), "GANA").points());
        assertEquals(0, score(CALC.compute(moon(3, 1), moon(1, 1)), "GANA").points());
        assertEquals(5, score(CALC.compute(moon(2, 1), moon(1, 1)), "GANA").points(), "Manushya boy x Deva girl");
    }

    @Test
    void interpretationBands() {
        assertEquals("NOT_RECOMMENDED", GunaMilanCalculator.band(17.5));
        assertEquals("AVERAGE", GunaMilanCalculator.band(18));
        assertEquals("AVERAGE", GunaMilanCalculator.band(24.5));
        assertEquals("VERY_GOOD", GunaMilanCalculator.band(25));
        assertEquals("VERY_GOOD", GunaMilanCalculator.band(32.5));
        assertEquals("EXCELLENT", GunaMilanCalculator.band(33));
    }

    @Test
    void invariantsHoldForEveryNakshatraPadaPair() {
        Random rnd = new Random(42);
        double[] max = {1, 2, 3, 4, 5, 6, 7, 8};
        for (int i = 0; i < 20_000; i++) {
            MoonProfile a = moon(1 + rnd.nextInt(27), 1 + rnd.nextInt(4)), b = moon(1 + rnd.nextInt(27), 1 + rnd.nextInt(4));
            GunaMilanReport r = CALC.compute(a, b);
            assertEquals(8, r.scores().size());
            double sum = 0;
            for (int k = 0; k < 8; k++) {
                KootaScore s = r.scores().get(k);
                assertTrue(s.points() >= 0 && s.points() <= max[k] && s.max() == max[k], s.koota());
                sum += s.points();
            }
            assertEquals(sum, r.total(), 1e-9);
            assertTrue(r.total() >= 0 && r.total() <= 36);
            // Tara, Yoni, Graha Maitri-as-computed and Bhakoot are symmetric in the two people
            GunaMilanReport rev = CALC.compute(b, a);
            for (String k : new String[]{"TARA", "YONI", "GRAHA_MAITRI", "BHAKOOT", "NADI"}) {
                assertEquals(score(r, k).points(), score(rev, k).points(), k);
            }
        }
    }
}
