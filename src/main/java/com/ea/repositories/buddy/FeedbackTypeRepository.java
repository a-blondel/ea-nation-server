package com.ea.repositories.buddy;

import com.ea.entities.social.FeedbackTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface FeedbackTypeRepository extends JpaRepository<FeedbackTypeEntity, Long> {

    @Query("SELECT ft FROM FeedbackTypeEntity ft WHERE ft.number = :number")
    Optional<FeedbackTypeEntity> findByNumber(@Param("number") BigDecimal number);
}
