package in.pahadijodi.airec.llm;

import java.util.Map;

/**
 * @param facts structured facts behind the prompt. Real providers ignore them (the same data is already in {@code user});
 *              the deterministic fake generator reads them. They are PII-redacted exactly like the prompt text.
 */
public record LlmRequest(String promptKey, String system, String user, String tier, double temperature,
                         int maxTokens, boolean json, Map<String, Object> facts) {}
