package in.pahadijodi.search.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.search.domain.SearchCriteria;
import in.pahadijodi.search.domain.SearchDoc;
import in.pahadijodi.search.port.SearchPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real adapter: Elasticsearch REST API through aliases (docs/02 section 6.3). Writes go to the write alias,
 * reads to the read alias, so reindexing into pj-profiles-v{N+1} is a zero-downtime alias swap.
 * Config: ES_URL, ES_API_KEY (optional). Not exercised by automated tests (needs a cluster).
 * Deviation: the bootstrap mapping omits the ICU transliteration analyzers (needs the analysis-icu plugin);
 * add them when your cluster has the plugin.
 */
@Component
@ConditionalOnProperty(name = "pj.search.provider", havingValue = "real")
public class ElasticsearchSearchAdapter implements SearchPort, ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSearchAdapter.class);

    private final RestClient http;
    private final ObjectMapper mapper;
    private final String readAlias;
    private final String writeAlias;
    private final String bootstrapIndex;

    public ElasticsearchSearchAdapter(ObjectMapper mapper,
                                      @Value("${pj.search.es.url}") String url,
                                      @Value("${pj.search.es.api-key:}") String apiKey,
                                      @Value("${pj.search.es.read-alias}") String readAlias,
                                      @Value("${pj.search.es.write-alias}") String writeAlias,
                                      @Value("${pj.search.es.bootstrap-index}") String bootstrapIndex) {
        var b = RestClient.builder().baseUrl(url).defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if (apiKey != null && !apiKey.isBlank()) b.defaultHeader("Authorization", "ApiKey " + apiKey);
        this.http = b.build();
        this.mapper = mapper;
        this.readAlias = readAlias;
        this.writeAlias = writeAlias;
        this.bootstrapIndex = bootstrapIndex;
    }

    /** Creates the first index + aliases when absent. Failure is logged, not fatal (cluster may start later). */
    @Override
    public void run(ApplicationArguments args) {
        try {
            var exists = http.head().uri("/{a}", writeAlias).retrieve().toBodilessEntity().getStatusCode().is2xxSuccessful();
            if (exists) return;
        } catch (Exception notFound) {
            // fall through to create
        }
        try {
            Map<String, Object> body = Map.of(
                    "settings", Map.of("number_of_shards", 1, "number_of_replicas", 0, "analysis",
                            Map.of("normalizer", Map.of("lc", Map.of("type", "custom", "filter", List.of("lowercase", "asciifolding"))))),
                    "aliases", Map.of(readAlias, Map.of(), writeAlias, Map.of("is_write_index", true)),
                    "mappings", mapping());
            http.put().uri("/{i}", bootstrapIndex).body(body).retrieve().toBodilessEntity();
            log.info("Created index {} with aliases {}, {}", bootstrapIndex, readAlias, writeAlias);
        } catch (Exception e) {
            log.warn("Could not bootstrap Elasticsearch index: {}", e.toString());
        }
    }

    private static Map<String, Object> mapping() {
        Map<String, Object> p = new LinkedHashMap<>();
        for (String f : List.of("profileId", "userId")) p.put(f, Map.of("type", "keyword"));
        for (String f : List.of("gender", "community", "caste", "subCaste", "gotra", "district", "division", "motherTongue")) {
            p.put(f, Map.of("type", "keyword", "normalizer", "lc"));
        }
        for (String f : List.of("displayName", "village", "education", "occupation")) p.put(f, Map.of("type", "text"));
        p.put("age", Map.of("type", "integer"));
        p.put("completeness", Map.of("type", "integer"));
        p.put("indexedAt", Map.of("type", "date"));
        return Map.of("dynamic", "strict", "properties", p);
    }

    @Override
    public void index(SearchDoc doc) {
        Map<String, Object> src = mapper.convertValue(doc, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        src.put("indexedAt", Instant.now().toString());
        http.put().uri("/{a}/_doc/{id}", writeAlias, doc.profileId()).body(src).retrieve().toBodilessEntity();
    }

    @Override
    public void delete(UUID profileId) {
        http.delete().uri("/{a}/_doc/{id}", writeAlias, profileId).retrieve().toBodilessEntity();
    }

    @Override
    public Page search(SearchCriteria c, UUID excludeUserId, SortMode sort, int offset, int size) {
        var body = EsQueryBuilder.build(c, excludeUserId, sort, offset, size);
        JsonNode res = http.post().uri("/{a}/_search", readAlias).body(body).retrieve().body(JsonNode.class);
        List<Hit> hits = new ArrayList<>();
        for (JsonNode h : res.path("hits").path("hits")) {
            SearchDoc d = mapper.convertValue(h.path("_source"), SearchDoc.class);
            hits.add(new Hit(d, h.path("_score").asDouble(0)));
        }
        return new Page(hits, res.path("hits").path("total").path("value").asLong(hits.size()));
    }
}
