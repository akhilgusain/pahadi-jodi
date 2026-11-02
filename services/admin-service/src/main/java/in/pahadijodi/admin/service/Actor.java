package in.pahadijodi.admin.service;

import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/** The acting admin, derived from the JWT. ADMIN and SUPER_ADMIN are both treated as super admin. */
public record Actor(String id, String roles, boolean superAdmin) {
    public static Actor of(Jwt jwt, Authentication auth) {
        var names = auth.getAuthorities().stream().map(a -> a.getAuthority().replaceFirst("^ROLE_", "")).sorted().collect(Collectors.toList());
        return new Actor(jwt.getSubject(), String.join(",", names), names.contains("ADMIN") || names.contains("SUPER_ADMIN"));
    }

    public boolean has(String role) { return superAdmin || ("," + roles + ",").contains("," + role + ","); }
}
