package in.pahadijodi.horoscope.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BirthProfileRepository extends JpaRepository<BirthProfile, UUID> {}
