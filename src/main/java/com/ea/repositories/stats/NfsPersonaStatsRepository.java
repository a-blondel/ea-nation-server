package com.ea.repositories.stats;

import com.ea.entities.stats.NfsPersonaStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NfsPersonaStatsRepository extends JpaRepository<NfsPersonaStatsEntity, Long> {
    NfsPersonaStatsEntity findByPersonaIdAndVers(Long personaId, String vers);
}
