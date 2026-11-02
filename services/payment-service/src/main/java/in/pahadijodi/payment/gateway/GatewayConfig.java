package in.pahadijodi.payment.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** pj.payment.provider = fake (default, no keys needed) | real (Razorpay for INR, Stripe otherwise). */
@Configuration
public class GatewayConfig {

    @Bean
    @ConditionalOnProperty(name = "pj.payment.provider", havingValue = "fake", matchIfMissing = true)
    FakeGateway fakeRazorpay(@Value("${pj.payment.fake.webhook-secret}") String secret, ObjectMapper m) {
        return new FakeGateway("RAZORPAY", secret, m);
    }

    @Bean
    @ConditionalOnProperty(name = "pj.payment.provider", havingValue = "fake", matchIfMissing = true)
    FakeGateway fakeStripe(@Value("${pj.payment.fake.webhook-secret}") String secret, ObjectMapper m) {
        return new FakeGateway("STRIPE", secret, m);
    }

    @Bean
    @ConditionalOnProperty(name = "pj.payment.provider", havingValue = "real")
    RazorpayGateway razorpay(@Value("${pj.payment.razorpay.base-url}") String url,
                             @Value("${pj.payment.razorpay.key-id}") String id,
                             @Value("${pj.payment.razorpay.key-secret}") String secret,
                             @Value("${pj.payment.razorpay.webhook-secret}") String whSecret, ObjectMapper m) {
        return new RazorpayGateway(url, id, secret, whSecret, m);
    }

    @Bean
    @ConditionalOnProperty(name = "pj.payment.provider", havingValue = "real")
    StripeGateway stripe(@Value("${pj.payment.stripe.base-url}") String url,
                         @Value("${pj.payment.stripe.secret-key}") String key,
                         @Value("${pj.payment.stripe.webhook-secret}") String whSecret, ObjectMapper m) {
        return new StripeGateway(url, key, whSecret, m);
    }
}
