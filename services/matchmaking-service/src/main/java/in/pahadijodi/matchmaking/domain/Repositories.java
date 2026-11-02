package in.pahadijodi.matchmaking.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public final class Repositories {
    private Repositories() {}

    public interface ProfileFacetRepository extends JpaRepository<ProfileFacet, UUID> {
        Optional<ProfileFacet> findByUserId(UUID userId);
        List<ProfileFacet> findByUserIdNot(UUID userId);
    }

    public interface PartnerPreferenceRepository extends JpaRepository<PartnerPreference, UUID> {}

    public interface InterestRepository extends JpaRepository<Interest, UUID> {
        Optional<Interest> findByFromUserIdAndToUserId(UUID from, UUID to);
        List<Interest> findByFromUserIdOrderByCreatedAtDesc(UUID from);
        List<Interest> findByToUserIdOrderByCreatedAtDesc(UUID to);
        @Query("select i from Interest i where (i.fromUserId = :u or i.toUserId = :u) and i.status = :status and i.respondedAt > :since")
        List<Interest> recentWithStatus(@Param("u") UUID user, @Param("status") Interest.Status status, @Param("since") Instant since);
        default List<Interest> recentDeclines(UUID user, Instant since) { return recentWithStatus(user, Interest.Status.DECLINED, since); }
    }

    public interface MatchRecordRepository extends JpaRepository<MatchRecord, UUID> {
        Optional<MatchRecord> findByUserAAndUserB(UUID a, UUID b);
        @Query("select m from MatchRecord m where m.userA = :u or m.userB = :u order by m.createdAt desc")
        List<MatchRecord> forUser(@Param("u") UUID user);
    }

    public interface ShortlistRepository extends JpaRepository<ShortlistEntry, UUID> {
        List<ShortlistEntry> findByUserIdAndKindOrderByCreatedAtDesc(UUID userId, ShortlistEntry.Kind kind);
        List<ShortlistEntry> findByUserIdAndKindAndListNameOrderByCreatedAtDesc(UUID userId, ShortlistEntry.Kind kind, String listName);
        Optional<ShortlistEntry> findByUserIdAndTargetProfileIdAndKindAndListName(UUID u, UUID t, ShortlistEntry.Kind k, String l);
        @Modifying
        @Query("delete from ShortlistEntry e where e.userId = :u and e.targetProfileId = :t and e.kind = :k and e.listName = :l")
        int remove(@Param("u") UUID u, @Param("t") UUID t, @Param("k") ShortlistEntry.Kind k, @Param("l") String l);
        /** Profiles this user has blocked or ignored, plus profiles that blocked this user's profile. */
        @Query("select e from ShortlistEntry e where e.kind in :kinds and (e.userId = :u or e.targetProfileId = :p)")
        List<ShortlistEntry> exclusionsFor(@Param("u") UUID user, @Param("p") UUID profile, @Param("kinds") Collection<ShortlistEntry.Kind> kinds);
    }

    public interface ProfileNoteRepository extends JpaRepository<ProfileNote, UUID> {
        Optional<ProfileNote> findByUserIdAndTargetProfileId(UUID userId, UUID target);
        List<ProfileNote> findByUserIdOrderByUpdatedAtDesc(UUID userId);
        @Modifying
        @Query("delete from ProfileNote n where n.userId = :u and n.targetProfileId = :t")
        int remove(@Param("u") UUID u, @Param("t") UUID t);
    }

    public interface DailyRecommendationRepository extends JpaRepository<DailyRecommendation, UUID> {
        List<DailyRecommendation> findByUserIdAndRecDateOrderByRankNo(UUID userId, LocalDate date);
        @Modifying
        @Query("delete from DailyRecommendation d where d.userId = :u and d.recDate = :d")
        int clear(@Param("u") UUID user, @Param("d") LocalDate date);
    }
}
