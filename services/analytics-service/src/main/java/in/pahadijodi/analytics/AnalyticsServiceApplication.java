package in.pahadijodi.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// The outbox package is scanned because pj-common's OutboxService is auto-configured; analytics publishes nothing yet.
@SpringBootApplication
@EntityScan({"in.pahadijodi.analytics", "in.pahadijodi.common.outbox"})
@EnableJpaRepositories({"in.pahadijodi.analytics", "in.pahadijodi.common.outbox"})
public class AnalyticsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
