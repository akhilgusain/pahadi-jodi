package in.pahadijodi.user.service;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ErasureJob {
    private static final Logger log = LoggerFactory.getLogger(ErasureJob.class);
    private final UserService service;

    public ErasureJob(UserService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${pj.user.erasure-sweep-ms:3600000}", initialDelay = 60_000)
    public void sweep() {
        int n = service.processDueErasures(Instant.now());
        if (n > 0) log.info("Executed {} account erasures", n);
    }
}
