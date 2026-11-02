package in.pahadijodi.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contact")
public class Contact {
    @Id @Column(name = "user_id") private UUID userId;
    private String email;
    @Column(name = "email_verified", nullable = false) private boolean emailVerified;
    private String phone;
    @Column(name = "phone_verified", nullable = false) private boolean phoneVerified;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    protected Contact() {}
    public Contact(UUID userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getPhone() { return phone; }
    public boolean isPhoneVerified() { return phoneVerified; }

    /** Changing a contact value resets its verified flag (re-verification via OTP in identity-service). */
    public void setEmail(String e) {
        if (e != null && !e.equalsIgnoreCase(email)) { this.email = e.toLowerCase(); this.emailVerified = false; }
        this.updatedAt = Instant.now();
    }
    public void setPhone(String p) {
        if (p != null && !p.equals(phone)) { this.phone = p; this.phoneVerified = false; }
        this.updatedAt = Instant.now();
    }
    public void markPhoneVerified() { this.phoneVerified = true; }
    public void markEmailVerified() { this.emailVerified = true; }
    public void clear() { this.email = null; this.phone = null; this.emailVerified = false; this.phoneVerified = false; }
}
