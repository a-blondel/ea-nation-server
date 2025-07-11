package com.ea.repositories.buddy;

import com.ea.entities.core.PersonaEntity;
import com.ea.entities.social.BuddyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BuddyRepository extends JpaRepository<BuddyEntity, Long> {

    @Query("SELECT b FROM BuddyEntity b WHERE b.fromPersona = :fromPersona AND b.toPersona = :toPersona")
    BuddyEntity findByFromPersonaAndToPersona(@Param("fromPersona") PersonaEntity fromPersona, @Param("toPersona") PersonaEntity toPersona);

    @Query("SELECT b FROM BuddyEntity b WHERE b.fromPersona = :persona AND b.list = :list")
    List<BuddyEntity> findByFromPersonaAndList(@Param("persona") PersonaEntity persona, @Param("list") String list);

    @Modifying
    @Query("DELETE FROM BuddyEntity b WHERE b.fromPersona = :fromPersona AND b.toPersona = :toPersona AND b.list = :list")
    void deleteByFromPersonaAndToPersonaAndList(@Param("fromPersona") PersonaEntity fromPersona, @Param("toPersona") PersonaEntity toPersona, @Param("list") String list);

    @Query("SELECT b FROM BuddyEntity b WHERE (b.fromPersona = :persona OR b.toPersona = :persona) AND b.list = :list")
    List<BuddyEntity> findByPersonaInEitherDirectionAndList(@Param("persona") PersonaEntity persona, @Param("list") String list);
}
