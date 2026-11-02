package in.pahadijodi.airec.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deterministic stand-in for a hosted model: no network, no keys, same JSON contracts as the real prompts.
 * Embeddings are a hashed bag-of-features ("key:value" tokens, weighted) so similar profiles land close together.
 */
public class FakeLlm implements LlmProvider {
    public static final int DIMS = 64;
    public static final String VERSION = "fake-hash-64-v1";
    private static final Map<String, Double> WEIGHTS = Map.of(
            "community", 2.0, "district", 2.0, "division", 1.5, "lang", 1.5, "edu", 1.0, "occ", 1.0, "age", 1.0, "village", 0.5);

    private final ObjectMapper mapper;
    private final CultureCatalog culture;
    private final AtomicBoolean failing = new AtomicBoolean(false);

    public FakeLlm(ObjectMapper mapper, CultureCatalog culture) { this.mapper = mapper; this.culture = culture; }

    /** Test/dev switch to simulate a provider outage (exercises the template fallbacks). */
    public void setFailing(boolean failing) { this.failing.set(failing); }

    @Override
    public String chat(LlmRequest r) {
        if (failing.get()) throw new IllegalStateException("fake provider outage");
        try {
            ObjectNode out = mapper.createObjectNode();
            if (r.promptKey().startsWith("assistant.bio")) {
                ArrayNode arr = out.putArray("suggestions");
                int max = Facts.num(r.facts(), "maxWords", 90);
                for (String text : BioTemplates.generate(r.facts(), culture)) {
                    ObjectNode s = arr.addObject();
                    s.put("text", text);
                    s.putArray("highlights").add("region").add("work");
                    if (BioTemplates.wordCount(text) > max) s.put("text", text);   // validator downstream enforces the limit
                }
            } else if (r.promptKey().startsWith("chat.icebreaker")) {
                ArrayNode arr = out.putArray("suggestions");
                int n = Facts.num(r.facts(), "n", 3);
                IceBreakerTemplates.generate(r.facts(), culture).stream().limit(n).forEach(arr::add);
            } else if (r.promptKey().startsWith("compat.explain")) {
                var n = CompatNarrative.build(r.facts(), culture);
                out.put("summary", n.summary());
                n.strengths().forEach(out.putArray("strengths")::add);
                n.discussionPoints().forEach(out.putArray("discussionPoints")::add);
                out.put("communicationStyle", n.communicationStyle());
                out.put("disclaimer", n.disclaimer());
            } else {
                throw new IllegalArgumentException("fake provider has no generator for " + r.promptKey());
            }
            return mapper.writeValueAsString(out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public float[] embed(String text) {
        if (failing.get()) throw new IllegalStateException("fake provider outage");
        float[] v = new float[DIMS];
        for (String token : text.trim().split("\\s+")) {
            if (token.isBlank()) continue;
            String key = token.contains(":") ? token.substring(0, token.indexOf(':')) : "";
            double w = WEIGHTS.getOrDefault(key, 1.0);
            byte[] h = sha(token);
            int idx = Math.floorMod(((h[0] & 0xff) << 8) | (h[1] & 0xff), DIMS);
            v[idx] += (float) ((h[2] & 1) == 0 ? w : -w);
        }
        double norm = 0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < DIMS; i++) v[i] /= (float) norm;
        return v;
    }

    @Override public String embeddingVersion() { return VERSION; }

    private static byte[] sha(String s) {
        try { return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static List<String> tokensOf(String text) { return List.of(text.trim().split("\\s+")); }
}
