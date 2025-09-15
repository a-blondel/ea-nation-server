package com.ea.repositories.stats;

import com.ea.entities.stats.NfsGameReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NfsGameReportRepository extends JpaRepository<NfsGameReportEntity, Long> {
    boolean existsByGameConnectionId(Long gameConnectionId);
}
