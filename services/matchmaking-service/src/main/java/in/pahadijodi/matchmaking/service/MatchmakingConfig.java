package in.pahadijodi.matchmaking.service;

import in.pahadijodi.matchmaking.port.Ports.AiRerankPort;
import in.pahadijodi.matchmaking.port.Ports.GunaPort;
import in.pahadijodi.matchmaking.scoring.CompatibilityEngine;
import java.util.OptionalInt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MatchmakingConfig {
    @Bean
    CompatibilityEngine compatibilityEngine() { return new CompatibilityEngine(); }

    /** Fake: no horoscope data, so the Horoscope factor is left out and its weight redistributed. */
    @Bean
    @ConditionalOnMissingBean
    GunaPort gunaPort() { return (a, b) -> OptionalInt.empty(); }

    /** Fake: identity re-rank (rule score only). */
    @Bean
    @ConditionalOnMissingBean
    AiRerankPort aiRerankPort() { return (userId, candidates) -> candidates; }
}
