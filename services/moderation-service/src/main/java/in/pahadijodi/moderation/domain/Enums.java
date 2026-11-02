package in.pahadijodi.moderation.domain;

public final class Enums {
    private Enums() {}
    public enum ContentType { TEXT, IMAGE, BIO, REPORT }
    public enum Decision { ALLOW, FLAG, BLOCK }
    public enum CaseStatus { OPEN, IN_REVIEW, ESCALATED, RESOLVED }
    public enum Resolution { APPROVE, REJECT, ESCALATE }
    public enum ReportReason { HARASSMENT, FAKE_PROFILE, SCAM, INAPPROPRIATE_CONTENT, SPAM, OTHER }
    public enum AccountStatus { ACTIVE, SUSPENDED, BANNED }
    public enum AppealStatus { OPEN, UPHELD, OVERTURNED }
}
