package de.tum.in.www1.hephaestus.codereview.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GHUserRepository extends JpaRepository<GHUser, Long> {

    Optional<GHUser> findByLogin(String login);

    @Query("""
                SELECT new de.tum.in.www1.hephaestus.codereview.actor.GHUserDTO(u.login, u.email, u.name, u.url)
                FROM GHUser u
                WHERE u.login = :login
            """)
    Optional<GHUserDTO> findUserDTO(@Param("login") String login);
}
