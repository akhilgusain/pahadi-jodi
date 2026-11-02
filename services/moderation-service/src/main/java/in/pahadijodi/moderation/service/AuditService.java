package in.pahadijodi.moderation.service;

import in.pahadijodi.moderation.domain.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Tamper-evident decision log: every row commits to the previous one. */
@Service
public class AuditService {
    public record Verification(boolean intact, long entries, Long firstBrokenSeq) {}

    private final DecisionAuditRepository audit;
    private final AuditHeadRepository head;

    public AuditService(DecisionAuditRepository audit, AuditHeadRepository head) {
        this.audit = audit;
        this.head = head;
    }

    /** Must join the caller's transaction so the log entry commits atomically with the decision. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(UUID caseId, String actor, String action, String detail) {
        AuditHead h = head.lockHead();
        Instant at = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String hash = hash(h.getLastHash(), caseId, actor, action, detail, at);
        audit.save(new DecisionAudit(caseId, actor, action, truncate(detail, 1000), at, h.getLastHash(), hash));
        h.setLastHash(hash);
    }

    @Transactional(readOnly = true)
    public Verification verify() {
        String prev = "GENESIS";
        long n = 0;
        for (DecisionAudit a : audit.findAllByOrderBySeqAsc()) {
            n++;
            String expected = hash(prev, a.getCaseId(), a.getActor(), a.getAction(), a.getDetail(), a.getCreatedAt());
            if (!a.getPrevHash().equals(prev) || !a.getHash().equals(expected)) return new Verification(false, n, a.getSeq());
            prev = a.getHash();
        }
        return new Verification(true, n, null);
    }

    @Transactional(readOnly = true)
    public List<DecisionAudit> forCase(UUID caseId) {
        return audit.findByCaseIdOrderBySeqAsc(caseId);
    }

    private static String hash(String prev, UUID caseId, String actor, String action, String detail, Instant at) {
        try {
            String s = prev + "|" + caseId + "|" + actor + "|" + action + "|" + truncate(detail, 1000) + "|" + at.toEpochMilli();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s, int n) {
        return s == null ? null : s.length() <= n ? s : s.substring(0, n);
    }
}
