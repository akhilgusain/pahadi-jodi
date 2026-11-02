package in.pahadijodi.airec.llm;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Azure OpenAI adapter (chat completions + embeddings). Env: AZURE_OPENAI_ENDPOINT, AZURE_OPENAI_API_KEY,
 * AZURE_OPENAI_DEPLOYMENT, AZURE_OPENAI_EMBEDDING_DEPLOYMENT. Written against the public REST contract; NOT exercised
 * against a live Azure resource in this repo. No retry/circuit breaker yet: a failure surfaces as LlmUnavailable and callers fall back.
 */
public class AzureOpenAiLlm implements LlmProvider {
    private final RestClient http;
    private final String chatDeployment;
    private final String embeddingDeployment;
    private final String apiVersion;

    public AzureOpenAiLlm(String endpoint, String apiKey, String chatDeployment, String embeddingDeployment,
                          String apiVersion, int timeoutSeconds) {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(3));
        f.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.http = RestClient.builder().requestFactory(f).baseUrl(endpoint).defaultHeader("api-key", apiKey).build();
        this.chatDeployment = chatDeployment; this.embeddingDeployment = embeddingDeployment; this.apiVersion = apiVersion;
    }

    @Override
    public String chat(LlmRequest r) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("messages", List.of(Map.of("role", "system", "content", r.system()), Map.of("role", "user", "content", r.user())));
        body.put("temperature", r.temperature());
        body.put("max_tokens", r.maxTokens());
        if (r.json()) body.put("response_format", Map.of("type", "json_object"));
        JsonNode res = http.post().uri("/openai/deployments/{d}/chat/completions?api-version={v}", chatDeployment, apiVersion)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        return res.path("choices").path(0).path("message").path("content").asText(null);
    }

    @Override
    public float[] embed(String text) {
        JsonNode res = http.post().uri("/openai/deployments/{d}/embeddings?api-version={v}", embeddingDeployment, apiVersion)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("input", text)).retrieve().body(JsonNode.class);
        JsonNode e = res.path("data").path(0).path("embedding");
        float[] v = new float[e.size()];
        for (int i = 0; i < v.length; i++) v[i] = (float) e.get(i).asDouble();
        return v;
    }

    @Override public String embeddingVersion() { return "azure-" + embeddingDeployment; }
}
