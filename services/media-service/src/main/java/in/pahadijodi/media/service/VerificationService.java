package in.pahadijodi.media.service;

import in.pahadijodi.common.outbox.OutboxService;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.media.domain.*;
import in.pahadijodi.media.domain.UserVerification.Level;
import in.pahadijodi.media.domain.VerificationCheck.Status;
import in.pahadijodi.media.domain.VerificationCheck.Type;
import in.pahadijodi.media.port.FaceVerifier;
import in.pahadijodi.media.port.KycPort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verification state machine. Each check (PAN | AADHAAR | SELFIE) moves
 * PENDING -> VERIFIED | MANUAL_REVIEW | REJECTED (MANUAL_REVIEW -> VERIFIED | REJECTED by a human).
 * Level: NONE -> ID_VERIFIED (PAN or Aadhaar) -> BLUE_TICK (ID + selfie).
 */
@Service
public class VerificationService {
    public record CheckView(UUID id, String type, String status, String reason, Double score, String maskedId, int attempts, Instant updatedAt) {}
    public record StatusView(String level, String state, List<CheckView> checks) {}

    private final VerificationCheckRepository checks;
    private final UserVerificationRepository levels;
    private final MediaAssetRepository assets;
    private final MediaService media;
    private final KycPort kyc;
    private final FaceVerifier faces;
    private final OutboxService outbox;
    private final byte[] pepper;
    private final double livenessMin, matchPass, matchReview;
    private final int maxAttempts;

    public VerificationService(VerificationCheckRepository checks, UserVerificationRepository levels,
                               MediaAssetRepository assets, MediaService media, KycPort kyc, FaceVerifier faces,
                               OutboxService outbox,
                               @Value("${pj.kyc.hash-pepper}") String pepper,
                               @Value("${pj.verification.liveness-min}") double livenessMin,
                               @Value("${pj.verification.match-pass}") double matchPass,
                               @Value("${pj.verification.match-review}") double matchReview,
                               @Value("${pj.verification.max-attempts}") int maxAttempts) {
        this.checks = checks;
        this.levels = levels;
        this.assets = assets;
        this.media = media;
        this.kyc = kyc;
        this.faces = faces;
        this.outbox = outbox;
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
        this.livenessMin = livenessMin;
        this.matchPass = matchPass;
        this.matchReview = matchReview;
        this.maxAttempts = maxAttempts;
    }

    @Transactional(readOnly = true)
    public StatusView status(UUID userId) {
        return view(userId, checks.findByUserId(userId));
    }

    @Transactional
    public StatusView submitPan(UUID userId, String pan, String fullName) {
        VerificationCheck row = startAttempt(userId, Type.PAN);
        var r = kyc.verifyPan(pan, fullName);
        if (!r.valid()) {
            row.set(Status.REJECTED, r.reason(), null);
        } else {
            String hash = hmac(r.normalizedPan());
            if (checks.existsByTypeAndIdHashAndUserIdNot(Type.PAN, hash, userId)) {
                row.set(Status.REJECTED, "DUPLICATE_ID", null);   // same PAN already used by another account
            } else {
                row.setIdentity(hash, last(r.normalizedPan(), 4));
                row.set(Status.VERIFIED, null, null);
            }
        }
        return afterChange(userId, row);
    }

    @Transactional
    public KycPort.AadhaarStart startAadhaar(UUID userId) {
        VerificationCheck row = startAttempt(userId, Type.AADHAAR);
        var start = kyc.startAadhaar(userId);
        row.setProviderRef(start.reference());
        row.set(Status.PENDING, "AWAITING_CONSENT", null);
        checks.save(row);
        return start;
    }

    @Transactional
    public StatusView completeAadhaar(UUID userId, String reference, String code) {
        VerificationCheck row = checks.findByUserIdAndType(userId, Type.AADHAAR)
                .filter(c -> c.getStatus() == Status.PENDING && reference.equals(c.getProviderRef()))
                .orElseThrow(() -> new PjException(HttpStatus.CONFLICT, "NO_PENDING_AADHAAR", "No pending Aadhaar verification for this reference"));
        var r = kyc.completeAadhaar(reference, code);
        if (!r.success()) {
            row.set(Status.REJECTED, r.reason(), null);
        } else {
            String hash = hmac(r.uidToken());
            if (checks.existsByTypeAndIdHashAndUserIdNot(Type.AADHAAR, hash, userId)) {
                row.set(Status.REJECTED, "DUPLICATE_ID", null);
            } else {
                row.setIdentity(hash, r.last4());   // the Aadhaar number itself never reaches us
                row.set(Status.VERIFIED, null, null);
            }
        }
        return afterChange(userId, row);
    }

    @Transactional
    public StatusView verifySelfie(UUID userId, UUID selfieMediaId) {
        MediaAsset selfie = assets.findById(selfieMediaId)
                .filter(a -> a.getUserId().equals(userId) && a.getKind() == MediaKind.SELFIE && a.getStatus() == MediaStatus.READY)
                .orElseThrow(() -> new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "SELFIE_NOT_READY", "Upload a SELFIE and complete it first"));
        MediaAsset primary = assets.findFirstByUserIdAndKindAndPrimaryPhotoTrueAndStatus(userId, MediaKind.PHOTO, MediaStatus.READY)
                .orElseThrow(() -> new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_PROFILE_PHOTO", "Add a primary profile photo first"));
        VerificationCheck row = startAttempt(userId, Type.SELFIE);

        byte[] s = media.bytesOf(selfie);
        double live = faces.liveness(s);
        if (live < livenessMin) {
            row.set(Status.REJECTED, "LIVENESS_FAILED", live);
        } else {
            double sim = faces.match(s, media.bytesOf(primary));
            if (sim >= matchPass) row.set(Status.VERIFIED, null, sim);
            else if (sim >= matchReview) row.set(Status.MANUAL_REVIEW, "FACE_MATCH_UNCERTAIN", sim);
            else row.set(Status.REJECTED, "FACE_MISMATCH", sim);
        }
        return afterChange(userId, row);
    }

    // ---- manual review (ADMIN / VERIFIER) -------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CheckView> reviewQueue() {
        return checks.findByStatusOrderByUpdatedAtAsc(Status.MANUAL_REVIEW).stream().map(VerificationService::checkView).toList();
    }

    @Transactional
    public StatusView decide(UUID reviewer, UUID checkId, boolean approve, String reason) {
        VerificationCheck c = checks.findById(checkId)
                .filter(x -> x.getStatus() == Status.MANUAL_REVIEW)
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "REVIEW_ITEM_NOT_FOUND", "No such item awaiting review"));
        c.review(reviewer, approve ? Status.VERIFIED : Status.REJECTED, approve ? "APPROVED_MANUALLY" : (reason == null ? "REJECTED_MANUALLY" : reason));
        return afterChange(c.getUserId(), c);
    }

    // ---- internals --------------------------------------------------------------------------------

    private VerificationCheck startAttempt(UUID userId, Type type) {
        VerificationCheck row = checks.findByUserIdAndType(userId, type).orElseGet(() -> new VerificationCheck(userId, type));
        if (row.getStatus() == Status.VERIFIED) throw new PjException(HttpStatus.CONFLICT, "ALREADY_VERIFIED", type + " already verified");
        if (row.getStatus() == Status.MANUAL_REVIEW) throw new PjException(HttpStatus.CONFLICT, "UNDER_REVIEW", type + " is under manual review");
        if (row.getAttempts() >= maxAttempts) throw new PjException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", "Contact support to continue");
        row.attempt();
        return row;
    }

    private StatusView afterChange(UUID userId, VerificationCheck row) {
        checks.save(row);
        refreshLevel(userId);
        return view(userId, checks.findByUserId(userId));
    }

    private void refreshLevel(UUID userId) {
        List<VerificationCheck> rows = checks.findByUserId(userId);
        boolean id = rows.stream().anyMatch(c -> (c.getType() == Type.PAN || c.getType() == Type.AADHAAR) && c.getStatus() == Status.VERIFIED);
        boolean selfie = rows.stream().anyMatch(c -> c.getType() == Type.SELFIE && c.getStatus() == Status.VERIFIED);
        Level next = id && selfie ? Level.BLUE_TICK : id ? Level.ID_VERIFIED : Level.NONE;
        UserVerification uv = levels.findById(userId).orElseGet(() -> new UserVerification(userId, Level.NONE));
        if (uv.getLevel() != next) {
            uv.setLevel(next);
            levels.save(uv);
            if (next != Level.NONE) {
                outbox.publish("pj.profile.verified.v1", "ProfileVerified", userId.toString(),
                        Map.of("userId", userId.toString(), "level", next.name(), "verifiedAt", Instant.now().toString()));
            }
        }
    }

    private StatusView view(UUID userId, List<VerificationCheck> rows) {
        Level level = levels.findById(userId).map(UserVerification::getLevel).orElse(Level.NONE);
        boolean pending = rows.stream().anyMatch(c -> c.getStatus() == Status.PENDING || c.getStatus() == Status.MANUAL_REVIEW);
        boolean rejected = rows.stream().anyMatch(c -> c.getStatus() == Status.REJECTED);
        String state = rows.isEmpty() ? "NONE"
                : level == Level.BLUE_TICK ? "VERIFIED"
                : pending ? "PENDING"
                : rejected ? "REJECTED"
                : "PENDING";   // e.g. ID verified, selfie still to do
        return new StatusView(level.name(), state, rows.stream().map(VerificationService::checkView).toList());
    }

    private static CheckView checkView(VerificationCheck c) {
        String masked = c.getIdLast4() == null ? null : "XXXXXXXX" + c.getIdLast4();
        return new CheckView(c.getId(), c.getType().name(), c.getStatus().name(), c.getReason(), c.getScore(), masked, c.getAttempts(), c.getUpdatedAt());
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String last(String s, int n) {
        return s.substring(Math.max(0, s.length() - n));
    }
}
