package in.pahadijodi.airec.api;

import static in.pahadijodi.common.security.ResourceServerAutoConfiguration.userId;

import in.pahadijodi.airec.llm.CultureCatalog;
import in.pahadijodi.airec.service.*;
import in.pahadijodi.airec.service.CompatibilityService.Explanation;
import in.pahadijodi.airec.service.IceBreakerService.Result;
import in.pahadijodi.airec.service.ProfileAssistantService.BioResult;
import in.pahadijodi.airec.service.ProfileAssistantService.Review;
import in.pahadijodi.airec.service.QuotaService.Usage;
import in.pahadijodi.airec.service.SafetyService;
import in.pahadijodi.airec.service.SimilarityService.Similar;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {
    public record BioRequest(@Pattern(regexp = "warm|formal|friendly") String tone, @Pattern(regexp = "en|hi") String language,
                             @Min(40) @Max(150) Integer maxWords, @Size(max = 5) List<@Size(max = 40) String> interests,
                             @Size(max = 1000) String draft) {}
    public record IceBreakerRequest(@NotNull UUID profileId, @Pattern(regexp = "en|hi") String language,
                                    @Pattern(regexp = "formal|friendly") String tone, @Min(1) @Max(5) Integer count) {}
    public record CompatRequest(@NotNull UUID profileId, @Pattern(regexp = "en|hi") String language) {}
    public record SafetyRequest(@NotNull @Size(max = 2000) String text) {}

    private final QuotaService quota;
    private final ProfileAssistantService assistant;
    private final IceBreakerService iceBreakers;
    private final CompatibilityService compatibility;
    private final SimilarityService similarity;
    private final SafetyService safety;
    private final CultureCatalog culture;

    public AiController(QuotaService quota, ProfileAssistantService assistant, IceBreakerService iceBreakers,
                        CompatibilityService compatibility, SimilarityService similarity, SafetyService safety, CultureCatalog culture) {
        this.quota = quota; this.assistant = assistant; this.iceBreakers = iceBreakers; this.compatibility = compatibility;
        this.similarity = similarity; this.safety = safety; this.culture = culture;
    }

    @PostMapping("/profile/bio-suggestions")
    BioResult bio(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody(required = false) BioRequest r) {
        BioRequest q = r == null ? new BioRequest(null, null, null, null, null) : r;
        quota.consume(userId(jwt));
        return assistant.bioSuggestions(userId(jwt), q.tone() == null ? "warm" : q.tone(), q.language() == null ? "en" : q.language(),
                q.maxWords(), q.interests(), q.draft());
    }

    @PostMapping("/profile/review")
    Review review(@AuthenticationPrincipal Jwt jwt) { return assistant.review(userId(jwt)); }

    @PostMapping("/chat/ice-breakers")
    Result iceBreakers(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody IceBreakerRequest r) {
        quota.consume(userId(jwt));
        return iceBreakers.generate(userId(jwt), r.profileId(), r.language() == null ? "en" : r.language(),
                r.tone() == null ? "friendly" : r.tone(), r.count() == null ? 3 : r.count());
    }

    @PostMapping("/compatibility/explain")
    Explanation explain(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CompatRequest r) {
        quota.consume(userId(jwt));
        return compatibility.explain(userId(jwt), r.profileId(), r.language() == null ? "en" : r.language());
    }

    @GetMapping("/recommendations/similar")
    List<Similar> similar(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) UUID profileId,
                          @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
                          @RequestParam(required = false) @Pattern(regexp = "MALE|FEMALE|OTHER|ANY") String gender) {
        return similarity.similar(userId(jwt), profileId, limit, gender);
    }

    @PostMapping("/safety/check")
    SafetyService.Result safety(@Valid @RequestBody SafetyRequest r) { return safety.check(r.text()); }

    @GetMapping("/usage")
    Usage usage(@AuthenticationPrincipal Jwt jwt) { return quota.current(userId(jwt)); }

    @GetMapping("/culture/festivals")
    List<CultureCatalog.Festival> festivals(@RequestParam(required = false) @Pattern(regexp = "GARHWAL|KUMAON") String division) {
        return culture.festivalsFor(division);
    }
}
