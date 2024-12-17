package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {
    @Query(
        """
        SELECT MIN(p.createdAt)
        FROM PullRequest p
        WHERE p.author.login ILIKE :authorLogin
        """
    )
    Optional<OffsetDateTime> firstContributionByAuthorLogin(@Param("authorLogin") String authorLogin);

    @Query(
        """
        SELECT p
        FROM PullRequest p
        LEFT JOIN FETCH p.labels
        JOIN FETCH p.author
        LEFT JOIN FETCH p.assignees
        LEFT JOIN FETCH p.repository
        WHERE (p.author.login ILIKE :assigneeLogin OR LOWER(:assigneeLogin) IN (SELECT LOWER(u.login) FROM p.assignees u)) AND p.state IN :states
        ORDER BY p.createdAt DESC
        """
    )
    List<PullRequest> findAssignedByLoginAndStates(
        @Param("assigneeLogin") String assigneeLogin,
        @Param("states") Set<PullRequest.State> states
    );

    @Query(
        """
        SELECT p
        FROM PullRequest p
        LEFT JOIN FETCH p.labels
        JOIN FETCH p.author
        LEFT JOIN FETCH p.assignees
        LEFT JOIN FETCH p.repository
        WHERE p.repository.id = :repositoryId AND p.number = :number
        """
    )
    Optional<PullRequest> findByRepositoryIdAndNumber(
        @Param("repositoryId") long repositoryId,
        @Param("number") int number
    );

    @Query(
        """
        SELECT p.number
        FROM PullRequest p
        WHERE Type(p) = PullRequest
        AND p.repository.id = :repositoryId
        AND p.lastSyncAt IS NOT NULL
        """
    )
    Set<Integer> findAllSyncedPullRequestNumbers(@Param("repositoryId") long repositoryId);
}
