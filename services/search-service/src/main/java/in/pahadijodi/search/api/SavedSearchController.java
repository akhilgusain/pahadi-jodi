package in.pahadijodi.search.api;

import in.pahadijodi.common.security.ResourceServerAutoConfiguration;
import in.pahadijodi.search.domain.SavedSearch;
import in.pahadijodi.search.domain.SearchCriteria;
import in.pahadijodi.search.port.SearchPort.SortMode;
import in.pahadijodi.search.service.SearchService;
import in.pahadijodi.search.service.SearchService.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search/saved")
public class SavedSearchController {
    public record SavedRequest(@NotBlank @Size(max = 100) String name, @NotNull SearchCriteria criteria) {}
    public record SavedResponse(UUID id, String name, SearchCriteria criteria, Instant createdAt, Instant updatedAt) {}

    private final SearchService service;

    public SavedSearchController(SearchService service) { this.service = service; }

    private SavedResponse view(SavedSearch s) {
        return new SavedResponse(s.getId(), s.getName(), service.criteriaOf(s), s.getCreatedAt(), s.getUpdatedAt());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SavedResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SavedRequest r) {
        return view(service.create(ResourceServerAutoConfiguration.userId(jwt), r.name(), r.criteria()));
    }

    @GetMapping
    List<SavedResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(ResourceServerAutoConfiguration.userId(jwt)).stream().map(this::view).toList();
    }

    @GetMapping("/{id}")
    SavedResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return view(service.get(ResourceServerAutoConfiguration.userId(jwt), id));
    }

    @PutMapping("/{id}")
    SavedResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody SavedRequest r) {
        return view(service.update(ResourceServerAutoConfiguration.userId(jwt), id, r.name(), r.criteria()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(ResourceServerAutoConfiguration.userId(jwt), id);
    }

    /** Re-runs a saved search with its stored criteria. */
    @GetMapping("/{id}/run")
    Result run(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
               @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int size) {
        UUID user = ResourceServerAutoConfiguration.userId(jwt);
        return service.search(user, service.criteriaOf(service.get(user, id)), SortMode.RELEVANCE, cursor, size);
    }
}
