package in.pahadijodi.user.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public final class Repos {
    private Repos() {}

    public interface AppUsers extends JpaRepository<AppUser, UUID> {}
    public interface Contacts extends JpaRepository<Contact, UUID> {}
    public interface Preferences extends JpaRepository<Preference, UUID> {}

    public interface Consents extends JpaRepository<Consent, UUID> {
        List<Consent> findByUserIdOrderByRecordedAtDesc(UUID userId);
        void deleteByUserId(UUID userId);
    }

    public interface Devices extends JpaRepository<Device, UUID> {
        List<Device> findByUserIdAndRevokedAtIsNullOrderByLastSeenDesc(UUID userId);
        Optional<Device> findByIdAndUserId(UUID id, UUID userId);
        void deleteByUserId(UUID userId);
    }

    public interface DataRequests extends JpaRepository<DataRequest, UUID> {
        List<DataRequest> findByUserIdOrderByRequestedAtDesc(UUID userId);
        Optional<DataRequest> findFirstByUserIdAndTypeAndStatus(UUID userId, DataRequest.Type t, DataRequest.Status s);
        List<DataRequest> findByTypeAndStatusAndScheduledForBefore(DataRequest.Type t, DataRequest.Status s, Instant now);
    }
}
