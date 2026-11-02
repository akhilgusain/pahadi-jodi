package in.pahadijodi.matchmaking.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ProfileEventListener {
    private final ProfileFacetEventHandler handler;

    public ProfileEventListener(ProfileFacetEventHandler handler) { this.handler = handler; }

    @KafkaListener(topics = "pj.profile.updated.v1", groupId = "matchmaking-service",
            autoStartup = "${pj.kafka.consumers.enabled:true}")
    void onProfileUpdated(String message) {
        handler.handle(message);
    }
}
