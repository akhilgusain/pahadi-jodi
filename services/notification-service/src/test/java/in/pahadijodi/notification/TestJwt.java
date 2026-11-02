package in.pahadijodi.notification;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Test-only RSA key: signs real JWTs and supplies a JwtDecoder that trusts them (no JWKS endpoint needed). */
public final class TestJwt {
    private static final RSAKey KEY;
    static {
        try { KEY = new RSAKeyGenerator(2048).keyID("test").generate(); } catch (JOSEException e) { throw new IllegalStateException(e); }
    }
    private TestJwt() {}

    public static String token(UUID subject) { return token(subject, 600); }

    public static String token(UUID subject, long ttlSeconds) {
        try {
            var claims = new JWTClaimsSet.Builder().subject(subject.toString()).issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + ttlSeconds * 1000)).claim("roles", List.of("USER")).build();
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").build(), claims);
            jwt.sign(new RSASSASigner(KEY));
            return jwt.serialize();
        } catch (JOSEException e) { throw new IllegalStateException(e); }
    }

    @TestConfiguration
    public static class Config {
        @Bean
        JwtDecoder jwtDecoder() throws JOSEException { return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build(); }
    }
}
