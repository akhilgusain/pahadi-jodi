package in.pahadijodi.analytics.port;

import org.springframework.security.oauth2.jwt.Jwt;

/** Does this member's plan include analytics insights (PREMIUM_PLUS or VIP)? Implemented by subscription-service. */
public interface EntitlementPort {
    boolean canViewInsights(Jwt caller);
}
