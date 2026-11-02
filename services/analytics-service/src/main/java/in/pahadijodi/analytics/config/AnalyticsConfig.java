package in.pahadijodi.analytics.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalyticsConfig {
    /** Business clock: "today" and day boundaries follow pj.analytics.timezone (default IST), not the server zone. */
    @Bean
    Clock businessClock(@Value("${pj.analytics.timezone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
