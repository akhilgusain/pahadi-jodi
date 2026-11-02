package in.pahadijodi.search.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.search.domain.SearchDoc;
import in.pahadijodi.search.port.SearchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Turns pj.profile.updated.v1 envelopes into index writes. Idempotent (document id = profileId). */
@Component
public class ProfileEventHandler {
    private static final Logger log = LoggerFactory.getLogger(ProfileEventHandler.class);
    private final SearchPort port;
    private final ObjectMapper mapper;

    public ProfileEventHandler(SearchPort port, ObjectMapper mapper) {
        this.port = port;
        this.mapper = mapper;
    }

    /** @return true when a document was indexed; malformed events are logged and skipped (never poison the consumer). */
    public boolean handle(String envelopeJson) {
        try {
            JsonNode payload = mapper.readTree(envelopeJson).path("payload");
            if (payload.isMissingNode() || payload.path("profileId").isMissingNode()) {
                log.warn("Skipping profile event without profileId");
                return false;
            }
            port.index(mapper.treeToValue(payload, SearchDoc.class));
            return true;
        } catch (Exception e) {
            log.warn("Skipping unparseable profile event: {}", e.toString());
            return false;
        }
    }
}
