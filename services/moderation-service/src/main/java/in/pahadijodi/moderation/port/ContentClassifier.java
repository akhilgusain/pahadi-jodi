package in.pahadijodi.moderation.port;

import java.util.List;

/** Content safety classifier. Fake = rules; production = LLM / Perspective / Rekognition Moderation adapters. */
public interface ContentClassifier {
    record Label(String name, double score) {}
    List<Label> classifyText(String text);
    List<Label> classifyImage(String refId);
}
