package eu.eahub.repositories.core;

import eu.eahub.entities.core.GameConnectionEntity;
import eu.eahub.entities.core.PersonaConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GameConnectionRepository extends JpaRepository<GameConnectionEntity, Long> {

    // --- From server ---

    /**
     * Get active game reports where players are disconnected
     *
     * @param addresses IP addresses of currently connected players
     * @return list of game reports to stop
     */
    List<GameConnectionEntity> findByEndTimeIsNullAndPersonaConnectionAddressNotIn(Collection<String> addresses);

    @Query("""
                SELECT pc.address
                FROM GameConnectionEntity gc
                JOIN gc.personaConnection pc
                WHERE gc.game.id = :gameId
                AND gc.isHost = true
                AND pc.endTime IS NULL
            """)
    List<String> findHostAddressByGameId(@Param("gameId") Long gameId);

    Optional<GameConnectionEntity> findByPersonaConnectionIdAndEndTimeIsNull(Long personaConnectionId);

    List<GameConnectionEntity> findByGameIdAndEndTimeIsNull(Long gameId);

    @Query("""
                SELECT gc FROM GameConnectionEntity gc
                WHERE gc.personaConnection.persona.pers = :playerName
                AND DATE_TRUNC('SECOND', CAST(gc.game.startTime AS timestamp)) = DATE_TRUNC('SECOND', CAST(:startTime AS timestamp))
                AND gc.id NOT IN (
                    SELECT gr.gameConnection.id
                    FROM MohhGameReportEntity gr
                    WHERE gr.gameConnection.id = gc.id
                )
                AND (:includeHosts = true OR gc.isHost = false)
            """)
    List<GameConnectionEntity> findMatchingGameConnections(
            @Param("playerName") String playerName,
            @Param("startTime") LocalDateTime startTime,
            @Param("includeHosts") boolean includeHosts
    );

    @Transactional
    @Modifying
    @Query("""
                UPDATE GameConnectionEntity gc
                SET gc.endTime = :endTime
                WHERE gc.endTime IS NULL
            """)
    int setEndTimeForAllUnfinishedGameConnections(@Param("endTime") LocalDateTime endTime);

    @Query("""
                SELECT COUNT(DISTINCT gc.personaConnection.id)
                FROM GameConnectionEntity gc
                WHERE gc.endTime IS NULL
                AND gc.personaConnection.isHost = false
                AND gc.game.vers = :name
            """)
    int countPlayersInGame(String name);

    // --- From bot ---

    @Query("SELECT COUNT(gc) FROM GameConnectionEntity gc WHERE gc.personaConnection.isHost = false AND gc.endTime IS NULL")
    int countAllPlayersInGame();

    @Query("SELECT gc FROM GameConnectionEntity gc WHERE gc.personaConnection.isHost = false AND gc.startTime BETWEEN :start AND :end AND (gc.endTime IS NULL OR gc.endTime <> gc.game.endTime) AND gc.personaConnection.vers IN :vers ORDER BY gc.startTime")
    List<GameConnectionEntity> findMohhPlayerJoins(LocalDateTime start, LocalDateTime end, List<String> vers);

    @Query("SELECT gc FROM GameConnectionEntity gc WHERE gc.personaConnection.isHost = false AND gc.startTime BETWEEN :start AND :end AND gc.endTime IS NULL AND gc.personaConnection.vers NOT IN :vers ORDER BY gc.startTime")
    List<GameConnectionEntity> findNotMohhPlayerJoins(LocalDateTime start, LocalDateTime end, List<String> vers);

    @Query("SELECT gc FROM GameConnectionEntity gc WHERE gc.personaConnection.isHost = false AND gc.endTime BETWEEN :start AND :end AND (gc.game.endTime IS NULL OR gc.endTime <> gc.game.endTime) AND gc.personaConnection.vers IN :vers ORDER BY gc.endTime")
    List<GameConnectionEntity> findMohhPlayerLeaves(LocalDateTime start, LocalDateTime end, List<String> vers);

    @Query("SELECT gc FROM GameConnectionEntity gc WHERE gc.personaConnection.isHost = false AND gc.endTime BETWEEN :start AND :end AND gc.personaConnection.vers NOT IN :vers ORDER BY gc.endTime")
    List<GameConnectionEntity> findNotMohhPlayerLeaves(LocalDateTime start, LocalDateTime end, List<String> vers);

    GameConnectionEntity findFirstByPersonaConnectionAndEndTimeOrderByEndTimeDesc(PersonaConnectionEntity personaConnection, LocalDateTime endTime);

}
