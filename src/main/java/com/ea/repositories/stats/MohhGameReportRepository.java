package com.ea.repositories.stats;

import com.ea.entities.stats.MohhGameReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MohhGameReportRepository extends JpaRepository<MohhGameReportEntity, Long> {

}