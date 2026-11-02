package in.pahadijodi.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan({"in.pahadijodi.chat", "in.pahadijodi.common.outbox"})
@EnableJpaRepositories({"in.pahadijodi.chat", "in.pahadijodi.common.outbox"})
public class ChatServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatServiceApplication.class, args);
    }
}
