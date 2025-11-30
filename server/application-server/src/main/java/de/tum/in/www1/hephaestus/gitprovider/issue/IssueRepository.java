package de.tum.in.www1.hephaestus.gitprovider.issue;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Finds the lowest issue number that has been synced (has lastSyncAt set).
     * Used to determine where backfill should start - just below this number.
     */
    @Query(
        """
        SELECT MIN(i.number)
        FROM Issue i
        WHERE i.repository.id = :repositoryId
        AND i.lastSyncAt IS NOT NULL
        """
    )
    Optional<Integer> findLowestSyncedIssueNumber(@Param("repositoryId") long repositoryId);
}
