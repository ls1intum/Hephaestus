package de.tum.in.www1.hephaestus.codereview.actor;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GHUserRepository extends JpaRepository<GHUser, Long> {

    @Query("SELECT a FROM Actor a WHERE a.login = ?1")
    GHUser findByLogin(String login);
}
