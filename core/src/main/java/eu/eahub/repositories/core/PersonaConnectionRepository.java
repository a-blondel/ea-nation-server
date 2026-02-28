package eu.eahub.repositories.core;

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

@Repository
public interface PersonaConnectionRepository extends JpaRepository<PersonaConnectionEntity, Long> {

    // --- From server ---

    /**
     * Get active persona connections where players are disconnected
     *
     * @param addresses IP addresses of currently connected players
     * @return list of persona connections to stop
     */
    List<PersonaConnectionEntity> findByEndTimeIsNullAndAddressNotIn(Collection<String> addresses);

    List<PersonaConnectionEntity> findByVersAndPersonaPersAndIsHostFalseAndEndTimeIsNull(
            String vers,
            String pers
    );

    @Transactional
    @Modifying
    @Query("""
                UPDATE PersonaConnectionEntity pc
                SET pc.endTime = :endTime
                WHERE pc.endTime IS NULL
            """)
    int setEndTimeForAllUnfinishedPersonaConnections(@Param("endTime") LocalDateTime endTime);

    @Query("""
                SELECT COUNT(pc)
                FROM PersonaConnectionEntity pc
                WHERE pc.endTime IS NULL
                AND pc.isHost = false
                AND pc.vers = :name
                AND pc.id NOT IN (
                    SELECT gc.personaConnection.id
                    FROM GameConnectionEntity gc
                    WHERE gc.endTime IS NULL
                )
            """)
    int countPlayersInLobby(String name);

    // --- From bot ---

    @Query("SELECT COUNT(pc) FROM PersonaConnectionEntity pc WHERE pc.isHost = false AND pc.endTime IS NULL")
    int countPlayersOnline();

    @Query("SELECT pc FROM PersonaConnectionEntity pc WHERE pc.isHost = false AND pc.startTime BETWEEN :start AND :end ORDER BY pc.startTime")
    List<PersonaConnectionEntity> findPersonaLogins(LocalDateTime start, LocalDateTime end);

    @Query("SELECT pc FROM PersonaConnectionEntity pc WHERE pc.isHost = false AND pc.endTime BETWEEN :start AND :end ORDER BY pc.endTime")
    List<PersonaConnectionEntity> findPersonaLogouts(LocalDateTime start, LocalDateTime end);

    /**
     * Find players currently connected in lobby (not in game) for specific VERS codes.
     * These are players connected but not participating in any active game.
     *
     * @param versCodesList list of client VERS codes to filter by
     * @return list of persona connections for players in lobby
     */
    @Query("SELECT pc FROM PersonaConnectionEntity pc WHERE pc.isHost = false AND pc.endTime IS NULL " +
            "AND pc.vers IN :versCodesList " +
            "AND NOT EXISTS (SELECT gc FROM GameConnectionEntity gc WHERE gc.personaConnection = pc AND gc.endTime IS NULL)")
    List<PersonaConnectionEntity> findPlayersInLobbyByVers(@Param("versCodesList") List<String> versCodesList);
}
