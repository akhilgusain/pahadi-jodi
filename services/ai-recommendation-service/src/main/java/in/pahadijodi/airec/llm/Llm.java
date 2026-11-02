package in.pahadijodi.airec.llm;

import in.pahadijodi.airec.domain.LlmCacheEntry;
import in.pahadijodi.airec.domain.LlmCacheRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The only way to reach an LLM. Pipeline: redact PII -> content-hash cache -> provider -> length clamp -> cache.
 * Callers treat {@link LlmUnavailableException} as "use the template fallback". Prompt/response bodies are never logged.
 */
@Service
public class Llm {
    private static final Logger log = LoggerFactory.getLogger(Llm.class);

    public record Outcome(String text, boolean cached) {}

    private final LlmProvider provider;
    private final Redactor redactor;
    private final LlmCacheRepository cache;
    private final Duration ttl;
    private final int maxChars;

    public Llm(LlmProvider provider, Redactor redactor, LlmCacheRepository cache,
               @Value("${pj.ai.cache-ttl-hours}") long ttlHours, @Value("${pj.ai.max-output-chars}") int maxChars) {
        this.provider = provider; this.redactor = redactor; this.cache = cache;
        this.ttl = Duration.ofHours(ttlHours); this.maxChars = maxChars;
    }

    @SuppressWarnings("unchecked")
    public Outcome chat(LlmRequest raw) {
        LlmRequest req = new LlmRequest(raw.promptKey(), redactor.redact(raw.system()), redactor.redact(raw.user()), raw.tier(),
                raw.temperature(), raw.maxTokens(), raw.json(),
                raw.facts() == null ? Map.of() : (Map<String, Object>) redactor.redactValue(new LinkedHashMap<>(raw.facts())));
        String key = sha256(req.promptKey() + "|" + req.system() + "|" + req.user());
        var hit = cache.findById(key).filter(e -> e.getCreatedAt().isAfter(Instant.now().minus(ttl)));
        if (hit.isPresent()) return new Outcome(hit.get().getResponse(), true);
        String text;
        try {
            text = provider.chat(req);
        } catch (RuntimeException e) {
            log.warn("LLM call failed for {}: {}", req.promptKey(), e.toString());
            throw new LlmUnavailableException("LLM provider failed", e);
        }
        if (text == null || text.isBlank()) throw new LlmUnavailableException("Empty LLM response");
        if (text.length() > maxChars) throw new LlmUnavailableException("LLM response exceeded " + maxChars + " chars");
        cache.save(new LlmCacheEntry(key, req.promptKey(), text));
        return new Outcome(text, false);
    }

    public float[] embed(String text) {
        try {
            return provider.embed(redactor.redact(text));
        } catch (RuntimeException e) {
            throw new LlmUnavailableException("Embedding provider failed", e);
        }
    }

    public String embeddingVersion() { return provider.embeddingVersion(); }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
