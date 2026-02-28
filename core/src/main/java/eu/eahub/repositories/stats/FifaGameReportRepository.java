package eu.eahub.repositories.stats;

import eu.eahub.entities.stats.FifaGameReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FifaGameReportRepository extends JpaRepository<FifaGameReportEntity, Long> {
}
