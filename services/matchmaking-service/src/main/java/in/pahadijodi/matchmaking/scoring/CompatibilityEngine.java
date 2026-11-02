package in.pahadijodi.matchmaking.scoring;

import static in.pahadijodi.matchmaking.scoring.Scoring.*;

import in.pahadijodi.matchmaking.domain.PartnerPreference;
import in.pahadijodi.matchmaking.domain.PartnerPreference.Dealbreaker;
import in.pahadijodi.matchmaking.domain.ProfileFacet;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Compatibility scoring (docs/02 section 5.3), pure and stateless.
 *  1. Hard filters (eliminators): opposite gender, same-gotra exclusion, listed dealbreakers (both directions).
 *  2. Soft score S(A->B) = sum(w_i * f_i), weights renormalised over the factors that are available.
 *  3. Mutual combine: 100 * sqrt(S(A->B) * S(B->A)): a one-sided mismatch drags the score down.
 * Deviation from the doc: only factors computable from profile facets are implemented (age, education, occupation,
 * community/caste, native region, language, horoscope, completeness). Height, lifestyle, family, income, location,
 * interests, verification and activity plug in as extra Factor entries once their data is projected here.
 */
public class CompatibilityEngine {
    public static final String VERSION = "pahadi-v1";

    private static final Set<String> PAHADI_GROUPS = Set.of("GARHWALI", "KUMAONI", "JAUNSARI", "BHOTIYA", "OTHER_PAHADI");

    /** A named, weighted, directional factor. Strategy pattern: add factors without touching the engine. */
    public record Factor(String name, double weight, Evaluator evaluator) {}

    @FunctionalInterface
    public interface Evaluator {
        /** How well {@code to} fits what {@code from} wants. guna is empty when no horoscope data exists. */
        Evaluation eval(ProfileFacet from, PartnerPreference prefFrom, ProfileFacet to, OptionalInt guna);
    }

    private final List<Factor> factors = List.of(
            new Factor("Age", 0.12, CompatibilityEngine::age),
            new Factor("Education", 0.10, CompatibilityEngine::education),
            new Factor("Occupation", 0.08, CompatibilityEngine::occupation),
            new Factor("Community & caste", 0.20, CompatibilityEngine::community),
            new Factor("Native region", 0.20, CompatibilityEngine::region),
            new Factor("Language", 0.10, CompatibilityEngine::language),
            new Factor("Horoscope", 0.15, CompatibilityEngine::horoscope),
            new Factor("Profile completeness", 0.05, (f, p, t, g) -> Evaluation.of(t.getCompleteness() / 100.0)));

    // ---------------------------------------------------------------- hard filters

    /** Structural rules that apply to everyone (used also when sending an interest). */
    public Optional<String> rejectStructural(ProfileFacet a, ProfileFacet b) {
        if (a.getUserId().equals(b.getUserId())) return Optional.of("This is your own profile");
        String ga = norm(a.getGender()), gb = norm(b.getGender());
        if (!ga.isEmpty() && !gb.isEmpty() && !ga.equals("other") && !gb.equals("other") && ga.equals(gb)) {
            return Optional.of("Same gender");
        }
        if (!norm(a.getGotra()).isEmpty() && norm(a.getGotra()).equals(norm(b.getGotra()))) {
            return Optional.of("Same gotra (sagotra marriage is avoided)");
        }
        return Optional.empty();
    }

    /** Structural rules plus both sides' declared dealbreakers. Empty = eligible. */
    public Optional<String> reject(ProfileFacet a, PartnerPreference pa, ProfileFacet b, PartnerPreference pb) {
        var s = rejectStructural(a, b);
        if (s.isPresent()) return s;
        var d = dealbreaker(a, pa, b);
        if (d.isPresent()) return Optional.of("Outside your dealbreaker: " + d.get());
        d = dealbreaker(b, pb, a);
        if (d.isPresent()) return Optional.of("Outside their dealbreaker: " + d.get());
        return Optional.empty();
    }

    private static Optional<String> dealbreaker(ProfileFacet from, PartnerPreference p, ProfileFacet to) {
        if (p == null) return Optional.empty();
        Set<Dealbreaker> d = p.dealbreakerSet();
        if (d.contains(Dealbreaker.AGE) && to.getAge() != null
                && ((p.getAgeMin() != null && to.getAge() < p.getAgeMin()) || (p.getAgeMax() != null && to.getAge() > p.getAgeMax()))) {
            return Optional.of("age");
        }
        if (d.contains(Dealbreaker.COMMUNITY) && notIn(p.communityList(), to.getCommunity())) return Optional.of("community");
        if (d.contains(Dealbreaker.DISTRICT) && notIn(p.districtList(), to.getDistrict())) return Optional.of("district");
        if (d.contains(Dealbreaker.DIVISION) && p.getDivision() != null && !norm(p.getDivision()).equals(norm(to.getDivision()))) {
            return Optional.of("division");
        }
        if (d.contains(Dealbreaker.MOTHER_TONGUE) && notIn(p.motherTongueList(), to.getMotherTongue())) return Optional.of("mother tongue");
        if (d.contains(Dealbreaker.EDUCATION) && !p.educationList().isEmpty()
                && educationLevel(to.getEducation()) < minLevel(p.educationList())) return Optional.of("education");
        if (d.contains(Dealbreaker.OCCUPATION) && !p.occupationList().isEmpty()
                && p.occupationList().stream().noneMatch(k -> norm(to.getOccupation()).contains(norm(k)))) return Optional.of("occupation");
        return Optional.empty();
    }

    private static boolean notIn(List<String> wanted, String have) {
        return !wanted.isEmpty() && wanted.stream().noneMatch(w -> norm(w).equals(norm(have)));
    }

    // ---------------------------------------------------------------- scoring

    /** Mutual score 0-100 with an explanation. Call reject() first; this does not apply hard filters. */
    public ScoredMatch score(ProfileFacet a, PartnerPreference pa, ProfileFacet b, PartnerPreference pb, OptionalInt guna) {
        List<Factor> active = factors.stream().filter(f -> !f.name().equals("Horoscope") || guna.isPresent()).toList();
        double total = active.stream().mapToDouble(Factor::weight).sum();
        double sAB = 0, sBA = 0;
        List<FactorScore> breakdown = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (Factor f : active) {
            double w = f.weight() / total;
            Evaluation ab = f.evaluator().eval(a, pa, b, guna);
            Evaluation ba = f.evaluator().eval(b, pb, a, guna);
            sAB += w * ab.score();
            sBA += w * ba.score();
            String reason = ab.reason() != null ? ab.reason() : ba.reason();
            breakdown.add(new FactorScore(f.name(), round(w, 3), round(ab.score(), 2), round(ba.score(), 2), reason));
            if (reason != null && Math.min(ab.score(), ba.score()) >= 0.8) reasons.add(reason);
        }
        double score = 100 * Math.sqrt(sAB * sBA);
        return new ScoredMatch(b, round(score, 1), List.copyOf(reasons), List.copyOf(breakdown));
    }

    // ---------------------------------------------------------------- factor functions

    static Evaluation age(ProfileFacet from, PartnerPreference p, ProfileFacet to, OptionalInt g) {
        if (to.getAge() == null) return Evaluation.of(0.5);
        if (p != null && (p.getAgeMin() != null || p.getAgeMax() != null)) {
            int lo = p.getAgeMin() == null ? 18 : p.getAgeMin(), hi = p.getAgeMax() == null ? 99 : p.getAgeMax();
            int out = to.getAge() < lo ? lo - to.getAge() : Math.max(0, to.getAge() - hi);
            return Evaluation.of(1 - out / 5.0);                 // linear decay to 0 over 5 years outside the band
        }
        if (from.getAge() == null) return Evaluation.of(0.5);
        int diff = Math.abs(from.getAge() - to.getAge());
        return Evaluation.of(diff <= 3 ? 1 : 1 - (diff - 3) / 9.0);   // no preference: close in age is best
    }

    static Evaluation education(ProfileFacet from, PartnerPreference p, ProfileFacet to, OptionalInt g) {
        int lt = educationLevel(to.getEducation());
        if (lt == 0) return Evaluation.of(0.5);
        if (p != null && !p.educationList().isEmpty()) {
            int shortfall = Math.max(0, minLevel(p.educationList()) - lt);
            return Evaluation.of(1 - 0.3 * shortfall);
        }
        int lf = educationLevel(from.getEducation());
        if (lf == 0) return Evaluation.of(0.6);
        return Evaluation.of(switch (Math.abs(lf - lt)) { case 0 -> 1.0; case 1 -> 0.8; case 2 -> 0.5; default -> 0.3; });
    }

    static Evaluation occupation(ProfileFacet from, PartnerPreference p, ProfileFacet to, OptionalInt g) {
        if (p != null && !p.occupationList().isEmpty()) {
            boolean ok = p.occupationList().stream().anyMatch(k -> norm(to.getOccupation()).contains(norm(k)));
            return Evaluation.of(ok ? 1 : 0.3);
        }
        return Evaluation.of(norm(to.getOccupation()).isEmpty() ? 0.4 : 0.7);
    }

    static Evaluation community(ProfileFacet from, PartnerPreference p, ProfileFacet to, OptionalInt g) {
        String cf = up(from.getCommunity()), ct = up(to.getCommunity());
        double s;
        String reason = null;
        if (!ct.isEmpty() && cf.equals(ct)) {
            s = 1.0;
            reason = "Both " + title(ct);
        } else if (PAHADI_GROUPS.contains(cf) && PAHADI_GROUPS.contains(ct)) {
            s = 0.6;                                              // cross-community among Pahadi groups
        } else {
            s = 0.2;
        }
        if (p != null && p.communityList().stream().anyMatch(c -> up(c).equals(ct))) s = Math.max(s, 0.9);
        if (!norm(from.getCaste()).isEmpty() && norm(from.getCaste()).equals(norm(to.getCaste()))) {
            s = 1.0;
            if (reason == null) reason = "Same caste (" + to.getCaste() + ")";
        }
        return Evaluation.of(s, reason);
    }

    static Evaluation region(ProfileFacet from, PartnerPreference p, ProfileFacet to, OptionalInt g) {
        double s;
        String reason = null;
        if (!norm(from.getVillage()).isEmpty() && norm(from.getVillage()).equals(norm(to.getVillage()))) {
            s = 1.0;
            reason = "Both from " + to.getVillage();
        } else if (!norm(from.getDistrict()).isEmpty() && norm(from.getDistrict()).equals(norm(to.getDistrict()))) {
            s = 0.8;
            reason = "Both from " + pretty(to.getDistrict());
        } else if (!norm(from.getDivision()).isEmpty() && norm(from.getDivision()).equals(norm(to.getDivision()))) {
            s = 0.6;
            reason = "Both from " + title(to.getDivision());
        } else {
            s = 0.5;                                              // other Uttarakhand
        }
        if (p != null) {
            if (p.districtList().stream().anyMatch(d -> norm(d).equals(norm(to.getDistrict())))) s = Math.max(s, 0.9);
            if (p.getDivision() != null && norm(p.getDivision()).equals(norm(to.getDivision()))) s = Math.max(s, 0.7);
        }
        return Evaluation.of(s, reason);
    }

    static Evaluation language(ProfileFacet from, PartnerPreference p, ProfileFacet to, OptionalInt g) {
        String a = norm(from.getMotherTongue()), b = norm(to.getMotherTongue());
        double s;
        String reason = null;
        if (a.isEmpty() || b.isEmpty()) {
            s = 0.5;                                              // Hindi is the common fallback
        } else if (a.equals(b)) {
            s = 1.0;
            reason = "Both speak " + title(to.getMotherTongue());
        } else if (Set.of(a, b).equals(Set.of("garhwali", "kumaoni"))) {
            s = 0.7;                                              // mutually intelligible
        } else {
            s = 0.5;
        }
        if (p != null && p.motherTongueList().stream().anyMatch(m -> norm(m).equals(b))) s = Math.max(s, 0.9);
        return Evaluation.of(s, reason);
    }

    static Evaluation horoscope(ProfileFacet from, PartnerPreference p, ProfileFacet to, OptionalInt guna) {
        int g = guna.orElse(18);
        return Evaluation.of(g / 36.0, g >= 25 ? g + "/36 gunas match" : null);
    }

    // ---------------------------------------------------------------- helpers

    /** 0 unknown, 1 school, 2 diploma, 3 bachelor's, 4 master's/professional, 5 doctorate. */
    static int educationLevel(String edu) {
        String e = norm(edu).replaceAll("[^a-z0-9]", "");        // "B.Tech" -> "btech"
        if (e.isEmpty()) return 0;
        if (anyContains(e, "phd", "doctorate", "dphil")) return 5;
        if (anyContains(e, "mtech", "mba", "msc", "mca", "mcom", "mbbs", "master", "postgraduate", "llm")
                || e.matches("ma|me|md|pg.*")) return 4;
        if (anyContains(e, "btech", "bsc", "bcom", "bca", "bba", "bachelor", "graduate", "llb", "bed")
                || e.matches("be|ba.*")) return 3;
        if (anyContains(e, "diploma", "polytechnic", "iti")) return 2;
        return 1;
    }

    private static boolean anyContains(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    private static int minLevel(List<String> wanted) {
        return wanted.stream().mapToInt(CompatibilityEngine::educationLevel).filter(l -> l > 0).min().orElse(0);
    }

    static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private static String up(String s) { return s == null ? "" : s.trim().toUpperCase(Locale.ROOT); }

    private static String title(String s) {
        String l = s.trim().toLowerCase(Locale.ROOT);
        return l.isEmpty() ? l : Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }

    private static String pretty(String enumName) {
        String[] parts = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder b = new StringBuilder();
        for (String p : parts) b.append(b.length() > 0 ? " " : "").append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        return b.toString();
    }

    private static double round(double v, int places) {
        double m = Math.pow(10, places);
        return Math.round(v * m) / m;
    }
}
