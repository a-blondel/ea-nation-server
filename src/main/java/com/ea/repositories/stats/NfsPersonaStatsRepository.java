package com.ea.repositories.stats;

import com.ea.entities.stats.NfsPersonaStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface NfsPersonaStatsRepository extends JpaRepository<NfsPersonaStatsEntity, Long> {
    NfsPersonaStatsEntity findByPersonaIdAndVers(Long personaId, String vers);

    @Query(value = """
            SELECT RANK FROM
                (SELECT PERSONA_ID, ROW_NUMBER() OVER(ORDER BY (WINS - LOSSES) DESC, PERSONA_ID ASC) AS RANK
                FROM stats.NFS_PERSONA_STATS PS
                JOIN core.PERSONA P ON PS.PERSONA_ID = P.ID
                JOIN core.ACCOUNT A ON P.ACCOUNT_ID = A.ID
                WHERE PS.VERS = ?2 AND PS.TIME > 0 AND P.DELETED_ON IS NULL AND A.IS_BANNED = FALSE) AS STATS
            WHERE STATS.PERSONA_ID = ?1
            """, nativeQuery = true)
    Long getRankByPersonaIdAndVers(long id, String vers);
}
