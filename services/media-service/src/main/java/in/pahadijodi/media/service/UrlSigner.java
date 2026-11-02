package in.pahadijodi.media.service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Expiring, tamper-proof download URLs (HMAC-SHA256). The CDN equivalent is a CloudFront signed URL. */
@Component
public class UrlSigner {
    private static final Logger log = LoggerFactory.getLogger(UrlSigner.class);
    private final byte[] secret;
    private final String baseUrl;

    public UrlSigner(@Value("${pj.media.signing-secret}") String secret,
                     @Value("${pj.media.public-base-url:}") String baseUrl) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.baseUrl = baseUrl;
        if ("dev-only-change-me".equals(secret)) log.warn("PJ_MEDIA_SIGNING_SECRET not set: using the dev-only default");
    }

    public String url(UUID id, String variant, Instant exp) {
        long e = exp.getEpochSecond();
        return baseUrl + "/api/v1/media/files/" + id + "?v=" + variant + "&exp=" + e + "&sig=" + sig(id, variant, e);
    }

    public boolean verify(UUID id, String variant, long exp, String sig) {
        if (sig == null || Instant.now().getEpochSecond() > exp) return false;
        return MessageDigest.isEqual(sig(id, variant, exp).getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8));
    }

    private String sig(UUID id, String variant, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((id + "|" + variant + "|" + exp).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
