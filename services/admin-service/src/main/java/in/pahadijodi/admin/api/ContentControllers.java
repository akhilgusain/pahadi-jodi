package in.pahadijodi.admin.api;

import in.pahadijodi.admin.domain.ContentItem;
import in.pahadijodi.admin.domain.ContentItem.Type;
import in.pahadijodi.admin.service.Actor;
import in.pahadijodi.admin.service.ContentService;
import in.pahadijodi.admin.service.ContentService.Draft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

public final class ContentControllers {
    private ContentControllers() {}

    public record ContentView(UUID id, String type, String slug, String locale, String title, String body, String imageUrl,
                              String linkUrl, int sortOrder, String status, String publishedAt) {
        static ContentView of(ContentItem c) {
            return new ContentView(c.getId(), c.getType().name(), c.getSlug(), c.getLocale(), c.getTitle(), c.getBody(), c.getImageUrl(),
                    c.getLinkUrl(), c.getSortOrder(), c.getStatus().name(), c.getPublishedAt() == null ? null : c.getPublishedAt().toString());
        }
    }

    public record ContentRequest(@NotNull Type type, @NotBlank @Pattern(regexp = "[a-z0-9-]{1,120}") String slug,
                                 @Pattern(regexp = "[a-z]{2}(-[A-Z]{2})?") String locale, @NotBlank @Size(max = 200) String title,
                                 @Size(max = 20000) String body, @Size(max = 500) String imageUrl, @Size(max = 500) String linkUrl,
                                 int sortOrder) {
        Draft toDraft() { return new Draft(type, slug, locale, title, body, imageUrl, linkUrl, sortOrder); }
    }

    /** Unauthenticated, cacheable, published content only (mounted under GET /api/v1/content/**). */
    @RestController
    @RequestMapping("/api/v1/content")
    public static class PublicContentController {
        private final ContentService service;
        public PublicContentController(ContentService service) { this.service = service; }

        @GetMapping("/pages/{slug}")
        ResponseEntity<ContentView> page(@PathVariable String slug, @RequestParam(defaultValue = "en") String locale) {
            return cached(ContentView.of(service.publicPage(slug, locale)));
        }
        @GetMapping("/banners")
        ResponseEntity<List<ContentView>> banners(@RequestParam(defaultValue = "en") String locale) { return cached(list(Type.BANNER, locale)); }
        @GetMapping("/faqs")
        ResponseEntity<List<ContentView>> faqs(@RequestParam(defaultValue = "en") String locale) { return cached(list(Type.FAQ, locale)); }
        @GetMapping("/stories")
        ResponseEntity<List<ContentView>> stories(@RequestParam(defaultValue = "en") String locale) { return cached(list(Type.STORY, locale)); }

        private List<ContentView> list(Type t, String locale) { return service.publicList(t, locale).stream().map(ContentView::of).toList(); }
        private static <T> ResponseEntity<T> cached(T body) {
            return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic()).body(body);
        }
    }

    @RestController
    @RequestMapping("/api/v1/admin/content")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','CONTENT_EDITOR')")
    public static class AdminContentController {
        private final ContentService service;
        public AdminContentController(ContentService service) { this.service = service; }

        @GetMapping List<ContentView> list(@RequestParam(required = false) Type type) { return service.list(type).stream().map(ContentView::of).toList(); }

        @PostMapping @ResponseStatus(HttpStatus.CREATED)
        ContentView create(@AuthenticationPrincipal Jwt jwt, Authentication auth, @Valid @RequestBody ContentRequest r) {
            return ContentView.of(service.create(Actor.of(jwt, auth), r.toDraft()));
        }
        @PutMapping("/{id}")
        ContentView update(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth, @Valid @RequestBody ContentRequest r) {
            return ContentView.of(service.update(Actor.of(jwt, auth), id, r.toDraft()));
        }
        @PostMapping("/{id}/publish")
        ContentView publish(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth) { return ContentView.of(service.publish(Actor.of(jwt, auth), id)); }
        @PostMapping("/{id}/archive")
        ContentView archive(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth) { return ContentView.of(service.archive(Actor.of(jwt, auth), id)); }
        @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
        void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth) { service.delete(Actor.of(jwt, auth), id); }
    }
}
