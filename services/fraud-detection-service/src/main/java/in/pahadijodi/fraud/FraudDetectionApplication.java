package in.pahadijodi.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan({"in.pahadijodi.fraud", "in.pahadijodi.common.outbox"})
@EnableJpaRepositories(basePackages = {"in.pahadijodi.fraud", "in.pahadijodi.common.outbox"}, considerNestedRepositories = true)
public class FraudDetectionApplication {
    public static void main(String[] args) {
        SpringApplication.run(FraudDetectionApplication.class, args);
    }
}
