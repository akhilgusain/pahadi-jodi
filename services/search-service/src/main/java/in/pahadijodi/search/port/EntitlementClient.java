package in.pahadijodi.search.port;

import java.util.UUID;

/**
 * HOOK for subscription-service: plan-based quotas for search (docs/02 section 6.9).
 * The default FakeEntitlementClient allows everything; replace with an adapter that calls
 * subscription-service's entitlement check API (cached in Redis) when that service is wired in.
 */
public interface EntitlementClient {
    record Entitlements(int maxPageSize, boolean advancedFilters, int maxSavedSearches) {}

    Entitlements forUser(UUID userId);
}
