package in.pahadijodi.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.search.adapter.EsQueryBuilder;
import in.pahadijodi.search.domain.SearchCriteria;
import in.pahadijodi.search.port.SearchPort.SortMode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EsQueryBuilderTest {
    @Test
    void buildsBoolQueryWithFiltersExclusionAndSort() throws Exception {
        UUID me = UUID.randomUUID();
        var c = new SearchCriteria(25, 32, "FEMALE", List.of("GARHWALI", "KUMAONI"), "Rajput", null, "GARHWAL", "kand", null, null, null, "anjali");
        String s = new ObjectMapper().writeValueAsString(EsQueryBuilder.build(c, me, SortMode.RECENT, 40, 20));
        assertThat(s).contains("\"from\":40").contains("\"size\":20")
                .contains("\"range\":{\"age\":{\"gte\":25,\"lte\":32}}")
                .contains("\"terms\":{\"community\":[\"garhwali\",\"kumaoni\"]}")
                .contains("\"term\":{\"caste\":\"rajput\"}")
                .contains("\"match_phrase_prefix\":{\"village\":\"kand\"}")
                .contains("\"must_not\":[{\"term\":{\"userId\":\"" + me + "\"}}]")
                .contains("\"multi_match\"").contains("{\"indexedAt\":\"desc\"}");
        assertThat(new ObjectMapper().writeValueAsString(
                EsQueryBuilder.build(new SearchCriteria(null, null, null, null, null, null, null, null, null, null, null, null), null, SortMode.RELEVANCE, 0, 10)))
                .contains("\"match_all\"").contains("\"_score\"");
    }
}
