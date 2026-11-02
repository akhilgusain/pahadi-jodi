package in.pahadijodi.horoscope;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan({"in.pahadijodi.horoscope", "in.pahadijodi.common.outbox"})
@EnableJpaRepositories({"in.pahadijodi.horoscope", "in.pahadijodi.common.outbox"})
public class HoroscopeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(HoroscopeServiceApplication.class, args);
    }
}
