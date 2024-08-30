package de.tum.in.www1.hephaestus.codereview.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u WHERE u.login = :login")
    Optional<User> findUser(@Param("login") String login);

    @Query("""
                SELECT new de.tum.in.www1.hephaestus.codereview.user.UserDTO(u.login, u.email, u.name, u.url)
                FROM User u
                WHERE u.login = :login
            """)
    Optional<UserDTO> findByLogin(@Param("login") String login);

    // @Query("""
    // SELECT new de.tum.in.www1.hephaestus.codereview.user.UserDTO(u.login,
    // u.email, u.name, u.url, u.pullRequests, u.comments)
    // FROM User u
    // WHERE u.login = :login
    // """)
    // Optional<UserDTO> findByLoginWithPullRequestsAndComments(@Param("login")
    // String login);
}
