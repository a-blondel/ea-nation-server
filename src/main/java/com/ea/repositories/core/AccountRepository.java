package com.ea.repositories.core;

import com.ea.entities.core.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    @Query("SELECT a FROM AccountEntity a WHERE LOWER(a.name) = LOWER(:name)")
    Optional<AccountEntity> findByName(@Param("name") String name);

    @Query("SELECT a FROM AccountEntity a WHERE LOWER(a.mail) = LOWER(:mail)")
    List<AccountEntity> findByMail(@Param("mail") String mail);

}
