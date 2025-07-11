package com.ea.repositories.stats;

import com.ea.entities.stats.MohhPersonaStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MohhPersonaStatsRepository extends JpaRepository<MohhPersonaStatsEntity, Long> {

    MohhPersonaStatsEntity findByPersonaIdAndVers(Long id, String vers);

    MohhPersonaStatsEntity findByPersonaIdAndVersIn(Long id, List<String> vers);

    @Query(value = """
            SELECT RANK FROM
                (SELECT PERSONA_ID, ROW_NUMBER() OVER(ORDER BY (KILL - DEATH) DESC, PERSONA_ID ASC) AS RANK
                FROM stats.MOHH_PERSONA_STATS PS
                JOIN core.PERSONA P ON PS.PERSONA_ID = P.ID
                JOIN core.ACCOUNT A ON P.ACCOUNT_ID = A.ID
                WHERE PS.VERS = ?2 AND PS.PLAYTIME > 0 AND P.DELETED_ON IS NULL AND A.IS_BANNED = FALSE) AS STATS
            WHERE STATS.PERSONA_ID = ?1
            """, nativeQuery = true)
    Long getRankByPersonaIdAndVers(long id, String vers);

    @Query(value = """
            FROM MohhPersonaStatsEntity ps
            WHERE ps.vers = :vers AND ps.playTime > 0
            AND ps.persona.deletedOn IS NULL
            AND ps.persona.account.isBanned = FALSE
            ORDER BY (kill - death) DESC, persona.id ASC LIMIT :limit OFFSET :offset
            """)
    List<MohhPersonaStatsEntity> getLeaderboardByVers(String vers, long limit, long offset);

    @Query(value = """
            FROM MohhPersonaStatsEntity ps
            WHERE ps.vers = :vers AND ps.playTime > 0
            AND ps.persona.deletedOn IS NULL
            AND ps.persona.account.isBanned = FALSE
            ORDER BY kill DESC, persona.id ASC
            LIMIT :limit OFFSET :offset
            """)
    List<MohhPersonaStatsEntity> getWeaponLeaderboardByVers(String vers, long limit, long offset);

}
