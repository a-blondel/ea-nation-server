package eu.eahub.repositories.stats;

import eu.eahub.entities.stats.TwGameReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TwGameReportRepository extends JpaRepository<TwGameReportEntity, Long> {

    boolean existsByGameConnectionGameId(Long gameId);

    @Query("""
            FROM TwGameReportEntity r
            JOIN FETCH r.gameConnection gc JOIN FETCH gc.game g JOIN FETCH gc.personaConnection pc JOIN FETCH pc.persona
            WHERE g.vers = :vers AND g.startTime >= :since
            ORDER BY g.startTime, g.id, r.side
            """)
    List<TwGameReportEntity> findByVersSince(String vers, LocalDateTime since);

}
