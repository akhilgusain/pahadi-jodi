package in.pahadijodi.matchmaking.api;

import static in.pahadijodi.common.security.ResourceServerAutoConfiguration.userId;

import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.matchmaking.scoring.CompatibilityEngine;
import in.pahadijodi.matchmaking.scoring.Scoring.FactorScore;
import in.pahadijodi.matchmaking.scoring.Scoring.ScoredMatch;
import in.pahadijodi.matchmaking.service.DailyRecommendationService;
import in.pahadijodi.matchmaking.service.DailyRecommendationService.DailyItem;
import in.pahadijodi.matchmaking.service.MatchService;
import in.pahadijodi.matchmaking.service.PreferenceService;
import in.pahadijodi.matchmaking.service.PreferenceService.PreferenceDto;
import in.pahadijodi.matchmaking.service.Views.ProfileCard;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {
    public record MatchItem(ProfileCard profile, double score, List<String> reasons, List<FactorScore> breakdown) {
        static MatchItem of(ScoredMatch m) { return new MatchItem(ProfileCard.of(m.profile()), m.score(), m.reasons(), m.breakdown()); }
    }
    public record CompatibilityResponse(boolean eligible, String rejectedBecause, String algorithmVersion, MatchItem match) {}

    private final MatchService matches;
    private final PreferenceService preferences;
    private final DailyRecommendationService daily;

    public MatchController(MatchService matches, PreferenceService preferences, DailyRecommendationService daily) {
        this.matches = matches; this.preferences = preferences; this.daily = daily;
    }

    @GetMapping("/preferences")
    PreferenceDto getPreferences(@AuthenticationPrincipal Jwt jwt) { return preferences.get(userId(jwt)); }

    @PutMapping("/preferences")
    PreferenceDto putPreferences(@AuthenticationPrincipal Jwt jwt, @RequestBody PreferenceDto body) {
        if (body.ageMin() != null && body.ageMax() != null && body.ageMin() > body.ageMax()) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_AGE_RANGE", "ageMin must not exceed ageMax");
        }
        return preferences.put(userId(jwt), body);
    }

    @GetMapping("/recommendations")
    List<MatchItem> recommendations(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "20") int limit) {
        return matches.rank(userId(jwt), f -> true, true, Math.max(1, Math.min(limit, 50))).stream().map(MatchItem::of).toList();
    }

    @GetMapping("/daily")
    List<DailyItem> dailyToday(@AuthenticationPrincipal Jwt jwt) { return daily.today(userId(jwt)); }

    @GetMapping("/compatibility/{profileId}")
    CompatibilityResponse compatibility(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID profileId) {
        var c = matches.compatibility(userId(jwt), profileId);
        return new CompatibilityResponse(c.eligible(), c.rejectedBecause(), CompatibilityEngine.VERSION,
                c.match() == null ? null : MatchItem.of(c.match()));
    }

    /** scope=district (same district as you) or division (Garhwal/Kumaon). Ordered by compatibility. */
    @GetMapping("/nearby")
    List<MatchItem> nearby(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "district") String scope,
                           @RequestParam(defaultValue = "20") int limit) {
        var me = matches.requireMe(userId(jwt));
        Predicate<in.pahadijodi.matchmaking.domain.ProfileFacet> p = switch (scope.toLowerCase()) {
            case "district" -> f -> me.getDistrict() != null && me.getDistrict().equalsIgnoreCase(f.getDistrict());
            case "division" -> f -> me.getDivision() != null && me.getDivision().equalsIgnoreCase(f.getDivision());
            default -> throw new PjException(HttpStatus.BAD_REQUEST, "INVALID_SCOPE", "scope must be district or division");
        };
        return matches.rank(userId(jwt), p, false, Math.max(1, Math.min(limit, 50))).stream().map(MatchItem::of).toList();
    }
}
