package eu.eahub.repositories.stats;

import eu.eahub.entities.stats.FifaPersonaStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FifaPersonaStatsRepository extends JpaRepository<FifaPersonaStatsEntity, Long> {

    FifaPersonaStatsEntity findByPersonaIdAndVers(Long id, String vers);

    @Query(value = """
            SELECT RANK FROM
                (SELECT PERSONA_ID, ROW_NUMBER() OVER(ORDER BY (WINS - LOSSES) DESC, (SCORE - SCORE_AGAINST) DESC, (WINS + LOSSES) DESC, P.PERS ASC) AS RANK
                FROM stats.FIFA_PERSONA_STATS PS
                JOIN core.PERSONA P ON PS.PERSONA_ID = P.ID
                JOIN core.ACCOUNT A ON P.ACCOUNT_ID = A.ID
                WHERE PS.VERS = ?2 AND PS.TIME > 0 AND P.DELETED_ON IS NULL AND A.IS_BANNED = FALSE) AS STATS
            WHERE STATS.PERSONA_ID = ?1
            """, nativeQuery = true)
    Long getRankByPersonaIdAndVers(long id, String vers);

    @Query("""
            FROM FifaPersonaStatsEntity ps
            WHERE ps.vers = :vers AND ps.time > 0
            AND ps.persona.deletedOn IS NULL
            AND ps.persona.account.isBanned = FALSE
            ORDER BY (ps.wins - ps.losses) DESC, (ps.score - ps.scoreAgainst) DESC, (ps.wins + ps.losses) DESC, ps.persona.pers ASC LIMIT :limit OFFSET :offset
            """)
    List<FifaPersonaStatsEntity> getLeaderboardByVers(String vers, long limit, long offset);
}
