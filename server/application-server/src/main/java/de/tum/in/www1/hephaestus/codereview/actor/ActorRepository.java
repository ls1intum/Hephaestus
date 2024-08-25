package de.tum.in.www1.hephaestus.codereview.actor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ActorRepository extends JpaRepository<Actor, Long> {

    @Query("SELECT a FROM Actor a WHERE a.login = ?1")
    Actor findByLogin(String login);
}
