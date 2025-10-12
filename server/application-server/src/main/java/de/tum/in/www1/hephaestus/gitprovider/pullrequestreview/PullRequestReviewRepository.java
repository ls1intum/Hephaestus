package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestReviewRepository extends JpaRepository<PullRequestReview, Long> {
    @Query(
        value = """
        SELECT prr
        FROM PullRequestReview prr
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.pullRequest
        LEFT JOIN FETCH prr.pullRequest.repository
        LEFT JOIN FETCH prr.comments
        WHERE prr.author.login ILIKE :authorLogin AND prr.submittedAt >= :activitySince
        ORDER BY prr.submittedAt DESC
        """
    )
    List<PullRequestReview> findAllByAuthorLoginSince(
        @Param("authorLogin") String authorLogin,
        @Param("activitySince") Instant activitySince
    );

    @Query(
        value = """
        SELECT prr
        FROM PullRequestReview prr
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.pullRequest
        LEFT JOIN FETCH prr.pullRequest.repository
        LEFT JOIN FETCH prr.comments
        WHERE
            prr.submittedAt BETWEEN :after AND :before
            AND prr.author.type = 'USER'
        ORDER BY prr.submittedAt DESC
        """
    )
    List<PullRequestReview> findAllInTimeframe(@Param("after") Instant after, @Param("before") Instant before);

    @Query(
        value = """
        SELECT prr
        FROM PullRequestReview prr
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.pullRequest
        LEFT JOIN FETCH prr.pullRequest.repository
        LEFT JOIN FETCH prr.comments
        WHERE
            prr.submittedAt BETWEEN :after AND :before
            AND prr.author.type = 'USER'
            AND EXISTS (
                SELECT 1
                FROM TeamRepositoryPermission trp
                JOIN trp.team t
                WHERE trp.repository = prr.pullRequest.repository
                AND t.id IN :teamIds
                AND trp.hiddenFromContributions = false
                AND (
                    NOT EXISTS (
                        SELECT l
                        FROM t.labels l
                        WHERE l.repository = prr.pullRequest.repository
                    )
                    OR
                    EXISTS (
                        SELECT l
                        FROM t.labels l
                        WHERE l.repository = prr.pullRequest.repository
                        AND l MEMBER OF prr.pullRequest.labels
                    )
                )
            )
        ORDER BY prr.submittedAt DESC
        """
    )
    List<PullRequestReview> findAllInTimeframeOfTeams(
        @Param("after") Instant after,
        @Param("before") Instant before,
        @Param("teamIds") Collection<Long> teamIds
    );
}
