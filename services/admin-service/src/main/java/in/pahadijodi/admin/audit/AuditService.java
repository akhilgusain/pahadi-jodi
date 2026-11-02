package in.pahadijodi.admin.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.admin.audit.AuditRepositories.AuditHeadRepository;
import in.pahadijodi.admin.audit.AuditRepositories.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditLogRepository logs;
    private final AuditHeadRepository heads;
    private final ObjectMapper json;

    public AuditService(AuditLogRepository logs, AuditHeadRepository heads, ObjectMapper json) {
        this.logs = logs; this.heads = heads; this.json = json;
    }

    /** Joins the caller's transaction so the audit row commits atomically with the action it records. */
    @Transactional
    public AuditLog append(String actorId, String actorRoles, String action, String targetType, String targetId, Map<String, ?> details) {
        AuditHead head = heads.lockHead();   // serialises concurrent appenders
        String detailsJson;
        try { detailsJson = json.writeValueAsString(details == null ? Map.of() : details); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        AuditLog row = AuditLog.next(head.getLastSeq(), head.getLastHash(), actorId, actorRoles, action, targetType, targetId, detailsJson, Instant.now());
        logs.save(row);
        head.advance(row.getSeq(), row.getHash());
        return row;
    }

    @Transactional(readOnly = true)
    public AuditChain.Result verify() {
        var verifier = new AuditChain.Verifier();
        long cursor = 0;
        while (true) {
            List<AuditLog> page = logs.findBySeqGreaterThanOrderBySeqAsc(cursor, PageRequest.of(0, 500));
            if (page.isEmpty()) break;
            for (AuditLog row : page) {
                if (!verifier.accept(row)) return verifier.finish(0, "");   // returns the recorded failure
                cursor = row.getSeq();
            }
        }
        AuditHead head = heads.findById(1).orElseThrow();
        return verifier.finish(head.getLastSeq(), head.getLastHash());
    }

    @Transactional(readOnly = true)
    public List<AuditLog> latest(int size) {
        return logs.findAllByOrderBySeqDesc(PageRequest.of(0, Math.min(Math.max(size, 1), 200)));
    }
}
