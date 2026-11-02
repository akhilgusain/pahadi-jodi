package in.pahadijodi.matchmaking.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.matchmaking.domain.ProfileFacet;
import in.pahadijodi.matchmaking.domain.Repositories.ProfileFacetRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the local facet projection in sync with pj.profile.updated.v1. Idempotent upsert keyed by profileId. */
@Component
public class ProfileFacetEventHandler {
    private static final Logger log = LoggerFactory.getLogger(ProfileFacetEventHandler.class);
    private final ProfileFacetRepository repo;
    private final ObjectMapper mapper;

    public ProfileFacetEventHandler(ProfileFacetRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Transactional
    public boolean handle(String envelopeJson) {
        try {
            JsonNode p = mapper.readTree(envelopeJson).path("payload");
            if (p.path("profileId").isMissingNode() || p.path("userId").isMissingNode()) {
                log.warn("Skipping profile event without profileId/userId");
                return false;
            }
            UUID profileId = UUID.fromString(p.path("profileId").asText());
            UUID userId = UUID.fromString(p.path("userId").asText());
            repo.findByUserId(userId).filter(old -> !old.getProfileId().equals(profileId)).ifPresent(repo::delete);
            repo.save(new ProfileFacet(profileId, userId, text(p, "displayName"), text(p, "gender"),
                    p.hasNonNull("age") ? p.get("age").asInt() : null, text(p, "community"), text(p, "caste"), text(p, "subCaste"),
                    text(p, "gotra"), text(p, "district"), text(p, "division"), text(p, "village"), text(p, "education"),
                    text(p, "occupation"), text(p, "motherTongue"), p.path("completeness").asInt(0)));
            return true;
        } catch (Exception e) {
            log.warn("Skipping unparseable profile event: {}", e.toString());
            return false;
        }
    }

    private static String text(JsonNode n, String f) { return n.hasNonNull(f) ? n.get(f).asText() : null; }
}
