package in.pahadijodi.matchmaking.service;

import static in.pahadijodi.matchmaking.domain.ShortlistEntry.Kind.BLOCK;
import static in.pahadijodi.matchmaking.domain.ShortlistEntry.Kind.IGNORE;

import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.matchmaking.domain.*;
import in.pahadijodi.matchmaking.domain.Repositories.*;
import in.pahadijodi.matchmaking.port.Ports.AiRerankPort;
import in.pahadijodi.matchmaking.port.Ports.GunaPort;
import in.pahadijodi.matchmaking.scoring.CompatibilityEngine;
import in.pahadijodi.matchmaking.scoring.Scoring.ScoredMatch;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Candidate generation, hard filtering, scoring, AI re-rank and diversity. */
@Service
public class MatchService {
    public static final Duration DECLINE_COOL_OFF = Duration.ofDays(90);
    static final int MAX_PER_VILLAGE = 3;

    public record Compatibility(boolean eligible, String rejectedBecause, ScoredMatch match) {}

    private final ProfileFacetRepository facets;
    private final PartnerPreferenceRepository prefs;
    private final ShortlistRepository shortlist;
    private final InterestRepository interests;
    private final CompatibilityEngine engine;
    private final GunaPort guna;
    private final AiRerankPort ai;

    public MatchService(ProfileFacetRepository facets, PartnerPreferenceRepository prefs, ShortlistRepository shortlist,
                        InterestRepository interests, CompatibilityEngine engine, GunaPort guna, AiRerankPort ai) {
        this.facets = facets; this.prefs = prefs; this.shortlist = shortlist;
        this.interests = interests; this.engine = engine; this.guna = guna; this.ai = ai;
    }

    public ProfileFacet requireMe(UUID userId) {
        return facets.findByUserId(userId).orElseThrow(() ->
                new PjException(HttpStatus.CONFLICT, "PROFILE_REQUIRED", "Create your profile before using matches"));
    }

    /** Users blocked or ignored by me, or who blocked/ignored me. These cannot be contacted at all. */
    @Transactional(readOnly = true)
    public Set<UUID> blockedUsers(ProfileFacet me) {
        Set<UUID> out = new HashSet<>();
        for (ShortlistEntry e : shortlist.exclusionsFor(me.getUserId(), me.getProfileId(), List.of(BLOCK, IGNORE))) {
            if (e.getUserId().equals(me.getUserId())) {
                facets.findById(e.getTargetProfileId()).ifPresent(f -> out.add(f.getUserId()));
            } else {
                out.add(e.getUserId());                      // they blocked/ignored me
            }
        }
        return out;
    }

    /** Users who must never appear in recommendations: blocked/ignored either way, plus declines within 90 days. */
    @Transactional(readOnly = true)
    public Set<UUID> excludedUsers(ProfileFacet me) {
        Set<UUID> out = new HashSet<>(blockedUsers(me));
        for (Interest i : interests.recentDeclines(me.getUserId(), Instant.now().minus(DECLINE_COOL_OFF))) {
            out.add(i.getFromUserId().equals(me.getUserId()) ? i.getToUserId() : i.getFromUserId());
        }
        return out;
    }

    /** Ranked recommendations for the caller. {@code scope} narrows candidates (nearby); skipInteracted drops sent/accepted. */
    @Transactional(readOnly = true)
    public List<ScoredMatch> rank(UUID userId, Predicate<ProfileFacet> scope, boolean skipInteracted, int limit) {
        ProfileFacet me = requireMe(userId);
        PartnerPreference myPref = prefs.findById(userId).orElse(null);
        Map<UUID, PartnerPreference> allPrefs = prefs.findAll().stream().collect(Collectors.toMap(PartnerPreference::getUserId, p -> p));
        Set<UUID> excluded = new HashSet<>(excludedUsers(me));
        if (skipInteracted) {
            interests.findByFromUserIdOrderByCreatedAtDesc(userId).forEach(i -> excluded.add(i.getToUserId()));
            interests.findByToUserIdOrderByCreatedAtDesc(userId).stream()
                    .filter(i -> i.getStatus() == Interest.Status.ACCEPTED).forEach(i -> excluded.add(i.getFromUserId()));
        }

        List<ScoredMatch> scored = new ArrayList<>();
        for (ProfileFacet c : facets.findByUserIdNot(userId)) {
            if (excluded.contains(c.getUserId()) || !scope.test(c)) continue;
            PartnerPreference cp = allPrefs.get(c.getUserId());
            if (engine.reject(me, myPref, c, cp).isPresent()) continue;
            scored.add(engine.score(me, myPref, c, cp, guna.gunaScore(me, c)));
        }
        scored.sort(Comparator.comparingDouble(ScoredMatch::score).reversed()
                .thenComparing(Comparator.comparingInt((ScoredMatch m) -> m.profile().getCompleteness()).reversed())
                .thenComparing(m -> m.profile().getProfileId()));
        return diversify(ai.rerank(userId, scored), limit);
    }

    /** At most MAX_PER_VILLAGE profiles from the same village per page, so one village cannot flood the list. */
    static List<ScoredMatch> diversify(List<ScoredMatch> ranked, int limit) {
        Map<String, Integer> perVillage = new HashMap<>();
        List<ScoredMatch> out = new ArrayList<>();
        for (ScoredMatch m : ranked) {
            String v = m.profile().getVillage() == null ? "" : m.profile().getVillage().trim().toLowerCase(Locale.ROOT);
            if (!v.isEmpty() && perVillage.merge(v, 1, Integer::sum) > MAX_PER_VILLAGE) continue;
            out.add(m);
            if (out.size() >= limit) break;
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Compatibility compatibility(UUID userId, UUID profileId) {
        ProfileFacet me = requireMe(userId);
        ProfileFacet other = facets.findById(profileId)
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found"));
        PartnerPreference mine = prefs.findById(userId).orElse(null);
        PartnerPreference theirs = prefs.findById(other.getUserId()).orElse(null);
        var rejection = engine.reject(me, mine, other, theirs);
        if (rejection.isPresent()) return new Compatibility(false, rejection.get(), null);
        return new Compatibility(true, null, engine.score(me, mine, other, theirs, guna.gunaScore(me, other)));
    }
}
