package in.pahadijodi.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import in.pahadijodi.moderation.adapter.RuleBasedClassifier;
import in.pahadijodi.moderation.port.ContentClassifier.Label;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleBasedClassifierTest {
    private final RuleBasedClassifier c = new RuleBasedClassifier(0.0);

    private double score(List<Label> labels, String name) {
        return labels.stream().filter(l -> l.name().equals(name)).mapToDouble(Label::score).findFirst().orElse(-1);
    }

    @Test
    void cleanTextHasNoLabels() {
        assertThat(c.classifyText("Hello! I work as an engineer in Dehradun. Love Garhwali songs.")).isEmpty();
        assertThat(c.classifyText("")).isEmpty();
        assertThat(c.classifyText(null)).isEmpty();
    }

    @Test
    void toxicityUsesWordBoundaries() {
        assertThat(score(c.classifyText("you are a bastard"), "TOXICITY")).isEqualTo(0.90);
        assertThat(score(c.classifyText("you are an idiot"), "TOXICITY")).isEqualTo(0.60);
        assertThat(c.classifyText("a foolproof plan for a grapefruit")).isEmpty();   // no substring matches
    }

    @Test
    void contactSharingScamSexualAndLinks() {
        assertThat(score(c.classifyText("call me on +91 98765 43210"), "CONTACT_SHARING")).isEqualTo(0.70);
        assertThat(score(c.classifyText("mail me at someone@example.com"), "CONTACT_SHARING")).isEqualTo(0.70);
        assertThat(score(c.classifyText("send money by Western Union"), "SCAM")).isEqualTo(0.80);
        assertThat(score(c.classifyText("send nudes"), "SEXUAL_CONTENT")).isEqualTo(0.85);
        assertThat(score(c.classifyText("see https://example.com/me"), "LINK")).isEqualTo(0.45);
        assertThat(c.classifyText("year 2024 age 29 height 170")).isEmpty();           // short digit groups are fine
    }

    @Test
    void spamPatterns() {
        assertThat(score(c.classifyText("heeeeeeeeeello"), "SPAM")).isEqualTo(0.60);
        assertThat(score(c.classifyText("buy buy buy buy buy now"), "SPAM")).isEqualTo(0.60);
        assertThat(score(c.classifyText("PLEASE ANSWER ME RIGHT NOW I AM WAITING"), "SPAM")).isEqualTo(0.60);
    }

    @Test
    void labelsAreSortedByScore() {
        var labels = c.classifyText("you bastard, send money to me@example.com");
        assertThat(labels).extracting(Label::name).containsExactly("TOXICITY", "SCAM", "CONTACT_SHARING");
    }

    @Test
    void imageSamplingIsDeterministicAndRateControlled() {
        var all = new RuleBasedClassifier(1.0);
        assertThat(all.classifyImage("media-1")).extracting(Label::name).containsExactly("SAMPLE_REVIEW");
        assertThat(all.classifyImage("media-1")).isEqualTo(all.classifyImage("media-1"));
        assertThat(c.classifyImage("media-1")).isEmpty();                                // rate 0

        var tenth = new RuleBasedClassifier(0.1);
        long hits = java.util.stream.IntStream.range(0, 2000).filter(i -> !tenth.classifyImage("m" + i).isEmpty()).count();
        assertThat(hits).isBetween(120L, 280L);                                          // ~10%
    }
}
