package in.pahadijodi.search.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ProfileEventListener {
    private final ProfileEventHandler handler;

    public ProfileEventListener(ProfileEventHandler handler) { this.handler = handler; }

    @KafkaListener(topics = "pj.profile.updated.v1", groupId = "search-service",
            autoStartup = "${pj.kafka.consumers.enabled:true}")
    void onProfileUpdated(String message) {
        handler.handle(message);
    }
}
