package in.pahadijodi.search.api;

import in.pahadijodi.search.domain.SearchDoc;
import in.pahadijodi.search.port.SearchPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Dev-only: index a document without Kafka. Only registered when pj.search.dev-endpoints=true (h2 profile). */
@RestController
@RequestMapping("/api/v1/search/dev")
@ConditionalOnProperty(name = "pj.search.dev-endpoints", havingValue = "true")
public class DevIndexController {
    private final SearchPort port;

    public DevIndexController(SearchPort port) { this.port = port; }

    @PutMapping("/index")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void index(@RequestBody SearchDoc doc) { port.index(doc); }
}
