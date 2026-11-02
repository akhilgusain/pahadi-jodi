package in.pahadijodi.subscription;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan({"in.pahadijodi.subscription", "in.pahadijodi.common.outbox"})
@EnableJpaRepositories(basePackages = {"in.pahadijodi.subscription", "in.pahadijodi.common.outbox"}, considerNestedRepositories = true)
@EnableScheduling
public class SubscriptionServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SubscriptionServiceApplication.class, args);
    }
}
