package in.pahadijodi.analytics.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import in.pahadijodi.analytics.port.EntitlementPort;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;

@Configuration
public class EntitlementAdapters {
    private static final Set<String> ELIGIBLE = Set.of("PREMIUM_PLUS", "VIP");

    /**
     * Local/dev: the plan comes from a "plan" claim on the JWT if present (handy for testing the 403 path),
     * otherwise from pj.analytics.entitlements.fake-plan. Not a security control.
     */
    @Bean
    @ConditionalOnProperty(name = "pj.analytics.entitlements.provider", havingValue = "fake", matchIfMissing = true)
    EntitlementPort fakeEntitlements(@Value("${pj.analytics.entitlements.fake-plan:PREMIUM_PLUS}") String defaultPlan) {
        return jwt -> {
            String plan = jwt.getClaimAsString("plan");
            return ELIGIBLE.contains((plan == null ? defaultPlan : plan).toUpperCase(Locale.ROOT));
        };
    }

    /**
     * Real: asks subscription-service for the caller's plan using the caller's own bearer token.
     * ASSUMED contract (verify against subscription-service when wiring): GET {url}/api/v1/subscriptions/me
     * returns JSON with a plan code under "plan", "planCode" or "tier". Result is cached for 60 s per member.
     * Fails closed: any error means "not entitled".
     */
    @Bean
    @ConditionalOnProperty(name = "pj.analytics.entitlements.provider", havingValue = "real")
    EntitlementPort subscriptionEntitlements(@Value("${pj.analytics.entitlements.subscription-url}") String url) {
        RestClient client = RestClient.builder().baseUrl(url).build();
        Cache<String, Boolean> cache = Caffeine.newBuilder().maximumSize(50_000).expireAfterWrite(Duration.ofSeconds(60)).build();
        return jwt -> cache.get(jwt.getSubject(), sub -> {
            try {
                JsonNode body = client.get().uri("/api/v1/subscriptions/me")
                        .header("Authorization", "Bearer " + jwt.getTokenValue()).retrieve().body(JsonNode.class);
                if (body == null) return false;
                for (String f : new String[]{"plan", "planCode", "tier"}) {
                    if (body.hasNonNull(f) && ELIGIBLE.contains(body.get(f).asText().toUpperCase(Locale.ROOT))) return true;
                }
                return false;
            } catch (Exception e) {
                return false;
            }
        });
    }
}
