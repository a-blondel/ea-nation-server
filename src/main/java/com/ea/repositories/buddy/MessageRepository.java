package com.ea.repositories.buddy;

import com.ea.entities.core.PersonaEntity;
import com.ea.entities.social.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    @Query("SELECT m FROM MessageEntity m WHERE m.toPersona = :persona AND m.ack = false ORDER BY m.createdOn ASC")
    List<MessageEntity> findUnacknowledgedMessagesByToPersona(@Param("persona") PersonaEntity persona);

    @Query("SELECT m FROM MessageEntity m WHERE (m.fromPersona = :persona1 AND m.toPersona = :persona2) OR (m.fromPersona = :persona2 AND m.toPersona = :persona1) ORDER BY m.createdOn ASC")
    List<MessageEntity> findConversationBetweenPersonas(@Param("persona1") PersonaEntity persona1, @Param("persona2") PersonaEntity persona2);
}
