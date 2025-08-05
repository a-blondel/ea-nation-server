package com.ea.repositories.buddy;

import com.ea.entities.core.PersonaEntity;
import com.ea.entities.social.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    @Query("SELECT m FROM MessageEntity m WHERE m.toPersona = :persona AND m.ack = false ORDER BY m.createdOn ASC")
    List<MessageEntity> findUnacknowledgedMessagesByToPersona(@Param("persona") PersonaEntity persona);

    @Query("SELECT m FROM MessageEntity m WHERE (m.fromPersona = :persona1 AND m.toPersona = :persona2) OR (m.fromPersona = :persona2 AND m.toPersona = :persona1) ORDER BY m.createdOn ASC")
    List<MessageEntity> findConversationBetweenPersonas(@Param("persona1") PersonaEntity persona1, @Param("persona2") PersonaEntity persona2);

    @Modifying
    @Transactional
    @Query("UPDATE MessageEntity m SET m.ack = true WHERE m.id IN :messageIds")
    int markMessagesAsAcknowledgedByIds(@Param("messageIds") List<Long> messageIds);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO social.MESSAGE (FROM_PERSONA_ID, TO_PERSONA_ID, BODY, ACK, CREATED_ON) " +
            "VALUES (:fromPersonaId, :toPersonaId, :body, :ack, :createdOn)", nativeQuery = true)
    void saveMessageByPersonaIds(@Param("fromPersonaId") Long fromPersonaId,
                                 @Param("toPersonaId") Long toPersonaId,
                                 @Param("body") String body,
                                 @Param("ack") Boolean ack,
                                 @Param("createdOn") LocalDateTime createdOn);
}
