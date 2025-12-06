package de.tum.in.www1.hephaestus.gitprovider.issuecomment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
            ic.author.login ILIKE :authorLogin
            AND ic.createdAt >= :activitySince
            AND ic.issue.repository.organization.workspace.id = :workspaceId
            AND (:onlyFromPullRequests = false OR ic.issue.htmlUrl LIKE '%/pull/%')
        ORDER BY ic.createdAt DESC
        """
    )
    List<IssueComment> findAllByAuthorLoginSince(
        @Param("authorLogin") String authorLogin,
        @Param("activitySince") Instant activitySince,
        @Param("onlyFromPullRequests") boolean onlyFromPullRequests,
        @Param("workspaceId") Long workspaceId
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
            AND ic.issue.repository.organization.workspace.id = :workspaceId
            AND (:onlyFromPullRequests = false OR ic.issue.htmlUrl LIKE '%/pull/%')
        ORDER BY ic.createdAt DESC
        """
    )
    List<IssueComment> findAllInTimeframe(
        @Param("after") Instant after,
        @Param("before") Instant before,
        @Param("onlyFromPullRequests") boolean onlyFromPullRequests,
        @Param("workspaceId") Long workspaceId
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
            AND ic.issue.repository.organization.workspace.id = :workspaceId
            AND EXISTS (
                SELECT 1
                FROM TeamRepositoryPermission trp
                JOIN trp.team t
                WHERE trp.repository = ic.issue.repository
                AND t.id IN :teamIds
                AND trp.hiddenFromContributions = false
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
            )
            AND EXISTS (
                SELECT 1
                FROM TeamMembership tm
                WHERE tm.team.id IN :teamIds
                AND tm.user = ic.author
            )
            AND (:onlyFromPullRequests = false OR ic.issue.htmlUrl LIKE '%/pull/%')
        ORDER BY ic.createdAt DESC
        """
    )
    List<IssueComment> findAllInTimeframeOfTeams(
        @Param("after") Instant after,
        @Param("before") Instant before,
        @Param("teamIds") Collection<Long> teamIds,
        @Param("onlyFromPullRequests") boolean onlyFromPullRequests,
        @Param("workspaceId") Long workspaceId
    );
}
