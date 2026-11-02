package in.pahadijodi.user.service;

import in.pahadijodi.common.outbox.OutboxService;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.user.domain.*;
import in.pahadijodi.user.domain.AppUser.Status;
import in.pahadijodi.user.domain.Consent.Type;
import in.pahadijodi.user.domain.DataRequest;
import in.pahadijodi.user.domain.Repos.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    public static final String T_DEACTIVATED = "pj.user.deactivated.v1";
    public static final String T_REACTIVATED = "pj.user.reactivated.v1";
    public static final String T_ERASURE_REQUESTED = "pj.user.erasure-requested.v1";
    public static final String T_ERASURE_CANCELED = "pj.user.erasure-canceled.v1";
    public static final String T_ACCOUNT_DELETED = "pj.user.account-deleted.v1";
    public static final String T_EXPORT_REQUESTED = "pj.user.export-requested.v1";
    public static final String T_DEVICE_REVOKED = "pj.user.device-revoked.v1";

    private final AppUsers users;
    private final Contacts contacts;
    private final Preferences prefs;
    private final Consents consents;
    private final Devices devices;
    private final DataRequests requests;
    private final UserBootstrap bootstrap;
    private final OutboxService outbox;
    private final long graceDays;

    public UserService(AppUsers users, Contacts contacts, Preferences prefs, Consents consents, Devices devices,
                       DataRequests requests, UserBootstrap bootstrap, OutboxService outbox,
                       @Value("${pj.user.erasure-grace-days:30}") long graceDays) {
        this.users = users; this.contacts = contacts; this.prefs = prefs; this.consents = consents;
        this.devices = devices; this.requests = requests; this.bootstrap = bootstrap; this.outbox = outbox;
        this.graceDays = graceDays;
    }

    /** Idempotent: called by the identity event consumer and lazily on first GET /me. */
    public void ensureUser(UUID id) {
        if (users.existsById(id)) return;
        try {
            bootstrap.create(id);
        } catch (DataIntegrityViolationException raced) {
            log.debug("user {} created concurrently", id);
        }
    }

    // ---------------- view ----------------
    public record Me(UUID id, Status status, Instant createdAt, Instant deletionScheduledAt,
                     ContactView contact, PreferenceView preferences) {}
    public record ContactView(String email, boolean emailVerified, String phoneMasked, boolean phoneVerified) {}
    public record PreferenceView(String language, boolean notifyEmail, boolean notifySms, boolean notifyPush,
                                 boolean notifyWhatsapp, String profileVisibility, String allowDirectMessages) {}

    @Transactional
    public Me me(UUID id) {
        ensureUser(id);
        AppUser u = users.findById(id).orElseThrow();
        Contact c = contacts.findById(id).orElseThrow();
        Preference p = prefs.findById(id).orElseThrow();
        return new Me(u.getId(), u.getStatus(), u.getCreatedAt(), u.getDeletionScheduledAt(),
                new ContactView(c.getEmail(), c.isEmailVerified(), mask(c.getPhone()), c.isPhoneVerified()),
                new PreferenceView(p.getLanguage(), p.isNotifyEmail(), p.isNotifySms(), p.isNotifyPush(),
                        p.isNotifyWhatsapp(), p.getProfileVisibility(), p.getAllowDirectMessages()));
    }

    public static String mask(String phone) {
        if (phone == null) return null;
        if (phone.length() <= 7) return "*".repeat(phone.length());
        return phone.substring(0, 3) + "*".repeat(phone.length() - 7) + phone.substring(phone.length() - 4);
    }

    // ---------------- contact / preferences ----------------
    @Transactional
    public Me updateContact(UUID id, String email, String phone) {
        ensureUser(id);
        Contact c = contacts.findById(id).orElseThrow();
        if (email != null) c.setEmail(email);
        if (phone != null) c.setPhone(phone);
        contacts.save(c);
        return me(id);
    }

    @Transactional
    public Me updatePreferences(UUID id, PreferenceView v) {
        ensureUser(id);
        Preference p = prefs.findById(id).orElseThrow();
        p.update(v.language(), v.notifyEmail(), v.notifySms(), v.notifyPush(), v.notifyWhatsapp(),
                v.profileVisibility(), v.allowDirectMessages());
        prefs.save(p);
        return me(id);
    }

    // ---------------- consents ----------------
    @Transactional
    public List<Consent> recordConsent(UUID id, Type type, String version, boolean granted) {
        ensureUser(id);
        if (!granted && type != Type.MARKETING) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "CONSENT_REQUIRED",
                    type + " consent is required to use the service; request account erasure instead");
        }
        consents.save(new Consent(id, type, version, granted));
        return currentConsents(id);
    }

    @Transactional
    public List<Consent> currentConsents(UUID id) {
        ensureUser(id);
        Map<Type, Consent> latest = new EnumMap<>(Type.class);
        for (Consent c : consents.findByUserIdOrderByRecordedAtDesc(id)) latest.putIfAbsent(c.getType(), c);
        return new ArrayList<>(latest.values());
    }

    // ---------------- devices ----------------
    @Transactional
    public Device registerDevice(UUID id, String name, String platform) {
        ensureUser(id);
        return devices.save(new Device(id, name, platform));
    }

    @Transactional
    public List<Device> listDevices(UUID id) {
        ensureUser(id);
        return devices.findByUserIdAndRevokedAtIsNullOrderByLastSeenDesc(id);
    }

    @Transactional
    public void revokeDevice(UUID id, UUID deviceId) {
        Device d = devices.findByIdAndUserId(deviceId, id)
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", "Device not found"));
        d.revoke();
        devices.save(d);
        // identity-service consumes this to revoke the device's refresh-token family / sessions
        outbox.publish(T_DEVICE_REVOKED, "DeviceRevoked", id.toString(),
                Map.of("userId", id.toString(), "deviceId", deviceId.toString()));
    }

    // ---------------- account status ----------------
    @Transactional
    public Me deactivate(UUID id) {
        ensureUser(id);
        AppUser u = users.findById(id).orElseThrow();
        transition(u::deactivate);
        users.save(u);
        outbox.publish(T_DEACTIVATED, "UserDeactivated", id.toString(), Map.of("userId", id.toString()));
        return me(id);
    }

    @Transactional
    public Me reactivate(UUID id) {
        ensureUser(id);
        AppUser u = users.findById(id).orElseThrow();
        transition(u::reactivate);
        users.save(u);
        outbox.publish(T_REACTIVATED, "UserReactivated", id.toString(), Map.of("userId", id.toString()));
        return me(id);
    }

    // ---------------- data-subject requests ----------------
    @Transactional
    public DataRequest requestErasure(UUID id) {
        ensureUser(id);
        if (requests.findFirstByUserIdAndTypeAndStatus(id, DataRequest.Type.ERASURE, DataRequest.Status.REQUESTED).isPresent()) {
            throw new PjException(HttpStatus.CONFLICT, "ERASURE_ALREADY_REQUESTED", "Erasure already requested");
        }
        AppUser u = users.findById(id).orElseThrow();
        Instant when = Instant.now().plus(Duration.ofDays(graceDays));
        transition(() -> u.scheduleDeletion(when));
        users.save(u);
        DataRequest r = requests.save(new DataRequest(id, DataRequest.Type.ERASURE, when));
        outbox.publish(T_ERASURE_REQUESTED, "ErasureRequested", id.toString(),
                Map.of("userId", id.toString(), "scheduledFor", when.toString()));
        return r;
    }

    @Transactional
    public void cancelErasure(UUID id) {
        DataRequest r = requests.findFirstByUserIdAndTypeAndStatus(id, DataRequest.Type.ERASURE, DataRequest.Status.REQUESTED)
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "NO_PENDING_ERASURE", "No erasure request pending"));
        AppUser u = users.findById(id).orElseThrow();
        transition(r::cancel);
        transition(u::cancelDeletion);
        requests.save(r);
        users.save(u);
        outbox.publish(T_ERASURE_CANCELED, "ErasureCanceled", id.toString(), Map.of("userId", id.toString()));
    }

    @Transactional
    public DataRequest requestExport(UUID id) {
        ensureUser(id);
        if (requests.findFirstByUserIdAndTypeAndStatus(id, DataRequest.Type.EXPORT, DataRequest.Status.REQUESTED).isPresent()) {
            throw new PjException(HttpStatus.CONFLICT, "EXPORT_ALREADY_REQUESTED", "Export already in progress");
        }
        DataRequest r = requests.save(new DataRequest(id, DataRequest.Type.EXPORT, null));
        // each service assembles its slice; media-service/admin-service deliver the archive link
        outbox.publish(T_EXPORT_REQUESTED, "ExportRequested", id.toString(),
                Map.of("userId", id.toString(), "requestId", r.getId().toString()));
        return r;
    }

    @Transactional
    public List<DataRequest> listRequests(UUID id) {
        ensureUser(id);
        return requests.findByUserIdOrderByRequestedAtDesc(id);
    }

    /** Scheduled sweep: erasures whose grace period elapsed are executed. Returns how many. */
    @Transactional
    public int processDueErasures(Instant now) {
        int n = 0;
        for (DataRequest r : requests.findByTypeAndStatusAndScheduledForBefore(
                DataRequest.Type.ERASURE, DataRequest.Status.REQUESTED, now)) {
            UUID id = r.getUserId();
            AppUser u = users.findById(id).orElseThrow();
            u.markDeleted();
            contacts.findById(id).ifPresent(c -> { c.clear(); contacts.save(c); });
            prefs.deleteById(id);
            devices.deleteByUserId(id);
            // the consent ledger is retained as legal evidence (no personal data beyond the id)
            r.complete();
            users.save(u);
            requests.save(r);
            outbox.publish(T_ACCOUNT_DELETED, "AccountDeleted", id.toString(), Map.of("userId", id.toString()));
            n++;
        }
        return n;
    }

    private static void transition(Runnable r) {
        try { r.run(); }
        catch (IllegalStateException e) { throw new PjException(HttpStatus.CONFLICT, "INVALID_STATE", e.getMessage()); }
    }
}
