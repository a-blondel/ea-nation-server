package eu.eahub.repositories.stats;

import eu.eahub.entities.stats.TwEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TwEventRepository extends JpaRepository<TwEventEntity, Long> {

    Optional<TwEventEntity> findByVersAndEventDay(String vers, int eventDay);

    List<TwEventEntity> findByVersAndEventDayBetweenOrderByEventDay(String vers, int first, int last);

    List<TwEventEntity> findByVersAndEventDayLessThanAndSettledOnIsNullOrderByEventDay(String vers, int day);

}
