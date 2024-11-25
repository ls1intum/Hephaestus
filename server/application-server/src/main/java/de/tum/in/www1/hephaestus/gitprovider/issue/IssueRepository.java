package de.tum.in.www1.hephaestus.gitprovider.issue;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IssueRepository extends JpaRepository<Issue, Long> {
    @Query(
        """
        SELECT i.number
        FROM Issue i
        WHERE Type(i) = Issue
        AND i.repository.id = :repositoryId
        AND i.lastSyncAt IS NOT NULL
        """
    )
    Set<Integer> findAllSyncedIssueNumbers(@Param("repositoryId") long repositoryId);

    @Query(
        """
        SELECT i.number
        FROM Issue i
        WHERE Type(i) = Issue
        AND i.repository.id = :repositoryId
        AND i.hasPullRequest = TRUE
        """
    )
    Set<Integer> findAllIssueNumbersWithPullRequest(@Param("repositoryId") long repositoryId);

    @Query(
        """
        SELECT MAX(i.number)
        FROM Issue i
        WHERE i.repository.id = :repositoryId
        """
    )
    Optional<Integer> findLastIssueNumber(long repositoryId);

    @Query("""
            SELECT i
            FROM Issue i
            LEFT JOIN FETCH i.labels
            JOIN FETCH i.author
            LEFT JOIN FETCH i.assignees
            LEFT JOIN FETCH i.repository
            WHERE LOWER(:assigneeLogin) IN (SELECT LOWER(u.login) FROM i.assignees u) AND i.state IN :states AND TYPE(i) <> PullRequest
            ORDER BY i.createdAt DESC
            """)
    List<Issue> findAssignedByLoginAndStates(
            @Param("assigneeLogin") String assigneeLogin,
            @Param("states") Set<Issue.State> states);
}
