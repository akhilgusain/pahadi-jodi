package in.pahadijodi.subscription.api;

import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.subscription.service.SubscriptionService;
import in.pahadijodi.subscription.service.SubscriptionService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {
    public record CheckRequest(UUID userId, @NotBlank String key, @Min(0) @Max(1000) Long consume, @Min(0) Long currentCount) {}

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) { this.service = service; }

    @GetMapping("/plans")
    List<PlanView> plans() { return service.catalogue(); }

    @GetMapping("/me")
    SubscriptionView me(@AuthenticationPrincipal Jwt jwt) { return service.me(UUID.fromString(jwt.getSubject())); }

    @GetMapping("/me/entitlements")
    List<EntitlementView> entitlements(@AuthenticationPrincipal Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        return service.entitlementsFor(id, service.effectiveTier(id));
    }

    @PostMapping("/me/cancel")
    SubscriptionView cancel(@AuthenticationPrincipal Jwt jwt) { return service.cancelAtPeriodEnd(UUID.fromString(jwt.getSubject()), true); }

    @PostMapping("/me/resume")
    SubscriptionView resume(@AuthenticationPrincipal Jwt jwt) { return service.cancelAtPeriodEnd(UUID.fromString(jwt.getSubject()), false); }

    /**
     * Entitlement check used by other services. Always HTTP 200 with allowed=true|false so callers' circuit
     * breakers stay calm. A caller may only check itself unless it holds ROLE_SERVICE / ROLE_ADMIN.
     */
    @PostMapping("/entitlements/check")
    CheckResult check(@AuthenticationPrincipal Jwt jwt, Authentication auth, @Valid @RequestBody CheckRequest r) {
        UUID caller = UUID.fromString(jwt.getSubject());
        UUID target = r.userId() == null ? caller : r.userId();
        boolean privileged = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SERVICE") || a.getAuthority().equals("ROLE_ADMIN"));
        if (!target.equals(caller) && !privileged) {
            throw new PjException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Cannot check another user's entitlements");
        }
        return service.check(target, r.key(), r.consume() == null ? 0 : r.consume(), r.currentCount());
    }
}
