package in.pahadijodi.identity.service;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private static final SecureRandom RNG = new SecureRandom();
    private final JwtEncoder encoder;
    private final RSAKey key;
    private final String issuer;
    private final long accessTtl;

    public TokenService(JwtEncoder encoder, RSAKey key,
                        @Value("${pj.jwt.issuer}") String issuer,
                        @Value("${pj.jwt.access-ttl-seconds}") long accessTtl) {
        this.encoder = encoder;
        this.key = key;
        this.issuer = issuer;
        this.accessTtl = accessTtl;
    }

    public String accessToken(UUID accountId, List<String> roles) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(issuer).subject(accountId.toString())
                .issuedAt(now).expiresAt(now.plus(Duration.ofSeconds(accessTtl)))
                .id(UUID.randomUUID().toString())
                .claim("roles", roles)
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTtlSeconds() { return accessTtl; }

    /** Opaque 256-bit refresh token; only its SHA-256 is stored. */
    public String newRefreshToken() {
        byte[] b = new byte[32];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Map<String, Object> publicJwks() {
        return new JWKSet(key.toPublicJWK()).toJSONObject();
    }
}
