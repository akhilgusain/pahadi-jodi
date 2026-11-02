package in.pahadijodi.profile.api;

import in.pahadijodi.profile.api.ProfileDtos.ProfileRequest;
import in.pahadijodi.profile.api.ProfileDtos.ProfileResponse;
import in.pahadijodi.profile.service.ProfileService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {
    private final ProfileService service;

    public ProfileController(ProfileService service) { this.service = service; }

    @PutMapping("/me")
    ProfileResponse upsertMine(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest r) {
        return ProfileResponse.from(service.upsert(UUID.fromString(jwt.getSubject()), r));
    }

    @GetMapping("/me")
    ProfileResponse mine(@AuthenticationPrincipal Jwt jwt) {
        return ProfileResponse.from(service.mine(UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/{id}")
    ProfileResponse byId(@PathVariable UUID id) {
        // TODO(privacy): apply viewer-specific field masking + block-list checks (docs/02 profile privacy)
        return ProfileResponse.from(service.byId(id));
    }
}
