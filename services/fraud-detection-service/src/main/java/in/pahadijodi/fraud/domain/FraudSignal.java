package in.pahadijodi.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_signal")
public class FraudSignal {
    public enum Type { REGISTERED, LOGIN, PROFILE_UPDATED, CHAT_MESSAGE, PAYMENT_FAILED, PAYMENT_CAPTURED, CHARGEBACK, PHOTO_UPLOADED, CUSTOM }

    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(name = "signal_type", nullable = false) private Type type;
    @Column(name = "device_hash") private String deviceHash;
    private String ip;
    @Column(name = "user_agent") private String userAgent;
    @Column(name = "email_domain") private String emailDomain;
    @Column(name = "phone_hash") private String phoneHash;
    @Column(name = "disposable_email", nullable = false) private boolean disposableEmail;
    @Column(name = "counterpart_id") private UUID counterpartId;
    @Column(name = "scam_hits", nullable = false) private int scamHits;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected FraudSignal() {}

    public FraudSignal(UUID userId, Type type) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.createdAt = Instant.now();
    }

    public FraudSignal device(String deviceHash, String ip, String userAgent) {
        this.deviceHash = deviceHash; this.ip = ip; this.userAgent = userAgent; return this;
    }
    public FraudSignal email(String domain, boolean disposable) { this.emailDomain = domain; this.disposableEmail = disposable; return this; }
    public FraudSignal phone(String phoneHash) { this.phoneHash = phoneHash; return this; }
    public FraudSignal chat(UUID counterpartId, int scamHits) { this.counterpartId = counterpartId; this.scamHits = scamHits; return this; }

    public UUID getUserId() { return userId; }
    public Type getType() { return type; }
    public String getDeviceHash() { return deviceHash; }
    public String getIp() { return ip; }
}
