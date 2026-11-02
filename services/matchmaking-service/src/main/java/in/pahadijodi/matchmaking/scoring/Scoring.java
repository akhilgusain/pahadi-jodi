package in.pahadijodi.matchmaking.scoring;

import in.pahadijodi.matchmaking.domain.ProfileFacet;
import java.util.List;

/** Value types returned by the compatibility engine. */
public final class Scoring {
    private Scoring() {}

    /** One explained factor: how well B fits A's expectations (forYou) and A fits B's (forThem). */
    public record FactorScore(String factor, double weight, double forYou, double forThem, String reason) {}

    public record ScoredMatch(ProfileFacet profile, double score, List<String> reasons, List<FactorScore> breakdown) {}

    public record Evaluation(double score, String reason) {
        public static Evaluation of(double score) { return new Evaluation(clamp(score), null); }
        public static Evaluation of(double score, String reason) { return new Evaluation(clamp(score), reason); }
        private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    }
}
