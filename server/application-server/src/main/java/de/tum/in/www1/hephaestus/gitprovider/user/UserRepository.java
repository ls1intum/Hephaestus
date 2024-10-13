package de.tum.in.www1.hephaestus.gitprovider.user;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u WHERE u.login = :login")
    Optional<User> findUser(@Param("login") String login);

    @Query("""
                SELECT u
                FROM User u
                JOIN FETCH u.createdIssues i
                JOIN FETCH u.issueComments
                JOIN FETCH u.reviewComments
                JOIN FETCH u.reviews
                WHERE u.login = :login AND TYPE(i) = PullRequest
            """)
    Optional<User> findUserEagerly(@Param("login") String login);

    @Query("""
                SELECT new UserDTO(u.id, u.login, u.email, u.name, u.htmlUrl)
                FROM User u
                WHERE u.login = :login
            """)
    Optional<UserDTO> findByLogin(@Param("login") String login);

    @Query("""
                SELECT u
                FROM User u
                JOIN FETCH u.createdIssues i
                JOIN FETCH u.issueComments
                JOIN FETCH u.reviewComments
                JOIN FETCH u.reviews
                WHERE TYPE(i) = PullRequest
            """)
    List<User> findAllWithRelations();

    @Query("""
                SELECT u
                FROM User u
                JOIN FETCH u.reviews re
                WHERE re.submittedAt BETWEEN :after AND :before
            """)
    List<User> findAllInTimeframe(@Param("after") OffsetDateTime after, @Param("before") OffsetDateTime before);
}
