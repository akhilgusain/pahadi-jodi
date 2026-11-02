package in.pahadijodi.notification.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

/**
 * JWT resource server. Browsers' EventSource cannot send an Authorization header, so ONLY the SSE stream path also
 * accepts the JWT as ?token= (every other endpoint requires the Authorization header). Mask "token" in access logs.
 */
@Configuration
public class SecurityConfig {
    static final String STREAM_PATH = "/api/v1/notifications/stream";

    @Bean
    SecurityFilterChain notificationChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health/**").permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.bearerTokenResolver(bearerTokenResolver()).jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    BearerTokenResolver bearerTokenResolver() {
        var header = new DefaultBearerTokenResolver();
        return (HttpServletRequest req) -> {
            String t = header.resolve(req);
            if (t != null) return t;
            if (STREAM_PATH.equals(req.getRequestURI())) {
                String q = req.getParameter("token");
                if (q != null && !q.isBlank()) return q;
            }
            return null;
        };
    }
}
