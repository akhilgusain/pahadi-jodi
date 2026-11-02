package in.pahadijodi.airec.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ProfileEventsConsumer {
    private static final Logger log = LoggerFactory.getLogger(ProfileEventsConsumer.class);
    private final ProjectionService projections;
    private final ObjectMapper mapper;

    public ProfileEventsConsumer(ProjectionService projections, ObjectMapper mapper) {
        this.projections = projections; this.mapper = mapper;
    }

    /** EventEnvelope JSON; the payload is the facet document published by profile-service. Idempotent (upsert by profileId). */
    @KafkaListener(topics = "pj.profile.updated.v1", groupId = "ai-recommendation-service",
            autoStartup = "${pj.kafka.consumers.enabled:true}")
    public void onProfileUpdated(String json) {
        try {
            projections.apply(mapper.readTree(json).path("payload"));
        } catch (Exception e) {
            log.warn("Could not process profile event: {}", e.toString());   // defensive: skip a bad message rather than block the partition
        }
    }
}
