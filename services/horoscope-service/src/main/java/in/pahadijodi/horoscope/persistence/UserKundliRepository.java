package in.pahadijodi.horoscope.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserKundliRepository extends JpaRepository<UserKundli, UUID> {}
