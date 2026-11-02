package in.pahadijodi.airec.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** pj.ai.provider = fake (default, deterministic, no keys) | real (Azure OpenAI). */
@Configuration
public class LlmConfig {

    @Bean
    @ConditionalOnProperty(name = "pj.ai.provider", havingValue = "fake", matchIfMissing = true)
    FakeLlm fakeLlm(ObjectMapper mapper, CultureCatalog culture) { return new FakeLlm(mapper, culture); }

    @Bean
    @ConditionalOnProperty(name = "pj.ai.provider", havingValue = "real")
    AzureOpenAiLlm azureLlm(@Value("${pj.ai.azure.endpoint}") String endpoint, @Value("${pj.ai.azure.api-key}") String key,
                            @Value("${pj.ai.azure.deployment}") String chat,
                            @Value("${pj.ai.azure.embedding-deployment}") String emb,
                            @Value("${pj.ai.azure.api-version}") String ver, @Value("${pj.ai.azure.timeout-seconds}") int timeout) {
        if (endpoint.isBlank() || key.isBlank() || chat.isBlank()) {
            throw new IllegalStateException("pj.ai.provider=real needs AZURE_OPENAI_ENDPOINT, AZURE_OPENAI_API_KEY and AZURE_OPENAI_DEPLOYMENT");
        }
        return new AzureOpenAiLlm(endpoint, key, chat, emb, ver, timeout);
    }
}
