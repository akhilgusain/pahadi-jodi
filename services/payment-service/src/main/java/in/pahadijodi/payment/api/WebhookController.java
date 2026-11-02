package in.pahadijodi.payment.api;

import in.pahadijodi.payment.service.WebhookService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** Public (no JWT): authenticated by the provider's HMAC signature over the raw body. Always answers fast. */
@RestController
public class WebhookController {
    private final WebhookService webhooks;

    public WebhookController(WebhookService webhooks) { this.webhooks = webhooks; }

    @PostMapping("/api/v1/payments/webhooks/{provider}")
    Map<String, String> receive(@PathVariable String provider, @RequestBody String rawBody,
                                @RequestHeader(value = "X-Razorpay-Signature", required = false) String razorpaySig,
                                @RequestHeader(value = "Stripe-Signature", required = false) String stripeSig,
                                @RequestHeader(value = "X-Signature", required = false) String genericSig,
                                @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        String sig = razorpaySig != null ? razorpaySig : stripeSig != null ? stripeSig : genericSig;
        return Map.of("result", webhooks.handle(provider, rawBody, sig, eventId).name());
    }
}
