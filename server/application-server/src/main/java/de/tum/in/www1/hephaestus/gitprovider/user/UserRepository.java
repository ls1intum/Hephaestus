package de.tum.in.www1.hephaestus.gitprovider.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("""
                SELECT u
                FROM User u
                WHERE u.login ILIKE :login
            """)
    Optional<User> findByLogin(@Param("login") String login);

    @Query("""
                SELECT u
                FROM User u
                JOIN FETCH u.teams
                WHERE u.type = 'USER'
            """)
    List<User> findAllWithEagerTeams();
}