package eu.eahub.repositories.stats;

import eu.eahub.entities.stats.TwPersonaStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TwPersonaStatsRepository extends JpaRepository<TwPersonaStatsEntity, Long> {

    Optional<TwPersonaStatsEntity> findByPersonaIdAndVers(Long personaId, String vers);

    List<TwPersonaStatsEntity> findByPersonaIdInAndVers(Collection<Long> personaIds, String vers);

    @Query("""
            FROM TwPersonaStatsEntity ps
            WHERE ps.vers = :vers
            AND ps.persona.deletedOn IS NULL
            AND ps.persona.account.isBanned = FALSE
            """)
    List<TwPersonaStatsEntity> findRankedByVers(String vers);

    @Query("""
            FROM TwPersonaStatsEntity ps
            WHERE ps.vers = :vers AND (ps.matchComplete + ps.matchIncomplete + ps.strokeComplete + ps.strokeIncomplete + ps.events) > 0
            AND ps.persona.deletedOn IS NULL
            AND ps.persona.account.isBanned = FALSE
            ORDER BY (ps.matchPoints + ps.strokePoints) DESC, LOWER(ps.persona.pers), ps.persona.id ASC LIMIT :limit OFFSET :offset
            """)
    List<TwPersonaStatsEntity> getLeaderboardByVers(String vers, long limit, long offset);

    @Query("""
            FROM TwPersonaStatsEntity ps
            WHERE ps.vers = :vers AND (ps.matchComplete + ps.matchIncomplete) > 0
            AND ps.persona.deletedOn IS NULL
            AND ps.persona.account.isBanned = FALSE
            ORDER BY ps.matchPoints DESC, LOWER(ps.persona.pers), ps.persona.id ASC LIMIT :limit OFFSET :offset
            """)
    List<TwPersonaStatsEntity> getMatchLeaderboardByVers(String vers, long limit, long offset);

    @Query("""
            FROM TwPersonaStatsEntity ps
            WHERE ps.vers = :vers AND (ps.strokeComplete + ps.strokeIncomplete) > 0
            AND ps.persona.deletedOn IS NULL
            AND ps.persona.account.isBanned = FALSE
            ORDER BY ps.strokePoints DESC, LOWER(ps.persona.pers), ps.persona.id ASC LIMIT :limit OFFSET :offset
            """)
    List<TwPersonaStatsEntity> getStrokeLeaderboardByVers(String vers, long limit, long offset);

    // Players with more points share the rank above: rank = 1 + number of players ahead
    @Query("""
            SELECT COUNT(ps) + 1 FROM TwPersonaStatsEntity ps
            WHERE ps.vers = :vers AND (ps.matchComplete + ps.matchIncomplete + ps.strokeComplete + ps.strokeIncomplete + ps.events) > 0
            AND ps.persona.deletedOn IS NULL
            AND ps.persona.account.isBanned = FALSE
            AND (ps.matchPoints + ps.strokePoints) > :points
            """)
    long getRankByPoints(String vers, int points);

    @Query("""
            SELECT COUNT(ps) + 1 FROM TwPersonaStatsEntity ps
            WHERE ps.vers = :vers AND (ps.matchComplete + ps.matchIncomplete) > 0
            AND ps.persona.deletedOn IS NULL
            AND ps.persona.account.isBanned = FALSE
            AND ps.matchPoints > :points
            """)
    long getMatchRankByPoints(String vers, int points);

    @Query("""
            SELECT COUNT(ps) + 1 FROM TwPersonaStatsEntity ps
            WHERE ps.vers = :vers AND (ps.strokeComplete + ps.strokeIncomplete) > 0
            AND ps.persona.deletedOn IS NULL
            AND ps.persona.account.isBanned = FALSE
            AND ps.strokePoints > :points
            """)
    long getStrokeRankByPoints(String vers, int points);

    @Query("""
            SELECT COUNT(ps) + 1 FROM TwPersonaStatsEntity ps
            WHERE ps.vers = :vers
            AND ps.persona.deletedOn IS NULL
            AND ps.persona.account.isBanned = FALSE
            AND ps.earnings > :earnings
            """)
    long getEarningsRank(String vers, long earnings);

}
