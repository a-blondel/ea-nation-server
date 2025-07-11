package com.ea.repositories.buddy;

import com.ea.entities.core.PersonaEntity;
import com.ea.entities.social.FeedbackEntity;
import com.ea.entities.social.FeedbackTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<FeedbackEntity, Long> {

    @Query("SELECT f FROM FeedbackEntity f WHERE f.fromPersona = :fromPersona AND f.toPersona = :toPersona AND f.feedbackType = :feedbackType")
    Optional<FeedbackEntity> findByFromPersonaAndToPersonaAndFeedbackType(@Param("fromPersona") PersonaEntity fromPersona, @Param("toPersona") PersonaEntity toPersona, @Param("feedbackType") FeedbackTypeEntity feedbackType);
}
