package com.ea.repositories.stats;

import com.ea.entities.stats.NfsGameReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NfsGameReportRepository extends JpaRepository<NfsGameReportEntity, Long> {
    boolean existsByGameConnectionId(Long gameConnectionId);

    @Query(value = """
            SELECT gr FROM NfsGameReportEntity gr
            WHERE gr.gameConnectionId IN (
                SELECT MIN(gr2.gameConnectionId)
                FROM NfsGameReportEntity gr2
                INNER JOIN (
                    SELECT gr3.gameConnection.personaConnection.persona.id as personaId, MIN(gr3.lap) as minLap
                    FROM NfsGameReportEntity gr3
                    WHERE gr3.gameConnection.game.vers = :vers
                    AND gr3.venue = :venue
                    AND gr3.dir = :dir
                    AND gr3.rnk = 1
                    AND gr3.lap > 0
                    AND gr3.gameConnection.personaConnection.persona.deletedOn IS NULL
                    AND gr3.gameConnection.personaConnection.persona.account.isBanned = FALSE
                    GROUP BY gr3.gameConnection.personaConnection.persona.id
                ) minTimes ON gr2.gameConnection.personaConnection.persona.id = minTimes.personaId
                AND gr2.lap = minTimes.minLap
                WHERE gr2.gameConnection.game.vers = :vers
                AND gr2.venue = :venue
                AND gr2.dir = :dir
                AND gr2.rnk = 1
                GROUP BY gr2.gameConnection.personaConnection.persona.id
            )
            ORDER BY gr.lap ASC LIMIT :limit OFFSET :offset
            """)
    List<NfsGameReportEntity> getLapRecordsByVenueAndDir(String vers, int venue, int dir, long limit, long offset);

    @Query(value = """
            SELECT gr FROM NfsGameReportEntity gr
            WHERE gr.gameConnectionId IN (
                SELECT MIN(gr2.gameConnectionId)
                FROM NfsGameReportEntity gr2
                INNER JOIN (
                    SELECT gr3.gameConnection.personaConnection.persona.id as personaId, MIN(gr3.racetime) as minRacetime
                    FROM NfsGameReportEntity gr3
                    WHERE gr3.gameConnection.game.vers = :vers
                    AND gr3.venue = :venue
                    AND gr3.dir = :dir
                    AND gr3.rnk = 1
                    AND gr3.racetime > 0
                    AND gr3.gameConnection.personaConnection.persona.deletedOn IS NULL
                    AND gr3.gameConnection.personaConnection.persona.account.isBanned = FALSE
                    GROUP BY gr3.gameConnection.personaConnection.persona.id
                ) minTimes ON gr2.gameConnection.personaConnection.persona.id = minTimes.personaId
                AND gr2.racetime = minTimes.minRacetime
                WHERE gr2.gameConnection.game.vers = :vers
                AND gr2.venue = :venue
                AND gr2.dir = :dir
                AND gr2.rnk = 1
                GROUP BY gr2.gameConnection.personaConnection.persona.id
            )
            ORDER BY gr.racetime ASC LIMIT :limit OFFSET :offset
            """)
    List<NfsGameReportEntity> getRacetimeRecordsByVenueAndDir(String vers, int venue, int dir, long limit, long offset);
}
