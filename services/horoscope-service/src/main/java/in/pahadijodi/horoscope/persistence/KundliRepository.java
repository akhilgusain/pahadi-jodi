package in.pahadijodi.horoscope.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KundliRepository extends JpaRepository<KundliEntity, UUID> {
    Optional<KundliEntity> findByBirthHashAndAyanamsaAndEngineVersion(String birthHash, String ayanamsa, String engineVersion);
}
