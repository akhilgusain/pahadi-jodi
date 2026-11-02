package in.pahadijodi.identity.service;

import in.pahadijodi.common.outbox.OutboxService;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.identity.domain.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    public record Tokens(String accessToken, String refreshToken, long expiresIn, String tokenType) {}

    private final AccountRepository accounts;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final OutboxService outbox;
    private final long refreshTtlDays;
    private final java.util.Set<String> adminEmails;
    /** Used to equalise timing when the email is unknown (avoid user enumeration). */
    private final String dummyHash;

    public AuthService(AccountRepository accounts, RefreshTokenRepository refreshTokens,
                       PasswordEncoder encoder, TokenService tokens, OutboxService outbox,
                       @Value("${pj.jwt.refresh-ttl-days}") long refreshTtlDays,
                       @Value("${pj.admin-emails:}") String adminEmails) {
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.tokens = tokens;
        this.outbox = outbox;
        this.refreshTtlDays = refreshTtlDays;
        this.adminEmails = java.util.Arrays.stream(adminEmails.split(",")).map(String::trim).map(String::toLowerCase)
                .filter(x -> !x.isEmpty()).collect(java.util.stream.Collectors.toSet());
        this.dummyHash = encoder.encode("timing-equaliser");
    }

    @Transactional
    public Tokens register(String email, String password) {
        String e = email.trim().toLowerCase();
        if (accounts.findByEmail(e).isPresent()) throw conflict();
        Account a;
        try {
            a = accounts.saveAndFlush(new Account(e, encoder.encode(password)));
        } catch (DataIntegrityViolationException ex) {
            throw conflict();
        }
        outbox.publish("pj.identity.user-registered.v1", "UserRegistered", a.getId().toString(),
                Map.of("userId", a.getId().toString()));   // no PII in events
        return issue(a.getId(), a.getEmail(), UUID.randomUUID());
    }

    @Transactional
    public Tokens login(String email, String password) {
        var a = accounts.findByEmail(email.trim().toLowerCase()).orElse(null);
        boolean ok = encoder.matches(password, a != null ? a.getPasswordHash() : dummyHash);
        if (a == null || !ok || !a.isActive()) {
            throw new PjException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        return issue(a.getId(), a.getEmail(), UUID.randomUUID());
    }

    /** Rotation with reuse detection: presenting a used token revokes the whole family. */
    @Transactional(noRollbackFor = PjException.class)
    public Tokens refresh(String presented) {
        var t = refreshTokens.findByTokenHash(TokenService.hash(presented))
                .orElseThrow(() -> new PjException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Invalid refresh token"));
        if (t.isUsed() || t.isRevoked()) {
            refreshTokens.revokeFamily(t.getFamilyId(), Instant.now());
            throw new PjException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED", "Session revoked; sign in again");
        }
        if (t.isExpired()) {
            throw new PjException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token expired");
        }
        t.markUsed();
        String email = accounts.findById(t.getAccountId()).map(Account::getEmail).orElse("");
        return issue(t.getAccountId(), email, t.getFamilyId());
    }

    private Tokens issue(UUID accountId, String email, UUID family) {
        String refresh = tokens.newRefreshToken();
        refreshTokens.save(new RefreshToken(accountId, family, TokenService.hash(refresh),
                Instant.now().plus(Duration.ofDays(refreshTtlDays))));
        return new Tokens(tokens.accessToken(accountId, adminEmails.contains(email) ? List.of("USER", "ADMIN") : List.of("USER")), refresh,
                tokens.accessTtlSeconds(), "Bearer");
    }

    private static PjException conflict() {
        return new PjException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email already registered");
    }
}
