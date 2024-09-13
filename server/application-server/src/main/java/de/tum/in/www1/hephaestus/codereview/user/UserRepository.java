package de.tum.in.www1.hephaestus.codereview.user;

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
}
