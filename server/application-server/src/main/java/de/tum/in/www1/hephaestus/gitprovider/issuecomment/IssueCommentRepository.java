package de.tum.in.www1.hephaestus.gitprovider.issuecomment;

import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueCommentRepository extends JpaRepository<IssueComment, Long> {
    @Query(
        """
        SELECT ic
        FROM IssueComment ic
        LEFT JOIN FETCH ic.author
        LEFT JOIN FETCH ic.issue
        LEFT JOIN FETCH ic.issue.repository
        WHERE
            ic.author.login ILIKE :authorLogin AND ic.createdAt >= :activitySince
            AND (:onlyFromPullRequests = false OR ic.issue.htmlUrl LIKE '%/pull/%')
        ORDER BY ic.createdAt DESC
        """
    )
    List<IssueComment> findAllByAuthorLoginSince(
        @Param("authorLogin") String authorLogin,
        @Param("activitySince") OffsetDateTime activitySince,
        @Param("onlyFromPullRequests") boolean onlyFromPullRequests
    );

    @Query(
        """
        SELECT ic
        FROM IssueComment ic
        LEFT JOIN FETCH ic.author
        LEFT JOIN FETCH ic.issue
        LEFT JOIN FETCH ic.issue.repository
        WHERE
            ic.createdAt BETWEEN :after AND :before
            AND ic.author.type = 'USER'
            AND (:onlyFromPullRequests = false OR ic.issue.htmlUrl LIKE '%/pull/%')
        ORDER BY ic.createdAt DESC
        """
    )
    List<IssueComment> findAllInTimeframe(
        @Param("after") OffsetDateTime after,
        @Param("before") OffsetDateTime before,
        @Param("onlyFromPullRequests") boolean onlyFromPullRequests
    );

    @Query(
        """
        SELECT ic
        FROM IssueComment ic
        LEFT JOIN FETCH ic.author
        LEFT JOIN FETCH ic.issue
        LEFT JOIN FETCH ic.issue.repository
        JOIN Team t ON ic.issue.repository MEMBER OF t.repositories
        WHERE
            ic.createdAt BETWEEN :after AND :before
            AND ic.author.type = 'USER'
            AND t.id = :teamId
            AND (
                NOT EXISTS (
                    SELECT l
                    FROM t.labels l
                    WHERE l.repository = ic.issue.repository
                )
                OR
                EXISTS (
                    SELECT l
                    FROM t.labels l
                    WHERE l.repository = ic.issue.repository
                    AND l MEMBER OF ic.issue.labels
                )
            )
            AND (:onlyFromPullRequests = false OR ic.issue.htmlUrl LIKE '%/pull/%')
        ORDER BY ic.createdAt DESC
        """
    )
    List<IssueComment> findAllInTimeframeOfTeam(
        @Param("after") OffsetDateTime after,
        @Param("before") OffsetDateTime before,
        @Param("teamId") Long teamId,
        @Param("onlyFromPullRequests") boolean onlyFromPullRequests
    );
}
