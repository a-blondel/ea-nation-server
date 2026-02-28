package eu.eahub.repositories;

import eu.eahub.entities.ParamEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParamRepository extends JpaRepository<ParamEntity, String> {
}
