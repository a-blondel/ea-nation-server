package com.ea.repositories.core;

import com.ea.entities.core.BlacklistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlacklistRepository extends JpaRepository<BlacklistEntity, Long> {

    boolean existsByIp(String ip);

}
