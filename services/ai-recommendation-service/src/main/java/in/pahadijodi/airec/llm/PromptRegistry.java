package in.pahadijodi.airec.llm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Versioned prompt templates loaded from classpath:prompts/{key}.txt ("SYSTEM:" ... "USER:" ...). {{name}} placeholders. */
@Component
public class PromptRegistry {
    public record Prompt(String key, String system, String user, String tier, double temperature, int maxTokens) {}

    private static final Map<String, Object[]> META = Map.of(
            "compat.explain.v3", new Object[]{"LARGE", 0.3, 700},
            "assistant.bio.v2", new Object[]{"SMALL", 0.7, 600},
            "chat.icebreaker.v2", new Object[]{"SMALL", 0.8, 250});

    private final Map<String, Prompt> cache = new ConcurrentHashMap<>();

    public Prompt get(String key) { return cache.computeIfAbsent(key, this::load); }

    public LlmRequest render(String key, Map<String, String> vars, Map<String, Object> facts) {
        Prompt p = get(key);
        return new LlmRequest(key, fill(p.system(), vars), fill(p.user(), vars), p.tier(), p.temperature(), p.maxTokens(), true, facts);
    }

    private static String fill(String t, Map<String, String> vars) {
        String out = t;
        for (var e : vars.entrySet()) out = out.replace("{{" + e.getKey() + "}}", e.getValue());
        return out;
    }

    private Prompt load(String key) {
        Object[] m = META.get(key);
        if (m == null) throw new IllegalArgumentException("Unknown prompt " + key);
        try (InputStream in = getClass().getResourceAsStream("/prompts/" + key + ".txt")) {
            if (in == null) throw new IllegalStateException("Missing prompt resource " + key);
            String all = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int u = all.indexOf("\nUSER:");
            String system = all.substring(all.indexOf("SYSTEM:") + 7, u).strip();
            String user = all.substring(u + 6).strip();
            return new Prompt(key, system, user, (String) m[0], (Double) m[1], (Integer) m[2]);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
