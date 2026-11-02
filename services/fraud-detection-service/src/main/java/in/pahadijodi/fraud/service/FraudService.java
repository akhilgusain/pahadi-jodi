package in.pahadijodi.fraud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.common.outbox.OutboxService;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.fraud.domain.*;
import in.pahadijodi.fraud.domain.Repositories.*;
import in.pahadijodi.fraud.engine.*;
import in.pahadijodi.fraud.engine.RiskEngine.Reason;
import in.pahadijodi.fraud.engine.RiskEngine.RuleConfig;
import in.pahadijodi.fraud.text.DisposableEmails;
import in.pahadijodi.fraud.text.ScamTextAnalyzer;
import in.pahadijodi.fraud.text.Simhash;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FraudService {

    /** Raw input for one signal; every field except userId and type is optional. */
    public record SignalCommand(UUID userId, FraudSignal.Type type, String deviceHash, String ip, String userAgent,
                                String emailDomain, String phone, UUID counterpartId, String chatText,
                                String profileText, Long photoHash, String trigger) {}

    public record Assessment(UUID evaluationId, UUID userId, int score, Action action, List<Reason> reasons) {}

    private final SignalRepository signals;
    private final RuleRepository rules;
    private final EvaluationRepository evaluations;
    private final UserRiskRepository userRisks;
    private final CaseRepository cases;
    private final ProfileFingerprintRepository fingerprints;
    private final PhotoDuplicateDetector photos;
    private final RiskEngine engine;
    private final OutboxService outbox;
    private final ObjectMapper json;
    private final int textMaxHamming;

    public FraudService(SignalRepository signals, RuleRepository rules, EvaluationRepository evaluations,
                        UserRiskRepository userRisks, CaseRepository cases, ProfileFingerprintRepository fingerprints,
                        PhotoDuplicateDetector photos, RiskEngine engine, OutboxService outbox, ObjectMapper json,
                        @Value("${pj.fraud.text.max-hamming:3}") int textMaxHamming) {
        this.signals = signals; this.rules = rules; this.evaluations = evaluations; this.userRisks = userRisks;
        this.cases = cases; this.fingerprints = fingerprints; this.photos = photos; this.engine = engine;
        this.outbox = outbox; this.json = json; this.textMaxHamming = textMaxHamming;
    }

    // ---- ingestion ---------------------------------------------------------------------------------------------

    @Transactional
    public Assessment ingest(SignalCommand c) {
        boolean disposable = DisposableEmails.isDisposable(c.emailDomain());
        var s = new FraudSignal(c.userId(), c.type())
                .device(c.deviceHash(), c.ip(), trim(c.userAgent(), 300))
                .email(c.emailDomain(), disposable)
                .phone(c.phone() == null ? null : sha256(c.phone().replaceAll("\\D", "")))   // never store the raw number
                .chat(c.counterpartId(), c.chatText() == null ? 0 : ScamTextAnalyzer.hits(c.chatText()));
        signals.save(s);

        if (c.profileText() != null && Simhash.tokenCount(c.profileText()) >= 8) {
            var fp = fingerprints.findById(c.userId()).orElseGet(() -> new ProfileFingerprint(c.userId()));
            fp.set(sha256(Simhash.normalize(c.profileText())), Simhash.hash(c.profileText()));
            fingerprints.save(fp);
        }
        if (c.photoHash() != null) photos.register(c.userId(), c.photoHash());
        return evaluate(c.userId(), c.trigger() != null ? c.trigger() : c.type().name());
    }

    // ---- evaluation --------------------------------------------------------------------------------------------

    @Transactional
    public Assessment evaluate(UUID userId, String trigger) {
        var result = engine.evaluate(buildFacts(userId), ruleConfig());
        String reasonsJson = toJson(result.reasons());
        var eval = evaluations.save(new RiskEvaluation(userId, result.score(), result.action().name(), reasonsJson, trigger));

        UserRisk risk = userRisks.findById(userId).orElse(null);
        boolean first = risk == null;
        int prevScore = first ? -1 : risk.getScore();
        Action prevEffective = first ? Action.ALLOW : risk.effectiveAction();
        if (first) risk = new UserRisk(userId);
        risk.apply(result.score(), result.action());
        userRisks.save(risk);

        FraudCase kase = null;
        if (result.action() != Action.ALLOW && risk.getManualAction() == null) {
            kase = cases.findFirstByUserIdAndStatus(userId, FraudCase.Status.OPEN).orElse(null);
            if (kase == null) kase = new FraudCase(userId, result.score(), result.action());
            else kase.update(result.score(), result.action());
            cases.save(kase);
        }

        Action effective = risk.effectiveAction();
        if (first || prevScore != result.score() || prevEffective != effective) {
            outbox.publish("pj.fraud.risk-scored.v1", "RiskScored", userId.toString(), Map.of(
                    "userId", userId.toString(), "evaluationId", eval.getId().toString(), "score", result.score(),
                    "action", effective.name(),
                    "reasons", result.reasons().stream().map(Reason::code).toList()));   // codes only, no raw signals
        }
        if (effective != prevEffective) {
            publishAction(userId, effective, kase != null ? kase.getId() : null, "AUTOMATIC_SCORE");
        }
        return new Assessment(eval.getId(), userId, result.score(), result.action(), result.reasons());
    }

    Facts buildFacts(UUID userId) {
        Instant now = Instant.now();
        var ctx = signals.latestContext(userId, PageRequest.of(0, 1));
        String ip = ctx.isEmpty() ? null : ctx.get(0).getIp();
        String device = ctx.isEmpty() ? null : ctx.get(0).getDeviceHash();
        int byIp = ip == null ? 0 : signals.distinctUsersByIp(ip, now.minus(Duration.ofHours(1)));
        int byDevice = device == null ? 0 : signals.distinctUsersByDevice(device, now.minus(Duration.ofDays(30)));
        return new Facts(byIp, byDevice, signals.disposableCount(userId) > 0, duplicateText(userId), photos.hasDuplicate(userId),
                (int) signals.scamHits(userId, now.minus(Duration.ofHours(24))),
                signals.distinctRecipients(userId, now.minus(Duration.ofHours(1))),
                signals.countByType(userId, FraudSignal.Type.PAYMENT_FAILED, now.minus(Duration.ofHours(24))),
                signals.countByType(userId, FraudSignal.Type.CHARGEBACK, Instant.EPOCH));
    }

    private boolean duplicateText(UUID userId) {
        var mine = fingerprints.findById(userId).orElse(null);
        if (mine == null) return false;
        return fingerprints.candidates(userId, mine.getTextHash(), mine.getBand0(), mine.getBand1(), mine.getBand2(), mine.getBand3())
                .stream().anyMatch(o -> o.getTextHash().equals(mine.getTextHash())
                        || Simhash.hamming(mine.getSimhash(), o.getSimhash()) <= textMaxHamming);
    }

    private Map<RuleCode, RuleConfig> ruleConfig() {
        Map<RuleCode, RuleConfig> m = new EnumMap<>(RuleCode.class);
        for (FraudRule r : rules.findAll()) {
            try { m.put(RuleCode.valueOf(r.getCode()), new RuleConfig(r.isEnabled(), r.getWeight())); }
            catch (IllegalArgumentException ignored) { /* rule row for a code this build does not know */ }
        }
        return m;
    }

    // ---- analyst workflow --------------------------------------------------------------------------------------

    @Transactional
    public FraudCase resolve(UUID caseId, FraudCase.Label label, String notes, UUID analyst) {
        FraudCase kase = cases.findById(caseId)
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Case not found"));
        if (kase.getStatus() != FraudCase.Status.OPEN) {
            throw new PjException(HttpStatus.CONFLICT, "CASE_ALREADY_RESOLVED", "Case already resolved");
        }
        kase.resolve(label, notes, analyst);   // the label is stored as training feedback
        UserRisk risk = userRisks.findById(kase.getUserId()).orElseGet(() -> new UserRisk(kase.getUserId()));
        Action before = risk.effectiveAction();
        risk.override(label == FraudCase.Label.TRUE_POSITIVE ? Action.BLOCK : Action.ALLOW);
        userRisks.save(risk);
        if (risk.effectiveAction() != before) {
            publishAction(kase.getUserId(), risk.effectiveAction(), kase.getId(), "ANALYST_" + label.name());
        }
        return kase;
    }

    @Transactional
    public FraudRule updateRule(String code, boolean enabled, int weight, String by) {
        FraudRule r = rules.findById(code).orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "Unknown rule"));
        r.update(enabled, weight, by);
        return rules.save(r);
    }

    private void publishAction(UUID userId, Action action, UUID caseId, String reason) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("userId", userId.toString());
        p.put("action", action.name());
        p.put("reason", reason);
        if (caseId != null) p.put("caseId", caseId.toString());
        outbox.publish("pj.fraud.action.v1", "FraudActionRequested", userId.toString(), p);
    }

    public List<Reason> parseReasons(String reasonsJson) {
        try { return json.readValue(reasonsJson, new TypeReference<List<Reason>>() {}); }
        catch (JsonProcessingException e) { return List.of(); }
    }

    private String toJson(Object o) {
        try { return json.writeValueAsString(o); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    static String sha256(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String trim(String s, int max) { return s == null || s.length() <= max ? s : s.substring(0, max); }
}
