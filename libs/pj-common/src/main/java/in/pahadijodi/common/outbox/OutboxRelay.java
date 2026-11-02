package in.pahadijodi.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polling relay (dev / small scale). In production switch to Debezium outbox routing
 * and set pj.outbox.relay.enabled=false (see docs/01 ADRs).
 */
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final OutboxRepository repo;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(OutboxRepository repo, KafkaTemplate<String, String> kafka) {
        this.repo = repo;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${pj.outbox.relay.interval-ms:1000}")
    @Transactional
    public void relay() {
        for (OutboxEvent e : repo.findUnpublished(PageRequest.of(0, 100))) {
            try {
                kafka.send(e.getTopic(), e.getPartitionKey(), e.getEnvelope()).get(5, java.util.concurrent.TimeUnit.SECONDS);
                e.markPublished();
            } catch (Exception ex) {
                log.warn("Outbox publish failed for {} ({}); will retry", e.getId(), ex.toString());
                return; // preserve ordering; retry next tick
            }
        }
    }
}
