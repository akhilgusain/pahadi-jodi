package in.pahadijodi.fraud.service;

import in.pahadijodi.fraud.engine.RiskEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {
    @Bean
    RiskEngine riskEngine(@Value("${pj.fraud.thresholds.step-up:30}") int stepUp,
                          @Value("${pj.fraud.thresholds.shadow-limit:60}") int shadow,
                          @Value("${pj.fraud.thresholds.block:80}") int block) {
        return new RiskEngine(new RiskEngine.Thresholds(stepUp, shadow, block));
    }
}
