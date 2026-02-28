package eu.eahub.repositories.stats;

import eu.eahub.entities.stats.NfsGameReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Find the best lap time (world record) for a specific track, direction, and game version.
     */
    @Query("SELECT MIN(r.lap) FROM NfsGameReportEntity r " +
            "WHERE r.gameConnection.game.vers = :vers " +
            "AND r.venue = :venue " +
            "AND r.dir = :dir " +
            "AND r.rnk = 1 " +
            "AND r.lap IS NOT NULL AND r.lap > 0")
    Integer findBestLapForTrack(@Param("vers") String vers,
                                @Param("venue") Integer venue,
                                @Param("dir") Integer dir);

    /**
     * Find the best race time (world record) for Most Wanted tracks.
     */
    @Query("SELECT MIN(r.racetime) FROM NfsGameReportEntity r " +
            "WHERE r.gameConnection.game.vers = :vers " +
            "AND r.venue = :venue " +
            "AND r.dir = :dir " +
            "AND r.rnk = 1 " +
            "AND r.racetime IS NOT NULL AND r.racetime > 0")
    Integer findBestRacetimeForTrack(@Param("vers") String vers,
                                     @Param("venue") Integer venue,
                                     @Param("dir") Integer dir);
}
