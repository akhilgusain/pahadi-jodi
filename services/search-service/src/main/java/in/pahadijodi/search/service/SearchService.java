package in.pahadijodi.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.search.domain.SavedSearch;
import in.pahadijodi.search.domain.SavedSearchRepository;
import in.pahadijodi.search.domain.SearchCriteria;
import in.pahadijodi.search.domain.SearchDoc;
import in.pahadijodi.search.port.EntitlementClient;
import in.pahadijodi.search.port.SearchPort;
import in.pahadijodi.search.port.SearchPort.SortMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchService {
    /** Public search card. No userId, no contact data, no date of birth. */
    public record Card(UUID profileId, String displayName, Integer age, String gender, String community, String caste,
                       String district, String division, String village, String education, String occupation,
                       Integer completeness, double score) {}

    public record Result(List<Card> items, String nextCursor, long total) {}

    private static final int MAX_OFFSET = 10_000;

    private final SearchPort port;
    private final EntitlementClient entitlements;
    private final SavedSearchRepository saved;
    private final ObjectMapper mapper;

    public SearchService(SearchPort port, EntitlementClient entitlements, SavedSearchRepository saved, ObjectMapper mapper) {
        this.port = port;
        this.entitlements = entitlements;
        this.saved = saved;
        this.mapper = mapper;
    }

    public Result search(UUID userId, SearchCriteria c, SortMode sort, String cursor, int size) {
        var ent = entitlements.forUser(userId);
        if (c.usesAdvancedFilters() && !ent.advancedFilters()) {
            throw new PjException(HttpStatus.FORBIDDEN, "UPGRADE_REQUIRED", "Advanced filters need a Premium plan");
        }
        int pageSize = Math.max(1, Math.min(size, ent.maxPageSize()));
        int offset = decode(cursor);
        var page = port.search(c, userId, sort, offset, pageSize);
        List<Card> items = page.hits().stream().map(h -> card(h.doc(), h.score())).toList();
        int next = offset + items.size();
        String nextCursor = (next < page.total() && next < MAX_OFFSET && !items.isEmpty()) ? encode(next) : null;
        return new Result(items, nextCursor, page.total());
    }

    private static Card card(SearchDoc d, double score) {
        return new Card(d.profileId(), d.displayName(), d.age(), d.gender(), d.community(), d.caste(), d.district(),
                d.division(), d.village(), d.education(), d.occupation(), d.completeness(), score);
    }

    static String encode(int offset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(("o:" + offset).getBytes(StandardCharsets.UTF_8));
    }

    static int decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            String s = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int o = Integer.parseInt(s.substring(2));
            if (!s.startsWith("o:") || o < 0 || o > MAX_OFFSET) throw new IllegalArgumentException();
            return o;
        } catch (RuntimeException e) {
            throw new PjException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "Invalid cursor");
        }
    }

    // ---- saved searches ----

    @Transactional
    public SavedSearch create(UUID userId, String name, SearchCriteria criteria) {
        if (saved.countByUserId(userId) >= entitlements.forUser(userId).maxSavedSearches()) {
            throw new PjException(HttpStatus.FORBIDDEN, "SAVED_SEARCH_LIMIT", "Saved search limit reached");
        }
        return saved.save(new SavedSearch(userId, name, json(criteria)));
    }

    @Transactional(readOnly = true)
    public List<SavedSearch> list(UUID userId) {
        return saved.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public SavedSearch get(UUID userId, UUID id) {
        return saved.findById(id).filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "SAVED_SEARCH_NOT_FOUND", "Saved search not found"));
    }

    @Transactional
    public SavedSearch update(UUID userId, UUID id, String name, SearchCriteria criteria) {
        SavedSearch s = get(userId, id);
        s.update(name, json(criteria));
        return s;
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        saved.delete(get(userId, id));
    }

    public SearchCriteria criteriaOf(SavedSearch s) {
        try {
            return mapper.readValue(s.getCriteriaJson(), SearchCriteria.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt saved search " + s.getId(), e);
        }
    }

    private String json(SearchCriteria c) {
        try {
            return mapper.writeValueAsString(c);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
