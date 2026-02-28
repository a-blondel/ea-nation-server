package eu.eahub.repositories.stats;

import eu.eahub.entities.stats.NhlGameReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NhlGameReportRepository extends JpaRepository<NhlGameReportEntity, Long> {

    /**
     * Check if a game report exists for the given game connection ID
     *
     * @param gameConnectionId The game connection ID
     * @return true if exists, false otherwise
     */
    boolean existsByGameConnectionId(Long gameConnectionId);

}
