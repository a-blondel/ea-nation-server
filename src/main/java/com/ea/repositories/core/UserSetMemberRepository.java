package com.ea.repositories.core;

import com.ea.entities.core.UserSetMemberEntity;
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
public interface UserSetMemberRepository extends JpaRepository<UserSetMemberEntity, Long> {

    @Query("SELECT m FROM UserSetMemberEntity m JOIN FETCH m.persona WHERE m.userSet.id = :userSetId AND m.endTime IS NULL")
    List<UserSetMemberEntity> findByUserSetIdAndEndTimeIsNull(@Param("userSetId") Long userSetId);

    Optional<UserSetMemberEntity> findByPersonaIdAndEndTimeIsNull(Long personaId);

    Optional<UserSetMemberEntity> findByUserSetIdAndPersonaIdAndEndTimeIsNull(Long userSetId, Long personaId);

    int countByUserSetIdAndEndTimeIsNull(Long userSetId);

    @Query("SELECT COALESCE(MAX(m.slot), 0) FROM UserSetMemberEntity m WHERE m.userSet.id = :userSetId AND m.endTime IS NULL")
    int findMaxSlotByUserSetId(@Param("userSetId") Long userSetId);

    @Transactional
    @Modifying
    @Query("UPDATE UserSetMemberEntity m SET m.endTime = :endTime WHERE m.endTime IS NULL")
    int setEndTimeForAllUnfinishedMembers(@Param("endTime") LocalDateTime endTime);

    @Query("""
             SELECT m FROM UserSetMemberEntity m
             WHERE m.endTime IS NULL
             AND NOT EXISTS (
                 SELECT pc FROM PersonaConnectionEntity pc
                 WHERE pc.persona.id = m.persona.id
                 AND pc.endTime IS NULL
                 AND pc.address IN :addresses
             )
            """)
    List<UserSetMemberEntity> findByEndTimeIsNullAndPersonaNotConnected(@Param("addresses") Collection<String> addresses);

}
