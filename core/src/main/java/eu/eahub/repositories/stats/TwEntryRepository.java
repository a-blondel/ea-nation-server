package eu.eahub.repositories.stats;

import eu.eahub.entities.stats.TwEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TwEntryRepository extends JpaRepository<TwEntryEntity, Long> {

    Optional<TwEntryEntity> findByTokenAndPersonaId(String token, Long personaId);

    boolean existsByPersonaIdAndEventId(Long personaId, Long eventId);

    List<TwEntryEntity> findByPersonaIdAndEventIdOrderByStartTimeAscIdAsc(Long personaId, Long eventId);

    boolean existsByEventVersAndEventEventDayBetween(String vers, int first, int last);

    boolean existsByPersonaIdAndCountedTrueAndEventVersAndEventEventDayBetween(Long personaId, String vers, int first, int last);

    @Query("""
            FROM TwEntryEntity e JOIN FETCH e.persona JOIN FETCH e.event ev
            WHERE ev.vers = :vers AND ev.eventDay BETWEEN :first AND :last AND e.counted = true
            ORDER BY e.endTime, e.id
            """)
    List<TwEntryEntity> findCounted(String vers, int first, int last);

    @Query("""
            FROM TwEntryEntity e JOIN FETCH e.persona JOIN FETCH e.event ev
            WHERE ev.vers = :vers AND ev.course = :course AND e.counted = true
            """)
    List<TwEntryEntity> findCountedByCourse(String vers, int course);

    @Query("""
            FROM TwEntryEntity e JOIN FETCH e.persona JOIN FETCH e.event ev
            WHERE ev.vers = :vers AND ev.eventDay BETWEEN :first AND :last AND e.prize IS NOT NULL
            ORDER BY ev.eventDay, e.finalRank
            """)
    List<TwEntryEntity> findPrizes(String vers, int first, int last);

    @Query("""
            FROM TwEntryEntity e JOIN FETCH e.persona JOIN FETCH e.event ev
            WHERE ev.vers = :vers AND e.endTime IS NOT NULL
            ORDER BY e.endTime, e.id
            """)
    List<TwEntryEntity> findReported(String vers);

}
