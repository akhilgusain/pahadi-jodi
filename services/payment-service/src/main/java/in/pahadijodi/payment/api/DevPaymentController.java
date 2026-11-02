package in.pahadijodi.payment.api;

import in.pahadijodi.common.security.ResourceServerAutoConfiguration;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.payment.domain.PaymentOrder;
import in.pahadijodi.payment.gateway.FakeGateway;
import in.pahadijodi.payment.gateway.GatewayRegistry;
import in.pahadijodi.payment.gateway.PaymentGateway;
import in.pahadijodi.payment.service.OrderService;
import in.pahadijodi.payment.service.WebhookService;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * DEV ONLY (pj.payment.dev-endpoints=true, and only with the fake provider): plays the role of "the customer paid in
 * the provider's checkout" by sending a correctly signed webhook through the real webhook pipeline.
 */
@RestController
@RequestMapping("/api/v1/payments/dev")
@ConditionalOnProperty(name = "pj.payment.dev-endpoints", havingValue = "true")
public class DevPaymentController {
    private final OrderService orders;
    private final GatewayRegistry gateways;
    private final WebhookService webhooks;

    public DevPaymentController(OrderService orders, GatewayRegistry gateways, WebhookService webhooks) {
        this.orders = orders; this.gateways = gateways; this.webhooks = webhooks;
    }

    @PostMapping("/complete/{orderId}")
    Map<String, String> complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return send(jwt, orderId, "payment.captured", null);
    }

    @PostMapping("/fail/{orderId}")
    Map<String, String> fail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return send(jwt, orderId, "payment.failed", "BAD_REQUEST_ERROR");
    }

    private Map<String, String> send(Jwt jwt, UUID orderId, String type, String failure) {
        PaymentOrder o = orders.get(orderId);
        if (!o.getUserId().equals(ResourceServerAutoConfiguration.userId(jwt))) {
            throw new PjException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found");
        }
        PaymentGateway gw = gateways.get(o.getProvider());
        if (!(gw instanceof FakeGateway fake)) {
            throw new PjException(HttpStatus.CONFLICT, "NOT_FAKE_PROVIDER", "Dev endpoints only work with pj.payment.provider=fake");
        }
        var wh = fake.simulate(type, o.getProviderOrderId(), o.getTotalMinor(), o.getCurrency(), failure);
        return Map.of("result", webhooks.handle(o.getProvider(), wh.body(), wh.signature(), null).name());
    }
}
