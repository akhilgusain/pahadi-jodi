package in.pahadijodi.analytics.mapping;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 pseudonyms for user ids. Deterministic (so funnels and "my insights" can join on it) but not reversible
 * without the key. Raw user ids never reach the fact table.
 */
@Component
public class Pseudonymizer {
    private static final Logger log = LoggerFactory.getLogger(Pseudonymizer.class);
    private static final String DEV_KEY = "dev-only-pseudonym-key-do-not-use-in-production";
    private final byte[] key;

    public Pseudonymizer(@Value("${pj.analytics.pseudonym-key:}") String key,
                         @Value("${pj.analytics.require-pseudonym-key:false}") boolean required) {
        if (key == null || key.isBlank()) {
            if (required) throw new IllegalStateException("PJ_ANALYTICS_PSEUDONYM_KEY is required (pj.analytics.require-pseudonym-key=true)");
            log.warn("PJ_ANALYTICS_PSEUDONYM_KEY not set: using a public dev key, pseudonyms are guessable (dev only)");
            this.key = DEV_KEY.getBytes(StandardCharsets.UTF_8);
        } else {
            this.key = key.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String hash(String userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(userId.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
