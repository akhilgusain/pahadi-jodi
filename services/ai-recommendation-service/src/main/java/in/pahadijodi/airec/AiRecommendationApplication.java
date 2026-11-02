package in.pahadijodi.airec;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan({"in.pahadijodi.airec", "in.pahadijodi.common.outbox"})
@EnableJpaRepositories({"in.pahadijodi.airec", "in.pahadijodi.common.outbox"})
public class AiRecommendationApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiRecommendationApplication.class, args);
    }
}
