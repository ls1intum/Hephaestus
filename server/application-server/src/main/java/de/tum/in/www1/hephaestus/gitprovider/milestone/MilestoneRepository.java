package de.tum.in.www1.hephaestus.gitprovider.milestone;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for milestone entities.
 *
 * <p>All queries filter by repository ID which inherently carries scope
 * through the Repository -> Organization relationship chain.
 */
@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    Optional<Milestone> findByNativeIdAndProviderId(Long nativeId, Long providerId);

    List<Milestone> findAllByRepository_Id(Long repositoryId);

    @Query(
        """
        SELECT m
        FROM Milestone m
        WHERE m.number = :number
        AND m.repository.id = :repositoryId
        """
    )
    Optional<Milestone> findByNumberAndRepositoryId(
        @Param("number") Integer number,
        @Param("repositoryId") Long repositoryId
    );

    /**
     * Atomically inserts a milestone if absent (race-condition safe).
     * <p>
     * Uses PostgreSQL's ON CONFLICT DO NOTHING to handle concurrent inserts
     * on the unique constraint (number, repository_id). This eliminates the race
     * condition where two threads both check for existence and try to insert.
     *
     * @return 1 if inserted, 0 if duplicate (milestone with same repo+number exists)
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO milestone (
            native_id, provider_id, number, title, description, state, html_url, due_on,
            open_issues_count, closed_issues_count, repository_id, created_at, updated_at
        )
        VALUES (
            :nativeId, :providerId, :number, :title, :description, :state, :htmlUrl, :dueOn,
            :openIssuesCount, :closedIssuesCount, :repositoryId, :createdAt, :updatedAt
        )
        ON CONFLICT (number, repository_id) DO NOTHING
        """,
        nativeQuery = true
    )
    int insertIfAbsent(
        @Param("nativeId") Long nativeId,
        @Param("providerId") Long providerId,
        @Param("number") int number,
        @Param("title") String title,
        @Param("description") String description,
        @Param("state") String state,
        @Param("htmlUrl") String htmlUrl,
        @Param("dueOn") Instant dueOn,
        @Param("openIssuesCount") int openIssuesCount,
        @Param("closedIssuesCount") int closedIssuesCount,
        @Param("repositoryId") Long repositoryId,
        @Param("createdAt") Instant createdAt,
        @Param("updatedAt") Instant updatedAt
    );
}
