package in.pahadijodi.media.service;

import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.media.domain.AccessGrant;
import in.pahadijodi.media.domain.AccessGrantRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** "May I see your photos?" requests for ON_REQUEST privacy. */
@Service
public class AccessService {
    private final AccessGrantRepository grants;

    public AccessService(AccessGrantRepository grants) { this.grants = grants; }

    @Transactional
    public AccessGrant request(UUID viewer, UUID owner) {
        if (viewer.equals(owner)) throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "SELF_REQUEST", "Cannot request your own photos");
        return grants.findByOwnerIdAndViewerId(owner, viewer).orElseGet(() -> grants.save(new AccessGrant(owner, viewer)));
    }

    @Transactional(readOnly = true)
    public List<AccessGrant> incoming(UUID owner) {
        return grants.findByOwnerIdAndStatusOrderByCreatedAtDesc(owner, AccessGrant.Status.PENDING);
    }

    @Transactional
    public AccessGrant decide(UUID owner, UUID requestId, boolean grant) {
        AccessGrant g = grants.findById(requestId).filter(x -> x.getOwnerId().equals(owner))
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "Request not found"));
        g.decide(grant ? AccessGrant.Status.GRANTED : AccessGrant.Status.DENIED);
        return g;
    }
}
