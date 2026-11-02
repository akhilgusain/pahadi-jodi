package in.pahadijodi.media.api;

import in.pahadijodi.common.security.ResourceServerAutoConfiguration;
import in.pahadijodi.media.api.MediaDtos.*;
import in.pahadijodi.media.domain.AccessGrant;
import in.pahadijodi.media.service.AccessService;
import in.pahadijodi.media.service.MediaService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {
    private final MediaService media;
    private final AccessService access;

    public MediaController(MediaService media, AccessService access) {
        this.media = media;
        this.access = access;
    }

    private static UUID uid(Jwt jwt) { return ResourceServerAutoConfiguration.userId(jwt); }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    DeclareResponse declare(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeclareRequest r) {
        var a = media.declare(uid(jwt), r.kind(), r.contentType(), r.sizeBytes());
        // Fake store: bytes go straight to this service. With S3 this would be a presigned PUT URL.
        return new DeclareResponse(a.getId(), "PUT", "/api/v1/media/uploads/" + a.getId() + "/content",
                "/api/v1/media/uploads/" + a.getId() + "/complete");
    }

    @PutMapping(value = "/uploads/{id}/content", consumes = MediaType.ALL_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void upload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody byte[] body) {
        media.receive(uid(jwt), id, body);
    }

    @PostMapping("/uploads/{id}/complete")
    AssetResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return AssetResponse.from(media.complete(uid(jwt), id));
    }

    @GetMapping("/me")
    List<AssetResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return media.mine(uid(jwt)).stream().map(AssetResponse::from).toList();
    }

    @GetMapping("/{id}/url")
    MediaService.SignedUrl url(@AuthenticationPrincipal Jwt jwt, Authentication auth, @PathVariable UUID id,
                               @RequestParam(defaultValue = "orig") String variant) {
        Set<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        return media.urlFor(uid(jwt), roles, id, variant);
    }

    @PutMapping("/{id}/primary")
    AssetResponse primary(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return AssetResponse.from(media.makePrimary(uid(jwt), id));
    }

    @PatchMapping("/{id}")
    AssetResponse privacy(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody PrivacyRequest r) {
        return AssetResponse.from(media.setPrivacy(uid(jwt), id, r.privacy()));
    }

    @PutMapping("/order")
    List<AssetResponse> order(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OrderRequest r) {
        return media.reorder(uid(jwt), r.ids()).stream().map(AssetResponse::from).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        media.delete(uid(jwt), id);
    }

    // ---- ON_REQUEST photo access ------------------------------------------------------------------

    record AccessView(UUID id, UUID viewerId, UUID ownerId, String status) {
        static AccessView of(AccessGrant g) { return new AccessView(g.getId(), g.getViewerId(), g.getOwnerId(), g.getStatus().name()); }
    }

    @PostMapping("/access-requests")
    @ResponseStatus(HttpStatus.CREATED)
    AccessView requestAccess(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AccessRequestBody b) {
        return AccessView.of(access.request(uid(jwt), b.ownerId()));
    }

    @GetMapping("/access-requests")
    List<AccessView> incoming(@AuthenticationPrincipal Jwt jwt) {
        return access.incoming(uid(jwt)).stream().map(AccessView::of).toList();
    }

    @PutMapping("/access-requests/{id}")
    AccessView decide(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody AccessDecision d) {
        return AccessView.of(access.decide(uid(jwt), id, "GRANT".equalsIgnoreCase(d.decision())));
    }
}
