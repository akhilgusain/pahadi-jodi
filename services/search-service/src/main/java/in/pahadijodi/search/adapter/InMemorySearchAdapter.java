package in.pahadijodi.search.adapter;

import static in.pahadijodi.search.service.Text.norm;

import in.pahadijodi.search.domain.SearchCriteria;
import in.pahadijodi.search.domain.SearchDoc;
import in.pahadijodi.search.port.SearchPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Fake adapter: whole index in a ConcurrentHashMap with the same filter semantics as the ES query. Dev/test only. */
@Component
@ConditionalOnProperty(name = "pj.search.provider", havingValue = "fake", matchIfMissing = true)
public class InMemorySearchAdapter implements SearchPort {
    private record Stored(SearchDoc doc, Instant indexedAt) {}

    private final Map<UUID, Stored> store = new ConcurrentHashMap<>();

    @Override
    public void index(SearchDoc doc) {
        store.put(doc.profileId(), new Stored(doc, Instant.now()));
    }

    /** Test/dev helper: empties the fake index. */
    public void clear() {
        store.clear();
    }

    @Override
    public void delete(UUID profileId) {
        store.remove(profileId);
    }

    @Override
    public Page search(SearchCriteria c, UUID excludeUserId, SortMode sort, int offset, int size) {
        List<Hit> all = new ArrayList<>();
        List<Stored> matched = new ArrayList<>();
        for (Stored s : store.values()) {
            if (excludeUserId != null && excludeUserId.equals(s.doc().userId())) continue;
            if (!matches(c, s.doc())) continue;
            matched.add(s);
        }
        Comparator<Hit> cmp = switch (sort) {
            case COMPLETENESS -> Comparator.comparingInt((Hit h) -> nz(h.doc().completeness())).reversed();
            case RECENT -> Comparator.comparing((Hit h) -> store.get(h.doc().profileId()).indexedAt()).reversed();
            case RELEVANCE -> Comparator.comparingDouble(Hit::score).reversed();
        };
        for (Stored s : matched) all.add(new Hit(s.doc(), score(c, s.doc())));
        all.sort(cmp.thenComparing(h -> h.doc().profileId()));
        int from = Math.min(offset, all.size());
        int to = Math.min(from + size, all.size());
        return new Page(List.copyOf(all.subList(from, to)), all.size());
    }

    static boolean matches(SearchCriteria c, SearchDoc d) {
        if (c.ageMin() != null && (d.age() == null || d.age() < c.ageMin())) return false;
        if (c.ageMax() != null && (d.age() == null || d.age() > c.ageMax())) return false;
        if (!eq(c.gender(), d.gender())) return false;
        if (!in(c.community(), d.community())) return false;
        if (!eq(c.caste(), d.caste())) return false;
        if (!in(c.district(), d.district())) return false;
        if (!eq(c.division(), d.division())) return false;
        if (!eq(c.motherTongue(), d.motherTongue())) return false;
        if (blank(c.village()) == false && !norm(d.village()).startsWith(norm(c.village()))) return false;
        if (blank(c.education()) == false && !norm(d.education()).contains(norm(c.education()))) return false;
        if (blank(c.occupation()) == false && !norm(d.occupation()).contains(norm(c.occupation()))) return false;
        return blank(c.q()) || textScore(c.q(), d) > 0;
    }

    static double score(SearchCriteria c, SearchDoc d) {
        double text = blank(c.q()) ? 0 : textScore(c.q(), d);
        return text + nz(d.completeness()) / 100.0;
    }

    /** Token match weights mimic the ES multi_match boosts: name 3, village/caste/gotra 2, occupation/education 1. */
    private static double textScore(String q, SearchDoc d) {
        double s = 0;
        for (String t : Arrays.stream(norm(q).split("\\s+")).filter(x -> !x.isEmpty()).toList()) {
            s += hit(t, d.displayName()) * 3 + hit(t, d.village()) * 2 + hit(t, d.caste()) * 2
                    + hit(t, d.subCaste()) * 2 + hit(t, d.gotra()) * 2 + hit(t, d.occupation()) + hit(t, d.education());
        }
        return s;
    }

    private static int hit(String token, String field) { return norm(field).contains(token) ? 1 : 0; }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static int nz(Integer i) { return i == null ? 0 : i; }
    private static boolean eq(String want, String have) { return blank(want) || norm(want).equals(norm(have)); }
    private static boolean in(List<String> want, String have) {
        return want == null || want.isEmpty() || want.stream().anyMatch(w -> norm(w).equals(norm(have)));
    }
}
