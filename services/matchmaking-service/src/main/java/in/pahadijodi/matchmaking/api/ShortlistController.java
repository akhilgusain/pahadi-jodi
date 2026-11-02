package in.pahadijodi.matchmaking.api;

import static in.pahadijodi.common.security.ResourceServerAutoConfiguration.userId;

import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.matchmaking.domain.ShortlistEntry.Kind;
import in.pahadijodi.matchmaking.service.ShortlistService;
import in.pahadijodi.matchmaking.service.ShortlistService.NoteView;
import in.pahadijodi.matchmaking.service.Views.ProfileCard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** /api/v1/shortlist/{favorites|ignored|blocked}, /notes, /lists. All data is private to the caller. */
@RestController
@RequestMapping("/api/v1/shortlist")
public class ShortlistController {
    public record NoteRequest(@NotBlank @Size(max = 2000) String note) {}

    private final ShortlistService service;

    public ShortlistController(ShortlistService service) { this.service = service; }

    private static Kind kind(String path) {
        return switch (path) {
            case "favorites" -> Kind.FAVORITE;
            case "ignored" -> Kind.IGNORE;
            case "blocked" -> Kind.BLOCK;
            default -> throw new PjException(HttpStatus.NOT_FOUND, "UNKNOWN_LIST", "Unknown list: " + path);
        };
    }

    @GetMapping("/{kind}")
    List<ProfileCard> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String kind) {
        return service.list(userId(jwt), kind(kind), null);
    }

    @PutMapping("/{kind}/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void add(@AuthenticationPrincipal Jwt jwt, @PathVariable String kind, @PathVariable UUID profileId) {
        service.add(userId(jwt), kind(kind), profileId, null);
    }

    @DeleteMapping("/{kind}/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable String kind, @PathVariable UUID profileId) {
        service.remove(userId(jwt), kind(kind), profileId, null);
    }

    // ---- private notes
    @GetMapping("/notes")
    List<NoteView> notes(@AuthenticationPrincipal Jwt jwt) { return service.allNotes(userId(jwt)); }

    @PutMapping("/notes/{profileId}")
    NoteView putNote(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID profileId, @Valid @RequestBody NoteRequest r) {
        return service.putNote(userId(jwt), profileId, r.note());
    }

    @DeleteMapping("/notes/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteNote(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID profileId) {
        service.deleteNote(userId(jwt), profileId);
    }

    // ---- named private lists
    @GetMapping("/lists")
    List<String> lists(@AuthenticationPrincipal Jwt jwt) { return service.listNames(userId(jwt)); }

    @GetMapping("/lists/{name}")
    List<ProfileCard> listMembers(@AuthenticationPrincipal Jwt jwt, @PathVariable String name) {
        return service.list(userId(jwt), Kind.PRIVATE, name);
    }

    @PutMapping("/lists/{name}/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void addToList(@AuthenticationPrincipal Jwt jwt, @PathVariable String name, @PathVariable UUID profileId) {
        service.add(userId(jwt), Kind.PRIVATE, profileId, name);
    }

    @DeleteMapping("/lists/{name}/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeFromList(@AuthenticationPrincipal Jwt jwt, @PathVariable String name, @PathVariable UUID profileId) {
        service.remove(userId(jwt), Kind.PRIVATE, profileId, name);
    }
}
