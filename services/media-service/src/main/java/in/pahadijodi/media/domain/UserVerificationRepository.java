package in.pahadijodi.media.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserVerificationRepository extends JpaRepository<UserVerification, UUID> {}
