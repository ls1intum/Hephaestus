package de.tum.in.www1.hephaestus.gitprovider.user;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.hephaestus.gitprovider.user.dto.UserInfoDTO;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("""
                SELECT new UserInfoDTO(u.id, u.login, u.avatarUrl, u.name, u.htmlUrl, u.createdAt, u.updatedAt)
                FROM User u
                WHERE u.login = :login
            """)
    Optional<UserInfoDTO> findByLogin(@Param("login") String login);

    @Query("""
                SELECT u
                FROM User u
                JOIN FETCH u.reviews re
                WHERE re.createdAt BETWEEN :after AND :before
                AND (:repository IS NULL OR re.pullRequest.repository.nameWithOwner = :repository)
            """)
    List<User> findAllInTimeframe(@Param("after") OffsetDateTime after, @Param("before") OffsetDateTime before,
            @Param("repository") Optional<String> repository);    
}