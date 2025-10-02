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
            FROM NfsGameReportEntity gr
            WHERE gr.gameConnection.game.vers = :vers AND gr.venue = :venue AND gr.dir = :dir AND gr.rnk = 1 AND gr.lap > 0
            AND gr.gameConnection.personaConnection.persona.deletedOn IS NULL
            AND gr.gameConnection.personaConnection.persona.account.isBanned = FALSE
            AND gr.lap = (
                SELECT MIN(gr2.lap) 
                FROM NfsGameReportEntity gr2
                WHERE gr2.gameConnection.game.vers = :vers 
                AND gr2.venue = :venue 
                AND gr2.dir = :dir 
                AND gr2.rnk = 1 
                AND gr2.lap > 0
                AND gr2.gameConnection.personaConnection.persona.id = gr.gameConnection.personaConnection.persona.id
                AND gr2.gameConnection.personaConnection.persona.deletedOn IS NULL
                AND gr2.gameConnection.personaConnection.persona.account.isBanned = FALSE
            )
            ORDER BY gr.lap ASC LIMIT :limit OFFSET :offset
            """)
    List<NfsGameReportEntity> getLapRecordsByVenueAndDir(String vers, int venue, int dir, long limit, long offset);

    @Query(value = """
            FROM NfsGameReportEntity gr
            WHERE gr.gameConnection.game.vers = :vers AND gr.venue = :venue AND gr.dir = :dir AND gr.rnk = 1 AND gr.racetime > 0
            AND gr.gameConnection.personaConnection.persona.deletedOn IS NULL
            AND gr.gameConnection.personaConnection.persona.account.isBanned = FALSE
            AND gr.racetime = (
                SELECT MIN(gr2.racetime) 
                FROM NfsGameReportEntity gr2
                WHERE gr2.gameConnection.game.vers = :vers 
                AND gr2.venue = :venue 
                AND gr2.dir = :dir 
                AND gr2.rnk = 1 
                AND gr2.racetime > 0
                AND gr2.gameConnection.personaConnection.persona.id = gr.gameConnection.personaConnection.persona.id
                AND gr2.gameConnection.personaConnection.persona.deletedOn IS NULL
                AND gr2.gameConnection.personaConnection.persona.account.isBanned = FALSE
            )
            ORDER BY gr.racetime ASC LIMIT :limit OFFSET :offset
            """)
    List<NfsGameReportEntity> getRacetimeRecordsByVenueAndDir(String vers, int venue, int dir, long limit, long offset);
}
