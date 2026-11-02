package in.pahadijodi.search.port;

import in.pahadijodi.search.domain.SearchCriteria;
import in.pahadijodi.search.domain.SearchDoc;
import java.util.List;
import java.util.UUID;

/** Outbound port to the profile index. Adapters: InMemorySearchAdapter (fake) and ElasticsearchSearchAdapter (real). */
public interface SearchPort {
    enum SortMode { RELEVANCE, COMPLETENESS, RECENT }

    record Hit(SearchDoc doc, double score) {}
    record Page(List<Hit> hits, long total) {}

    void index(SearchDoc doc);

    void delete(UUID profileId);

    /** @param excludeUserId profile owner to leave out of results (the caller); may be null */
    Page search(SearchCriteria criteria, UUID excludeUserId, SortMode sort, int offset, int size);
}
