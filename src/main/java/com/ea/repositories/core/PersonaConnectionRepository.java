package com.ea.repositories.core;

import com.ea.entities.core.PersonaConnectionEntity;
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
public interface PersonaConnectionRepository extends JpaRepository<PersonaConnectionEntity, Long> {

    /**
     * Get active persona connections where players are disconnected
     *
     * @param addresses IP addresses of currently connected players
     * @return list of persona connections to stop
     */
    List<PersonaConnectionEntity> findByEndTimeIsNullAndAddressNotIn(Collection<String> addresses);

    Optional<PersonaConnectionEntity> findByVersAndSlusAndPersonaPersAndIsHostFalseAndEndTimeIsNull(
            String vers,
            String slus,
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
                AND pc.vers IN ( :vers )
                AND pc.id NOT IN (
                    SELECT gc.personaConnection.id
                    FROM GameConnectionEntity gc
                    WHERE gc.endTime IS NULL
                )
            """)
    int countPlayersInLobby(List<String> vers);

}