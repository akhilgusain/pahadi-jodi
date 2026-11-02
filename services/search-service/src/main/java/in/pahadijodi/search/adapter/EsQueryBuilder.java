package in.pahadijodi.search.adapter;

import in.pahadijodi.search.domain.SearchCriteria;
import in.pahadijodi.search.port.SearchPort.SortMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Pure function: criteria -> Elasticsearch request body (unit-testable without a cluster). */
public final class EsQueryBuilder {
    private EsQueryBuilder() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> build(SearchCriteria c, UUID excludeUserId, SortMode sort, int from, int size) {
        List<Object> filter = new ArrayList<>();
        List<Object> mustNot = new ArrayList<>();
        List<Object> must = new ArrayList<>();

        if (c.ageMin() != null || c.ageMax() != null) {
            Map<String, Object> r = new LinkedHashMap<>();
            if (c.ageMin() != null) r.put("gte", c.ageMin());
            if (c.ageMax() != null) r.put("lte", c.ageMax());
            filter.add(Map.of("range", Map.of("age", r)));
        }
        term(filter, "gender", c.gender());
        terms(filter, "community", c.community());
        term(filter, "caste", c.caste());
        terms(filter, "district", c.district());
        term(filter, "division", c.division());
        term(filter, "motherTongue", c.motherTongue());
        if (notBlank(c.village())) filter.add(Map.of("match_phrase_prefix", Map.of("village", c.village())));
        if (notBlank(c.education())) filter.add(Map.of("match", Map.of("education", c.education())));
        if (notBlank(c.occupation())) filter.add(Map.of("match", Map.of("occupation", c.occupation())));
        if (excludeUserId != null) mustNot.add(Map.of("term", Map.of("userId", excludeUserId.toString())));

        if (notBlank(c.q())) {
            must.add(Map.of("multi_match", Map.of("query", c.q(), "fuzziness", "AUTO",
                    "fields", List.of("displayName^3", "village^2", "caste^2", "subCaste^2", "gotra^2", "occupation", "education"))));
        } else {
            must.add(Map.of("match_all", Map.of()));
        }

        List<Object> sorts = switch (sort) {
            case RELEVANCE -> List.of("_score", Map.of("completeness", "desc"));
            case COMPLETENESS -> List.of(Map.of("completeness", "desc"), "_score");
            case RECENT -> List.of(Map.of("indexedAt", "desc"));
        };
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("size", size);
        body.put("track_total_hits", true);
        body.put("query", Map.of("bool", Map.of("must", must, "filter", filter, "must_not", mustNot)));
        List<Object> withTie = new ArrayList<>(sorts);
        withTie.add(Map.of("profileId", "asc"));
        body.put("sort", withTie);
        return body;
    }

    /** keyword fields use a lowercase normalizer, so query values are lowercased. */
    private static void term(List<Object> f, String field, String v) {
        if (notBlank(v)) f.add(Map.of("term", Map.of(field, v.trim().toLowerCase(Locale.ROOT))));
    }

    private static void terms(List<Object> f, String field, List<String> v) {
        if (v != null && !v.isEmpty()) {
            f.add(Map.of("terms", Map.of(field, v.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).toList())));
        }
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
