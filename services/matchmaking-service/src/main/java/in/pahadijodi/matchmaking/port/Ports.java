package in.pahadijodi.matchmaking.port;

import in.pahadijodi.matchmaking.domain.ProfileFacet;
import in.pahadijodi.matchmaking.scoring.Scoring.ScoredMatch;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/** Outbound ports. Fakes live in MatchmakingConfig; real adapters call horoscope-service / ai-recommendation-service. */
public final class Ports {
    private Ports() {}

    /** Ashtakoot Guna Milan points out of 36 from horoscope-service; empty when either side has no horoscope. */
    public interface GunaPort {
        OptionalInt gunaScore(ProfileFacet a, ProfileFacet b);
    }

    /** Hybrid re-rank (docs/02 5.3 step 3): final = 0.75 rule + 0.25 AI. May reorder or rescore the list. */
    public interface AiRerankPort {
        List<ScoredMatch> rerank(UUID userId, List<ScoredMatch> candidates);
    }
}
