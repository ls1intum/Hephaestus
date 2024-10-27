package de.tum.in.www1.hephaestus.gitprovider.issuecomment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueCommentRepository extends JpaRepository<IssueComment, Long> {

    @Query("""
            SELECT ic
            FROM IssueComment ic
            LEFT JOIN FETCH ic.author
            LEFT JOIN FETCH ic.issue
            LEFT JOIN FETCH ic.issue.repository
            WHERE 
                ic.author.login = :authorLogin AND ic.createdAt >= :activitySince
                AND (:onlyFromPullRequests = false OR ic.issue.htmlUrl LIKE '%/pull/%')
            ORDER BY ic.createdAt DESC
            """)
    List<IssueComment> findAllByAuthorLoginSince(
            @Param("authorLogin") String authorLogin,
            @Param("activitySince") OffsetDateTime activitySince,
            @Param("onlyFromPullRequests") boolean onlyFromPullRequests);

    @Query("""
        SELECT ic
        FROM IssueComment ic
        LEFT JOIN FETCH ic.author
        LEFT JOIN FETCH ic.issue
        LEFT JOIN FETCH ic.issue.repository
        WHERE 
            ic.createdAt BETWEEN :after AND :before
            AND (:repository IS NULL OR ic.issue.repository.nameWithOwner = :repository)
            AND ic.author.type = 'USER'
            AND (:onlyFromPullRequests = false OR ic.issue.htmlUrl LIKE '%/pull/%')
        ORDER BY ic.createdAt DESC
        """)
    List<IssueComment> findAllInTimeframe(
        @Param("after") OffsetDateTime after, 
        @Param("before") OffsetDateTime before, 
        @Param("repository") Optional<String> repository,
        @Param("onlyFromPullRequests") boolean onlyFromPullRequests);
}
