package eu.eahub.repositories.core;

import eu.eahub.entities.core.UserSetEntity;
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
public interface UserSetRepository extends JpaRepository<UserSetEntity, Long> {

    Optional<UserSetEntity> findById(Long id);

    List<UserSetEntity> findByVersAndEndTimeIsNull(String vers);

    Optional<UserSetEntity> findByNameAndVersAndEndTimeIsNull(String name, String vers);

    boolean existsByNameAndVersAndEndTimeIsNull(String name, String vers);

    @Transactional
    @Modifying
    @Query("UPDATE UserSetEntity us SET us.endTime = :endTime WHERE us.endTime IS NULL")
    int setEndTimeForAllUnfinishedUserSets(@Param("endTime") LocalDateTime endTime);

    @Query("""
             SELECT us FROM UserSetEntity us
             WHERE us.endTime IS NULL
             AND us.owner IS NOT NULL
             AND NOT EXISTS (
                 SELECT pc FROM PersonaConnectionEntity pc
                 WHERE pc.persona.id = us.owner.id
                 AND pc.endTime IS NULL
                 AND pc.address IN :addresses
             )
            """)
    List<UserSetEntity> findByEndTimeIsNullAndOwnerNotConnected(@Param("addresses") Collection<String> addresses);

}
