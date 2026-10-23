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

    List<TwPersonaStatsEntity> findByVers(String vers);

    @Query("""
            FROM TwPersonaStatsEntity s
            WHERE s.vers = :vers AND s.persona.deletedOn IS NULL
            AND (s.matchComplete + s.matchIncomplete + s.strokeComplete + s.strokeIncomplete + s.events) > 0
            ORDER BY (s.matchPoints + s.strokePoints) DESC, LOWER(s.persona.pers), s.persona.id LIMIT :limit OFFSET :offset
            """)
    List<TwPersonaStatsEntity> getLeaderboardByVers(String vers, long limit, long offset);

    @Query("""
            FROM TwPersonaStatsEntity s
            WHERE s.vers = :vers AND s.persona.deletedOn IS NULL AND (s.matchComplete + s.matchIncomplete) > 0
            ORDER BY s.matchPoints DESC, LOWER(s.persona.pers), s.persona.id LIMIT :limit OFFSET :offset
            """)
    List<TwPersonaStatsEntity> getMatchLeaderboardByVers(String vers, long limit, long offset);

    @Query("""
            FROM TwPersonaStatsEntity s
            WHERE s.vers = :vers AND s.persona.deletedOn IS NULL AND (s.strokeComplete + s.strokeIncomplete) > 0
            ORDER BY s.strokePoints DESC, LOWER(s.persona.pers), s.persona.id LIMIT :limit OFFSET :offset
            """)
    List<TwPersonaStatsEntity> getStrokeLeaderboardByVers(String vers, long limit, long offset);

    // Players with more points share the rank above: rank = 1 + number of players ahead
    @Query("""
            SELECT COUNT(s) + 1 FROM TwPersonaStatsEntity s
            WHERE s.vers = :vers AND s.persona.deletedOn IS NULL
            AND (s.matchComplete + s.matchIncomplete + s.strokeComplete + s.strokeIncomplete + s.events) > 0
            AND (s.matchPoints + s.strokePoints) > :points
            """)
    long getRankByPoints(String vers, int points);

    @Query("""
            SELECT COUNT(s) + 1 FROM TwPersonaStatsEntity s
            WHERE s.vers = :vers AND s.persona.deletedOn IS NULL AND (s.matchComplete + s.matchIncomplete) > 0
            AND s.matchPoints > :points
            """)
    long getMatchRankByPoints(String vers, int points);

    @Query("""
            SELECT COUNT(s) + 1 FROM TwPersonaStatsEntity s
            WHERE s.vers = :vers AND s.persona.deletedOn IS NULL AND (s.strokeComplete + s.strokeIncomplete) > 0
            AND s.strokePoints > :points
            """)
    long getStrokeRankByPoints(String vers, int points);

    @Query("SELECT COUNT(s) + 1 FROM TwPersonaStatsEntity s WHERE s.vers = :vers AND s.earnings > :earnings")
    long getEarningsRank(String vers, long earnings);

}
