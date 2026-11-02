package in.pahadijodi.airec.llm;

/** Provider adapter (anti-corruption layer). Never called directly: always go through {@link Llm}, which redacts PII first. */
public interface LlmProvider {
    String chat(LlmRequest request);

    float[] embed(String text);

    /** Identifies the vector space; similarity is only computed between vectors with the same version. */
    String embeddingVersion();
}
