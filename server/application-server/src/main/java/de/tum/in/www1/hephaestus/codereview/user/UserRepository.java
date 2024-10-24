package de.tum.in.www1.hephaestus.codereview.user;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.hephaestus.codereview.team.Team;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u WHERE u.login = :login")
    Optional<User> findUser(@Param("login") String login);

    @Query("""
                SELECT u
                FROM User u
                JOIN FETCH u.pullRequests
                JOIN FETCH u.issueComments
                JOIN FETCH u.reviewComments
                JOIN FETCH u.reviews
                WHERE u.login = :login
            """)
    Optional<User> findUserEagerly(@Param("login") String login);

    @Query("""
                SELECT new UserDTO(u.id, u.login, u.email, u.name, u.url)
                FROM User u
                WHERE u.login = :login
            """)
    Optional<UserDTO> findByLogin(@Param("login") String login);

    @Query("""
                SELECT u
                FROM User u
                JOIN FETCH u.pullRequests
                JOIN FETCH u.issueComments
                JOIN FETCH u.reviewComments
                JOIN FETCH u.reviews
            """)
    List<User> findAllWithRelations();

    @Query("""
                SELECT u
                FROM User u
                JOIN FETCH u.reviews re
                WHERE re.createdAt BETWEEN :after AND :before
                AND (:team IS NULL OR :team MEMBER OF u.teams)
            """)
    List<User> findAllInTimeframe(@Param("after") OffsetDateTime after, @Param("before") OffsetDateTime before,
            @Param("team") Optional<Team> team);

    @Query("""
                SELECT u
                FROM User u
                JOIN FETCH u.teams
            """)
    List<User> findAllWithTeams();
}
