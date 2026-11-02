package in.pahadijodi.subscription.api;

import in.pahadijodi.subscription.service.SubscriptionService;
import in.pahadijodi.subscription.service.SubscriptionService.SubscriptionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Local-dev shortcut that activates a plan without a payment. Absent unless pj.subscription.dev-endpoints=true. */
@RestController
@RequestMapping("/api/v1/subscriptions/dev")
@ConditionalOnProperty(name = "pj.subscription.dev-endpoints", havingValue = "true")
public class DevController {
    public record ActivateRequest(@NotBlank String planCode) {}

    private final SubscriptionService service;

    public DevController(SubscriptionService service) { this.service = service; }

    @PostMapping("/activate")
    SubscriptionView activate(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ActivateRequest r) {
        UUID id = UUID.fromString(jwt.getSubject());
        service.activate(id, r.planCode(), null, "dev-" + UUID.randomUUID());
        return service.me(id);
    }
}
