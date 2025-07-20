package com.ea.repositories.stats;

import com.ea.entities.stats.NhlPersonaStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NhlPersonaStatsRepository extends JpaRepository<NhlPersonaStatsEntity, Long> {

    /**
     * Find persona stats by persona ID and version
     *
     * @param id   The persona ID
     * @param vers The game version
     * @return The NHL persona stats entity, or null if not found
     */
    NhlPersonaStatsEntity findByPersonaIdAndVers(Long id, String vers);

    /**
     * Get the ranking position of a persona
     *
     * @param id   The persona ID
     * @param vers The game version
     * @return The ranking position, or null if not found
     */
    @Query(value = """
            SELECT RANK FROM
                (SELECT PERSONA_ID, ROW_NUMBER() OVER(ORDER BY POINTS DESC, (SCORE - SCORE_AGAINST) DESC, PERSONA_ID ASC) AS RANK
                FROM stats.NHL_PERSONA_STATS PS
                JOIN core.PERSONA P ON PS.PERSONA_ID = P.ID
                JOIN core.ACCOUNT A ON P.ACCOUNT_ID = A.ID
                WHERE PS.VERS = ?2 AND PS.TIME > 0 AND P.DELETED_ON IS NULL AND A.IS_BANNED = FALSE) AS STATS
            WHERE STATS.PERSONA_ID = ?1
            """, nativeQuery = true)
    Long getRankByPersonaIdAndVers(long id, String vers);

    /**
     * Get leaderboard ordered by points, goals difference, and persona ID
     *
     * @param vers   The game version
     * @param limit  Maximum number of results
     * @param offset Starting offset
     * @return List of NHL persona stats entities
     */
    @Query(value = """
            FROM NhlPersonaStatsEntity ps
            WHERE ps.vers = :vers AND ps.time > 0
            AND ps.persona.deletedOn IS NULL
            AND ps.persona.account.isBanned = FALSE
            ORDER BY ps.points DESC, (ps.score - ps.scoreAgainst) DESC, ps.persona.id ASC LIMIT :limit OFFSET :offset
            """)
    List<NhlPersonaStatsEntity> getLeaderboardByVers(String vers, long limit, long offset);

}
