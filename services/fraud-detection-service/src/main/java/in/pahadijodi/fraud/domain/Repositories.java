package in.pahadijodi.fraud.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public final class Repositories {
    private Repositories() {}

    public interface SignalRepository extends JpaRepository<FraudSignal, UUID> {
        @Query("select count(distinct s.userId) from FraudSignal s where s.ip = :ip and s.createdAt >= :since")
        int distinctUsersByIp(@Param("ip") String ip, @Param("since") Instant since);

        @Query("select count(distinct s.userId) from FraudSignal s where s.deviceHash = :device and s.createdAt >= :since")
        int distinctUsersByDevice(@Param("device") String device, @Param("since") Instant since);

        @Query("select count(s) from FraudSignal s where s.userId = :user and s.disposableEmail = true")
        int disposableCount(@Param("user") UUID user);

        @Query("select coalesce(sum(s.scamHits), 0) from FraudSignal s where s.userId = :user and s.type = 'CHAT_MESSAGE' and s.createdAt >= :since")
        long scamHits(@Param("user") UUID user, @Param("since") Instant since);

        @Query("select count(distinct s.counterpartId) from FraudSignal s where s.userId = :user and s.type = 'CHAT_MESSAGE' and s.createdAt >= :since")
        int distinctRecipients(@Param("user") UUID user, @Param("since") Instant since);

        @Query("select count(s) from FraudSignal s where s.userId = :user and s.type = :type and s.createdAt >= :since")
        int countByType(@Param("user") UUID user, @Param("type") FraudSignal.Type type, @Param("since") Instant since);

        /** Latest signal that carried a device/IP, used to anchor velocity checks to the user's current context. */
        @Query("select s from FraudSignal s where s.userId = :user and (s.ip is not null or s.deviceHash is not null) order by s.createdAt desc")
        List<FraudSignal> latestContext(@Param("user") UUID user, Pageable page);
    }

    public interface RuleRepository extends JpaRepository<FraudRule, String> {}

    public interface EvaluationRepository extends JpaRepository<RiskEvaluation, UUID> {
        List<RiskEvaluation> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable page);
    }

    public interface UserRiskRepository extends JpaRepository<UserRisk, UUID> {}

    public interface CaseRepository extends JpaRepository<FraudCase, UUID> {
        List<FraudCase> findByStatusOrderByOpenedAtDesc(FraudCase.Status status, Pageable page);
        Optional<FraudCase> findFirstByUserIdAndStatus(UUID userId, FraudCase.Status status);
        long countByStatus(FraudCase.Status status);
    }

    public interface ProfileFingerprintRepository extends JpaRepository<ProfileFingerprint, UUID> {
        @Query("select f from ProfileFingerprint f where f.userId <> :user and (f.textHash = :hash or f.band0 = :b0 or f.band1 = :b1 or f.band2 = :b2 or f.band3 = :b3)")
        List<ProfileFingerprint> candidates(@Param("user") UUID user, @Param("hash") String hash, @Param("b0") int b0,
                                            @Param("b1") int b1, @Param("b2") int b2, @Param("b3") int b3);
    }

    public interface PhotoHashRepository extends JpaRepository<PhotoHash, UUID> {
        List<PhotoHash> findByUserId(UUID userId);

        @Query("select p from PhotoHash p where p.userId <> :user and (p.band0 = :b0 or p.band1 = :b1 or p.band2 = :b2 or p.band3 = :b3)")
        List<PhotoHash> candidates(@Param("user") UUID user, @Param("b0") int b0, @Param("b1") int b1,
                                   @Param("b2") int b2, @Param("b3") int b3);
    }
}
