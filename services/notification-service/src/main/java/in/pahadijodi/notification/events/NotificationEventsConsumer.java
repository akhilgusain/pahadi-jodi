package in.pahadijodi.notification.events;

import in.pahadijodi.notification.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventsConsumer {
    private static final Logger log = LoggerFactory.getLogger(NotificationEventsConsumer.class);
    private final EventRouter router;
    private final NotificationService service;

    public NotificationEventsConsumer(EventRouter router, NotificationService service) {
        this.router = router;
        this.service = service;
    }

    @KafkaListener(autoStartup = "${pj.kafka.consumers.enabled:true}", topics = {
            "${pj.notification.topics.user-registered}", "${pj.notification.topics.interest-sent}",
            "${pj.notification.topics.interest-accepted}", "${pj.notification.topics.match-created}",
            "${pj.notification.topics.chat-message-sent}", "${pj.notification.topics.payment-captured}",
            "${pj.notification.topics.subscription-expired}", "${pj.notification.topics.profile-verified}"})
    public void onEvent(ConsumerRecord<String, String> record) {
        handle(record.topic(), record.value());
    }

    /** Idempotent: a duplicate (same dedupe key) is a no-op even when two consumers race. */
    public void handle(String topic, String json) {
        for (var request : router.route(topic, json)) {
            try {
                service.process(request);
            } catch (DataIntegrityViolationException duplicate) {
                log.debug("duplicate notification {} for {}", request.dedupeKey(), request.userId());
            }
        }
    }
}
