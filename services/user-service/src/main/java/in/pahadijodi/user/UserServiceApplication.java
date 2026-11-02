package in.pahadijodi.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan({"in.pahadijodi.user", "in.pahadijodi.common.outbox"})
@EnableJpaRepositories(basePackages = {"in.pahadijodi.user", "in.pahadijodi.common.outbox"}, considerNestedRepositories = true)
@EnableScheduling
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
