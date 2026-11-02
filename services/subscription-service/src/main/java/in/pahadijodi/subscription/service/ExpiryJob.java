package in.pahadijodi.subscription.service;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(ExpiryJob.class);
    private final SubscriptionService service;

    public ExpiryJob(SubscriptionService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${pj.subscription.expiry-sweep-ms:300000}", initialDelay = 30_000)
    public void sweep() {
        int n = service.processExpiries(Instant.now());
        if (n > 0) log.info("Subscription sweep changed {} subscriptions", n);
    }
}
