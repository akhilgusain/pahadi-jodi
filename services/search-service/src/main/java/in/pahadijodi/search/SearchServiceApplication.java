package in.pahadijodi.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan({"in.pahadijodi.search", "in.pahadijodi.common.outbox"})
@EnableJpaRepositories({"in.pahadijodi.search", "in.pahadijodi.common.outbox"})
public class SearchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
