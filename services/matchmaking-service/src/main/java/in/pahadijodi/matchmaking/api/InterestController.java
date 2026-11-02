package in.pahadijodi.matchmaking.api;

import static in.pahadijodi.common.security.ResourceServerAutoConfiguration.userId;

import in.pahadijodi.matchmaking.service.InterestService;
import in.pahadijodi.matchmaking.service.InterestService.InterestView;
import in.pahadijodi.matchmaking.service.InterestService.MatchView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/interests")
public class InterestController {
    public record SendRequest(@NotNull UUID toProfileId, @Size(max = 500) String message) {}

    private final InterestService service;

    public InterestController(InterestService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    InterestView send(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SendRequest r) {
        return service.send(userId(jwt), r.toProfileId(), r.message());
    }

    @PostMapping("/{id}/accept")
    InterestView accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return service.accept(userId(jwt), id); }

    @PostMapping("/{id}/decline")
    InterestView decline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return service.decline(userId(jwt), id); }

    @DeleteMapping("/{id}")
    InterestView withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return service.withdraw(userId(jwt), id); }

    @GetMapping("/sent")
    List<InterestView> sent(@AuthenticationPrincipal Jwt jwt) { return service.sent(userId(jwt)); }

    @GetMapping("/received")
    List<InterestView> received(@AuthenticationPrincipal Jwt jwt) { return service.received(userId(jwt)); }

    @GetMapping("/matches")
    List<MatchView> matches(@AuthenticationPrincipal Jwt jwt) { return service.myMatches(userId(jwt)); }
}
