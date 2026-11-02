package in.pahadijodi.payment.gateway;

import in.pahadijodi.common.web.ProblemAdvice.PjException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class GatewayRegistry {
    private final Map<String, PaymentGateway> byProvider;

    public GatewayRegistry(List<PaymentGateway> gateways) {
        this.byProvider = gateways.stream().collect(Collectors.toMap(g -> g.provider().toUpperCase(), Function.identity()));
    }

    public PaymentGateway get(String provider) {
        PaymentGateway g = byProvider.get(provider.toUpperCase());
        if (g == null) throw new PjException(HttpStatus.NOT_FOUND, "UNKNOWN_PROVIDER", "Unknown provider " + provider);
        return g;
    }

    public Collection<PaymentGateway> all() { return byProvider.values(); }

    /** INR is collected through Razorpay (UPI, cards, net banking); everything else through Stripe. */
    public static String providerFor(String currency) { return "INR".equals(currency) ? "RAZORPAY" : "STRIPE"; }
}
