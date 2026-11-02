package in.pahadijodi.airec.service;

import in.pahadijodi.airec.domain.AiUsage;
import in.pahadijodi.airec.domain.AiUsageRepository;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Per-user daily limit on AI generation requests. {@link #limitFor} is the hook for plan-based limits (subscription-service). */
@Service
public class QuotaService {
    public record Usage(int used, int limit, LocalDate date) {}

    private final AiUsageRepository usage;
    private final int defaultLimit;

    public QuotaService(AiUsageRepository usage, @Value("${pj.ai.daily-quota}") int defaultLimit) {
        this.usage = usage; this.defaultLimit = defaultLimit;
    }

    /** TODO: look up the user's plan entitlement (FREE/PREMIUM/PLUS/VIP) instead of one global number. */
    protected int limitFor(UUID userId) { return defaultLimit; }

    public Usage consume(UUID userId) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        String id = userId + ":" + today;
        if (usage.increment(id) == 0) {
            try { usage.saveAndFlush(new AiUsage(userId, today)); }
            catch (DataIntegrityViolationException race) { usage.increment(id); }   // another request created today's row first
        }
        int used = usage.findById(id).map(AiUsage::getCalls).orElse(1);
        int limit = limitFor(userId);
        if (used > limit) {
            throw new PjException(HttpStatus.TOO_MANY_REQUESTS, "AI_QUOTA_EXCEEDED", "Daily AI limit of " + limit + " reached; try again tomorrow");
        }
        return new Usage(used, limit, today);
    }

    public Usage current(UUID userId) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        int used = usage.findById(userId + ":" + today).map(AiUsage::getCalls).orElse(0);
        return new Usage(Math.min(used, limitFor(userId)), limitFor(userId), today);
    }
}
