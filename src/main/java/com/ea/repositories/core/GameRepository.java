package com.ea.repositories.core;

import com.ea.entities.core.GameEntity;
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
public interface GameRepository extends JpaRepository<GameEntity, Long> {

    Optional<GameEntity> findById(Long id);

    /**
     * Get active games where host are disconnected
     *
     * @param addresses IP addresses of currently connected players/hosts
     * @return list of games to stop
     */
    List<GameEntity> findByEndTimeIsNullAndGameConnectionsIsHostIsTrueAndGameConnectionsPersonaConnectionAddressNotIn(Collection<String> addresses);

    @Query("SELECT g FROM GameEntity g JOIN g.gameConnections gc JOIN gc.personaConnection pc WHERE pc.id = :personaConnectionId AND gc.endTime IS NULL")
    List<GameEntity> findCurrentGameOfPersona(long personaConnectionId);

    List<GameEntity> findByEndTimeIsNull();

    List<GameEntity> findByVersInAndEndTimeIsNull(List<String> vers);

    Optional<GameEntity> findByNameAndVersInAndEndTimeIsNull(String name, List<String> vers);

    boolean existsByNameAndVersInAndEndTimeIsNull(String name, List<String> vers);

    @Transactional
    @Modifying
    @Query("UPDATE GameEntity g SET g.endTime = :endTime WHERE g.endTime IS NULL")
    int setEndTimeForAllUnfinishedGames(@Param("endTime") LocalDateTime endTime);
}
