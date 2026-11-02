package in.pahadijodi.chat.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "pj.chat")
public record ChatProperties(
        @DefaultValue Policy policy,
        @DefaultValue Moderation moderation,
        @DefaultValue Rtc rtc,
        @DefaultValue Ws ws,
        @DefaultValue Call call,
        @DefaultValue Topics topics) {

    public record Policy(@DefaultValue("true") boolean requireMatch) {}

    public record Moderation(@DefaultValue({"idiot", "stupid", "bastard", "scumbag"}) List<String> bannedWords) {}

    public record Rtc(@DefaultValue("stun:stun.l.google.com:19302") List<String> stunUrls,
                      @DefaultValue List<String> turnUrls,
                      @DefaultValue("") String turnSecret,
                      @DefaultValue("3600") long turnTtlSeconds) {}

    public record Ws(@DefaultValue({"http://localhost:*", "http://127.0.0.1:*"}) List<String> allowedOrigins) {}

    public record Call(@DefaultValue("45") long ringTimeoutSeconds) {}

    public record Topics(@DefaultValue("pj.match.created.v1") String matchCreated) {}
}
