package in.pahadijodi.admin;

import static org.junit.jupiter.api.Assertions.*;

import in.pahadijodi.admin.policy.BanPolicy;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import org.junit.jupiter.api.Test;

class BanPolicyTest {
    private final BanPolicy policy = new BanPolicy(30);

    @Test
    void shortBansAreImmediateLongOrPermanentNeedApproval() {
        assertFalse(policy.banNeedsApproval(false, 7));
        assertFalse(policy.banNeedsApproval(false, 30));
        assertTrue(policy.banNeedsApproval(false, 31));
        assertTrue(policy.banNeedsApproval(true, null));
        assertTrue(policy.banNeedsApproval(false, null));
    }

    @Test
    void unbanFollowsTheSameSeverityRule() {
        assertFalse(policy.unbanNeedsApproval(false, 14));
        assertTrue(policy.unbanNeedsApproval(false, 90));
        assertTrue(policy.unbanNeedsApproval(true, null));
    }

    @Test
    void durationValidation() {
        policy.validate(true, null);
        policy.validate(false, 1);
        policy.validate(false, BanPolicy.MAX_DURATION_DAYS);
        for (Integer bad : new Integer[]{null, 0, -5, BanPolicy.MAX_DURATION_DAYS + 1}) {
            assertEquals("INVALID_BAN_DURATION", assertThrows(PjException.class, () -> policy.validate(false, bad)).code());
        }
    }

    @Test
    void checkerMustBeADifferentSuperAdmin() {
        assertDoesNotThrow(() -> policy.assertCanDecide("alice", "bob", true));
        assertEquals("MAKER_CHECKER_VIOLATION", assertThrows(PjException.class, () -> policy.assertCanDecide("alice", "alice", true)).code());
        assertEquals("APPROVER_NOT_AUTHORIZED", assertThrows(PjException.class, () -> policy.assertCanDecide("alice", "bob", false)).code());
    }
}
