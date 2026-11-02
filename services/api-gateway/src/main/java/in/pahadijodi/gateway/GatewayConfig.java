package in.pahadijodi.gateway;

import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class GatewayConfig {

    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(a -> a
                        .pathMatchers("/api/v1/auth/**", "/actuator/health/**").permitAll()
                        // services that authenticate these themselves, or that are public by design
                        .pathMatchers("/ws/**", "/api/v1/notifications/stream", "/api/v1/payments/webhooks/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/content/**", "/api/v1/subscriptions/plans",
                                "/api/v1/media/files/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    @org.springframework.context.annotation.Primary
    KeyResolver ipKeyResolver() {
        return ex -> Mono.just(Optional.ofNullable(ex.getRequest().getRemoteAddress())
                .map(a -> a.getAddress().getHostAddress()).orElse("unknown"));
    }

    @Bean
    KeyResolver userKeyResolver() {
        return ex -> ReactiveSecurityContextHolder.getContext()
                .map(c -> c.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(t -> ((Jwt) t.getPrincipal()).getSubject())
                .defaultIfEmpty("anonymous");
    }
}
