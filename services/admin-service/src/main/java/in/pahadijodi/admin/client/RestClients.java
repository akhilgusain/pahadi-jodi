package in.pahadijodi.admin.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

/**
 * pj.admin.provider=real: calls the downstream services with the calling admin's own JWT (so each service
 * re-authorizes the admin). The user-service admin paths below are ASSUMED and must be aligned with that service:
 *   GET  /api/v1/admin/users?q=&page=&size=      -> {items:[...], total}
 *   GET  /api/v1/admin/users/{id}
 *   POST /api/v1/admin/users/{id}/deactivate | /ban | /unban | /force-logout
 */
@Configuration
@ConditionalOnProperty(name = "pj.admin.provider", havingValue = "real")
public class RestClients {

    private static RestClient client(String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).requestInterceptor((req, body, exec) -> {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a instanceof JwtAuthenticationToken t) req.getHeaders().setBearerAuth(t.getToken().getTokenValue());
            return exec.execute(req, body);
        }).build();
    }

    @Bean
    UserDirectoryClient userDirectoryClient(@Value("${pj.admin.clients.user-url}") String url) {
        RestClient rc = client(url);
        return new UserDirectoryClient() {
            @Override public PageResult<UserSummary> search(String q, int page, int size) {
                JsonNode n = rc.get().uri(u -> u.path("/api/v1/admin/users").queryParam("q", q == null ? "" : q)
                        .queryParam("page", page).queryParam("size", size).build()).retrieve().body(JsonNode.class);
                List<UserSummary> items = new ArrayList<>();
                for (JsonNode i : n.path("items")) items.add(toSummary(i));
                return new PageResult<>(items, n.path("total").asLong(items.size()), page, size);
            }
            @Override public Optional<UserSummary> find(UUID id) {
                try { return Optional.of(toSummary(rc.get().uri("/api/v1/admin/users/{id}", id).retrieve().body(JsonNode.class))); }
                catch (org.springframework.web.client.HttpClientErrorException.NotFound e) { return Optional.empty(); }
            }
            @Override public void deactivate(UUID id) { rc.post().uri("/api/v1/admin/users/{id}/deactivate", id).retrieve().toBodilessEntity(); }
            @Override public void setBanned(UUID id, boolean banned, String reason, Instant until) {
                rc.post().uri("/api/v1/admin/users/{id}/" + (banned ? "ban" : "unban"), id).contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", reason == null ? "" : reason, "until", until == null ? "" : until.toString()))
                        .retrieve().toBodilessEntity();
            }
            @Override public void forceLogout(UUID id) { rc.post().uri("/api/v1/admin/users/{id}/force-logout", id).retrieve().toBodilessEntity(); }
        };
    }

    private static UserDirectoryClient.UserSummary toSummary(JsonNode i) {
        return new UserDirectoryClient.UserSummary(UUID.fromString(i.path("id").asText()),
                UserDirectoryClient.maskEmail(i.path("email").asText(i.path("maskedEmail").asText(""))), i.path("displayName").asText(""),
                i.path("district").asText(""), i.path("status").asText("ACTIVE"), i.path("plan").asText("FREE"),
                i.hasNonNull("createdAt") ? Instant.parse(i.get("createdAt").asText()) : null);
    }

    /** KPI sources are best-effort: a failing one is reported in "degraded", it never fails the dashboard. */
    @Bean
    DashboardClient dashboardClient(@Value("${pj.admin.clients.user-url}") String userUrl,
                                    @Value("${pj.admin.clients.subscription-url}") String subUrl,
                                    @Value("${pj.admin.clients.payment-url}") String payUrl,
                                    @Value("${pj.admin.clients.fraud-url}") String fraudUrl,
                                    @Value("${pj.admin.clients.moderation-url}") String modUrl) {
        RestClient users = client(userUrl), subs = client(subUrl), pay = client(payUrl), fraud = client(fraudUrl), mod = client(modUrl);
        return () -> {
            List<String> degraded = new ArrayList<>();
            Long total = null, fresh = null, active = null, today = null, mtd = null, open = null, queue = null;
            Map<String, Long> byPlan = null;
            try { JsonNode n = users.get().uri("/api/v1/admin/stats").retrieve().body(JsonNode.class);
                total = n.path("total").asLong(); fresh = n.path("new7d").asLong(); } catch (Exception e) { degraded.add("user-service"); }
            try { JsonNode n = subs.get().uri("/api/v1/admin/stats").retrieve().body(JsonNode.class);
                active = n.path("active").asLong(); byPlan = new LinkedHashMap<>();
                for (var it = n.path("byPlan").fields(); it.hasNext(); ) { var f = it.next(); byPlan.put(f.getKey(), f.getValue().asLong()); }
            } catch (Exception e) { degraded.add("subscription-service"); }
            try { JsonNode n = pay.get().uri("/api/v1/admin/revenue").retrieve().body(JsonNode.class);
                today = n.path("todayPaise").asLong(); mtd = n.path("monthToDatePaise").asLong(); } catch (Exception e) { degraded.add("payment-service"); }
            try { open = fraud.get().uri("/api/v1/fraud/admin/summary").retrieve().body(JsonNode.class).path("openCases").asLong(); }
            catch (Exception e) { degraded.add("fraud-detection-service"); }
            try { queue = mod.get().uri("/api/v1/moderation/admin/queue-size").retrieve().body(JsonNode.class).path("size").asLong(); }
            catch (Exception e) { degraded.add("moderation-service"); }
            return new DashboardClient.Kpis(total, fresh, active, byPlan, today, mtd, open, queue, degraded);
        };
    }
}
