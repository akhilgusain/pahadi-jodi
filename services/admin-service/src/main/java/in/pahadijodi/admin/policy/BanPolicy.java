package in.pahadijodi.admin.policy;

import in.pahadijodi.common.web.ProblemAdvice.PjException;
import org.springframework.http.HttpStatus;

/** Pure maker-checker rules for bans. Short bans are immediate; long or permanent ones need a second super-admin. */
public final class BanPolicy {
    public static final int MAX_DURATION_DAYS = 3650;

    private final int autoApproveMaxDays;

    public BanPolicy(int autoApproveMaxDays) {
        this.autoApproveMaxDays = autoApproveMaxDays;
    }

    /** Throws if the duration is not usable. permanent bans ignore durationDays. */
    public void validate(boolean permanent, Integer durationDays) {
        if (permanent) return;
        if (durationDays == null || durationDays < 1 || durationDays > MAX_DURATION_DAYS) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BAN_DURATION",
                    "durationDays must be between 1 and " + MAX_DURATION_DAYS + " unless the ban is permanent");
        }
    }

    public boolean banNeedsApproval(boolean permanent, Integer durationDays) {
        return permanent || durationDays == null || durationDays > autoApproveMaxDays;
    }

    /** Lifting a long or permanent ban is as serious as imposing it. */
    public boolean unbanNeedsApproval(boolean currentPermanent, Integer currentDurationDays) {
        return banNeedsApproval(currentPermanent, currentDurationDays);
    }

    public void assertCanDecide(String maker, String checker, boolean checkerIsSuperAdmin) {
        if (!checkerIsSuperAdmin) {
            throw new PjException(HttpStatus.FORBIDDEN, "APPROVER_NOT_AUTHORIZED", "Only a super admin can approve");
        }
        if (maker.equals(checker)) {
            throw new PjException(HttpStatus.FORBIDDEN, "MAKER_CHECKER_VIOLATION", "The requester cannot approve their own request");
        }
    }
}
