package in.pahadijodi.moderation.service;

import in.pahadijodi.common.outbox.OutboxService;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.moderation.domain.*;
import in.pahadijodi.moderation.domain.Enums.*;
import in.pahadijodi.moderation.port.ContentClassifier;
import in.pahadijodi.moderation.port.ContentClassifier.Label;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationService {
    public record Verdict(Decision decision, List<Label> labels, double score, UUID caseId) {}

    static final Set<String> SEVERE = Set.of("SCAM", "SEXUAL_CONTENT", "HARASSMENT");
    private static final Pattern DIGITS = Pattern.compile("\\d{6,}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");

    private final ContentClassifier classifier;
    private final ModerationCaseRepository cases;
    private final SanctionService sanctions;
    private final AuditService audit;
    private final OutboxService outbox;
    private final double flagAt, blockAt;

    public ModerationService(ContentClassifier classifier, ModerationCaseRepository cases, SanctionService sanctions,
                             AuditService audit, OutboxService outbox,
                             @Value("${pj.moderation.thresholds.flag}") double flagAt,
                             @Value("${pj.moderation.thresholds.block}") double blockAt) {
        this.classifier = classifier;
        this.cases = cases;
        this.sanctions = sanctions;
        this.audit = audit;
        this.outbox = outbox;
        this.flagAt = flagAt;
        this.blockAt = blockAt;
    }

    // ---- automated pipeline -------------------------------------------------------------------------

    @Transactional
    public Verdict check(UUID subject, ContentType type, String text, String refId, String source) {
        List<Label> labels = type == ContentType.IMAGE ? classifier.classifyImage(refId) : classifier.classifyText(text);
        double max = labels.stream().mapToDouble(Label::score).max().orElse(0);
        Decision d = max >= blockAt ? Decision.BLOCK : max >= flagAt ? Decision.FLAG : Decision.ALLOW;
        if (d == Decision.ALLOW) return new Verdict(d, labels, max, null);

        String names = labels.stream().filter(l -> l.score() >= flagAt).map(Label::name).collect(Collectors.joining(","));
        ModerationCase c = cases.save(new ModerationCase(type, refId == null ? "n/a" : refId, subject, source, d, names, max,
                priorityOf(d, names), type == ContentType.IMAGE ? null : redact(text)));
        audit.append(c.getId(), "system", "AUTO_" + d, "labels=" + names + " score=" + max);
        publishDecision(c, d.name(), "AUTO");
        return new Verdict(d, labels, max, c.getId());
    }

    /** Used by user reports: a case without classifier input. */
    @Transactional
    public ModerationCase openCase(ContentType type, String refId, UUID subject, String source, String labels, int priority, String excerpt) {
        ModerationCase c = cases.save(new ModerationCase(type, refId, subject, source, Decision.FLAG, labels, 0.5, priority, excerpt));
        audit.append(c.getId(), "system", "CASE_OPENED", "source=" + source + " labels=" + labels);
        return c;
    }

    static int priorityOf(Decision d, String labelNames) {
        if (d == Decision.BLOCK) return 1;
        for (String l : labelNames.split(",")) if (SEVERE.contains(l)) return 2;
        return 3;
    }

    // ---- human review -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ModerationCase> queue(Collection<CaseStatus> statuses, int page, int size) {
        return cases.findByStatusInOrderByPriorityAscDueAtAsc(statuses, PageRequest.of(page, Math.min(size, 100)));
    }

    @Transactional(readOnly = true)
    public ModerationCase get(UUID id) {
        return cases.findById(id).orElseThrow(ModerationService::notFound);
    }

    @Transactional
    public ModerationCase assign(UUID id, UUID moderator, boolean isAdmin) {
        ModerationCase c = get(id);
        guardStage(c, isAdmin);
        c.assign(moderator);
        audit.append(id, moderator.toString(), "ASSIGNED", null);
        return c;
    }

    @Transactional
    public ModerationCase decide(UUID id, UUID actor, boolean isAdmin, Resolution r, String note) {
        ModerationCase c = get(id);
        guardStage(c, isAdmin);
        switch (r) {
            case ESCALATE -> {
                c.escalate(actor, note);
                audit.append(id, actor.toString(), "ESCALATED", note);
                publishDecision(c, "ESCALATED", "HUMAN");
            }
            case APPROVE -> {
                c.resolve(r, actor, note);
                audit.append(id, actor.toString(), "APPROVED", note);
                publishDecision(c, "APPROVED", "HUMAN");
            }
            case REJECT -> {
                c.resolve(r, actor, note);
                audit.append(id, actor.toString(), "REJECTED", note);
                if (c.getSubjectUserId() != null) {
                    sanctions.addStrike(c.getSubjectUserId(), id, pointsFor(c.getLabels()), c.getLabels());
                }
                publishDecision(c, "REJECTED", "HUMAN");
            }
        }
        return c;
    }

    private void guardStage(ModerationCase c, boolean isAdmin) {
        if (c.isFinal()) throw new PjException(HttpStatus.CONFLICT, "CASE_CLOSED", "Case already resolved");
        if (c.getStatus() == CaseStatus.ESCALATED && !isAdmin) {
            throw new PjException(HttpStatus.FORBIDDEN, "ESCALATED_TO_ADMIN", "Escalated cases need an admin");
        }
    }

    static int pointsFor(String labels) {
        for (String l : labels.split(",")) if (SEVERE.contains(l)) return 2;
        return 1;
    }

    // ---- helpers ------------------------------------------------------------------------------------

    void publishDecision(ModerationCase c, String outcome, String decidedBy) {
        Map<String, Object> p = new HashMap<>();
        p.put("caseId", c.getId().toString());
        p.put("contentType", c.getContentType().name());
        p.put("refId", c.getRefId());
        p.put("subjectUserId", c.getSubjectUserId() == null ? null : c.getSubjectUserId().toString());
        p.put("outcome", outcome);              // BLOCK | FLAG (auto) ; APPROVED | REJECTED | ESCALATED (human)
        p.put("labels", c.getLabels());
        p.put("decidedBy", decidedBy);
        p.put("decidedAt", Instant.now().toString());
        outbox.publish("pj.moderation.decision.v1", "ModerationDecision", c.getId().toString(), p);
    }

    /** Reviewers see the gist without phone numbers or e-mail addresses. */
    static String redact(String text) {
        if (text == null) return null;
        String t = EMAIL.matcher(text).replaceAll("***@***");
        t = DIGITS.matcher(t.replaceAll("(?<=\\d)[\\s-](?=\\d)", "")).replaceAll("***");
        return t.length() <= 280 ? t : t.substring(0, 280);
    }

    private static PjException notFound() {
        return new PjException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Case not found");
    }
}
