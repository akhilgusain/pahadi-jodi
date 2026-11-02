package in.pahadijodi.analytics.kafka;

import in.pahadijodi.analytics.service.IngestService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to every pj.* topic by pattern (new topics are picked up within metadata.max.age.ms). Malformed or
 * unknown events are skipped, never retried: analytics must not block or poison the pipeline.
 */
@Component
public class AllEventsConsumer {
    private final IngestService ingest;

    public AllEventsConsumer(IngestService ingest) { this.ingest = ingest; }

    @KafkaListener(topicPattern = "pj\\..*", groupId = "analytics-service", autoStartup = "${pj.kafka.consumers.enabled:true}")
    public void onEvent(ConsumerRecord<String, String> record) {
        ingest.ingest(record.topic(), record.value());
    }
}
