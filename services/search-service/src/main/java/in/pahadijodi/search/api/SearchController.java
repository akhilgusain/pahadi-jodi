package in.pahadijodi.search.api;

import in.pahadijodi.common.security.ResourceServerAutoConfiguration;
import in.pahadijodi.search.domain.SearchCriteria;
import in.pahadijodi.search.port.SearchPort.SortMode;
import in.pahadijodi.search.service.SearchService;
import in.pahadijodi.search.service.SearchService.Result;
import in.pahadijodi.search.service.VillageCatalog;
import in.pahadijodi.search.service.VillageCatalog.Village;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
    private final SearchService service;
    private final VillageCatalog villages;

    public SearchController(SearchService service, VillageCatalog villages) {
        this.service = service;
        this.villages = villages;
    }

    @GetMapping("/profiles")
    Result profiles(@AuthenticationPrincipal Jwt jwt,
                    @RequestParam(required = false) Integer ageMin, @RequestParam(required = false) Integer ageMax,
                    @RequestParam(required = false) String gender, @RequestParam(required = false) List<String> community,
                    @RequestParam(required = false) String caste, @RequestParam(required = false) List<String> district,
                    @RequestParam(required = false) String division, @RequestParam(required = false) String village,
                    @RequestParam(required = false) String education, @RequestParam(required = false) String occupation,
                    @RequestParam(required = false) String motherTongue, @RequestParam(required = false) String q,
                    @RequestParam(defaultValue = "RELEVANCE") SortMode sort,
                    @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int size) {
        var criteria = new SearchCriteria(ageMin, ageMax, gender, community, caste, district, division, village,
                education, occupation, motherTongue, q);
        return service.search(ResourceServerAutoConfiguration.userId(jwt), criteria, sort, cursor, size);
    }

    @GetMapping("/villages/suggest")
    List<Village> suggest(@RequestParam String q, @RequestParam(defaultValue = "8") int limit) {
        return villages.suggest(q, Math.max(1, Math.min(limit, 20)));
    }
}
