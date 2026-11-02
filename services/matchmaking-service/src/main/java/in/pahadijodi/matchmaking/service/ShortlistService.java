package in.pahadijodi.matchmaking.service;

import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.matchmaking.domain.ProfileFacet;
import in.pahadijodi.matchmaking.domain.ProfileNote;
import in.pahadijodi.matchmaking.domain.Repositories.*;
import in.pahadijodi.matchmaking.domain.ShortlistEntry;
import in.pahadijodi.matchmaking.domain.ShortlistEntry.Kind;
import in.pahadijodi.matchmaking.service.Views.ProfileCard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Favorites, ignore, block, private notes and named private lists. Everything here is visible only to its owner. */
@Service
public class ShortlistService {
    public record NoteView(UUID profileId, String note, Instant updatedAt) {}

    private final ShortlistRepository entries;
    private final ProfileNoteRepository notes;
    private final ProfileFacetRepository facets;

    public ShortlistService(ShortlistRepository entries, ProfileNoteRepository notes, ProfileFacetRepository facets) {
        this.entries = entries; this.notes = notes; this.facets = facets;
    }

    private ProfileFacet target(UUID userId, UUID profileId) {
        ProfileFacet t = facets.findById(profileId)
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found"));
        if (t.getUserId().equals(userId)) throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "SELF_TARGET", "You cannot do this to your own profile");
        return t;
    }

    /** Idempotent add. */
    @Transactional
    public void add(UUID userId, Kind kind, UUID profileId, String listName) {
        target(userId, profileId);
        String list = kind == Kind.PRIVATE ? clean(listName) : "";
        if (entries.findByUserIdAndTargetProfileIdAndKindAndListName(userId, profileId, kind, list).isEmpty()) {
            entries.save(new ShortlistEntry(userId, profileId, kind, list));
        }
        if (kind == Kind.BLOCK) {                          // a blocked profile cannot stay a favorite
            entries.remove(userId, profileId, Kind.FAVORITE, "");
        }
    }

    @Transactional
    public void remove(UUID userId, Kind kind, UUID profileId, String listName) {
        entries.remove(userId, profileId, kind, kind == Kind.PRIVATE ? clean(listName) : "");
    }

    @Transactional(readOnly = true)
    public List<ProfileCard> list(UUID userId, Kind kind, String listName) {
        List<ShortlistEntry> es = kind == Kind.PRIVATE
                ? entries.findByUserIdAndKindAndListNameOrderByCreatedAtDesc(userId, kind, clean(listName))
                : entries.findByUserIdAndKindOrderByCreatedAtDesc(userId, kind);
        return es.stream().map(e -> facets.findById(e.getTargetProfileId()).map(ProfileCard::of).orElse(null))
                .filter(c -> c != null).toList();
    }

    @Transactional(readOnly = true)
    public List<String> listNames(UUID userId) {
        return entries.findByUserIdAndKindOrderByCreatedAtDesc(userId, Kind.PRIVATE).stream()
                .map(ShortlistEntry::getListName).distinct().sorted().toList();
    }

    @Transactional
    public NoteView putNote(UUID userId, UUID profileId, String text) {
        target(userId, profileId);
        ProfileNote n = notes.findByUserIdAndTargetProfileId(userId, profileId).orElse(null);
        if (n == null) n = notes.save(new ProfileNote(userId, profileId, text)); else n.setNote(text);
        return new NoteView(profileId, n.getNote(), n.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<NoteView> allNotes(UUID userId) {
        return notes.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(n -> new NoteView(n.getTargetProfileId(), n.getNote(), n.getUpdatedAt())).toList();
    }

    @Transactional
    public void deleteNote(UUID userId, UUID profileId) {
        notes.remove(userId, profileId);
    }

    private static String clean(String name) {
        if (name == null || name.isBlank()) throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "LIST_NAME_REQUIRED", "List name required");
        return name.trim();
    }
}
