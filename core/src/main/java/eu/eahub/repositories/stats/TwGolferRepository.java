package eu.eahub.repositories.stats;

import eu.eahub.entities.stats.TwGolferEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TwGolferRepository extends JpaRepository<TwGolferEntity, Long> {

    Optional<TwGolferEntity> findByPersonaIdAndVers(Long personaId, String vers);

}
