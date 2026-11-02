package in.pahadijodi.notification.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreferenceRepository extends JpaRepository<NotificationPreference, UUID> {}
