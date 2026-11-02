package in.pahadijodi.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan({"in.pahadijodi.payment", "in.pahadijodi.common.outbox"})
@EnableJpaRepositories({"in.pahadijodi.payment", "in.pahadijodi.common.outbox"})
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
