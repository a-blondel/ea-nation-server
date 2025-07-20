package com.ea.repositories.core;

import com.ea.entities.core.GameConnectionEntity;
import com.ea.frontend.DTO;
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
                AND gc.game.vers IN ( :vers )
            """)
    int countPlayersInGame(List<String> vers);

    @Query("""
                SELECT new com.ea.frontend.DTO$PlayerInfoDTO(
                    gc.personaConnection.persona.pers,
                    gc.isHost,
                    gc.startTime
                )
                FROM GameConnectionEntity gc
                WHERE gc.game.id = :gameId
                AND gc.endTime IS NULL
                AND gc.isHost = false
            """)
    List<DTO.PlayerInfoDTO> findActivePlayersByGameId(@Param("gameId") Long gameId);

    @Query("""
                SELECT new com.ea.frontend.DTO$GameStatusDTO(
                    g.id,
                    g.name,
                    g.vers,
                    g.params,
                    g.pass,
                    g.startTime,
                    g.maxsize,
                    h.personaConnection.persona.pers,
                    COUNT(p)
                )
                FROM GameEntity g
                LEFT JOIN GameConnectionEntity h ON h.game = g AND h.isHost = true AND h.endTime IS NULL
                LEFT JOIN GameConnectionEntity p ON p.game = g AND p.isHost = false AND p.endTime IS NULL
                WHERE g.endTime IS NULL
                GROUP BY g.id, g.name, g.vers, g.startTime, g.maxsize, h.personaConnection.persona.pers
            """)
    List<DTO.GameStatusDTO> findAllActiveGamesWithStats();

}