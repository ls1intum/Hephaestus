package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

    Set<PullRequest> findByAuthor_Login(String authorLogin);

    @Query("""
            SELECT MIN(p.createdAt)
            FROM PullRequest p
            WHERE p.author.login = :authorLogin
            """)
    Optional<OffsetDateTime> firstContributionByAuthorLogin(@Param("authorLogin") String authorLogin);
    
    @Query("""
            SELECT p
            FROM PullRequest p
            JOIN FETCH p.labels
            JOIN FETCH p.author
            JOIN FETCH p.assignees
            JOIN FETCH p.repository
            WHERE (p.author.login = :assigneeLogin OR :assigneeLogin IN (SELECT u.login FROM p.assignees u)) AND p.state IN :states
            ORDER BY p.createdAt DESC
            """)
    List<PullRequest> findAssignedByLoginAndStates(
            @Param("assigneeLogin") String assigneeLogin,
            @Param("states") Set<PullRequest.State> states);
}