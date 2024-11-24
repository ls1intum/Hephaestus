package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestReviewRepository extends JpaRepository<PullRequestReview, Long> {
    @Query(
        """
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
        @Param("activitySince") OffsetDateTime activitySince
    );

    @Query(
        """
        SELECT prr
        FROM PullRequestReview prr
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.author.teams
        LEFT JOIN FETCH prr.pullRequest
        LEFT JOIN FETCH prr.pullRequest.repository
        LEFT JOIN FETCH prr.comments
        WHERE
            prr.submittedAt BETWEEN :after AND :before
            AND prr.author.type = 'USER'
        ORDER BY prr.submittedAt DESC
        """
    )
    List<PullRequestReview> findAllInTimeframe(
        @Param("after") OffsetDateTime after,
        @Param("before") OffsetDateTime before
    );

    @Query(
        """
        SELECT prr
        FROM PullRequestReview prr
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.author.teams
        LEFT JOIN FETCH prr.pullRequest
        LEFT JOIN FETCH prr.pullRequest.repository
        LEFT JOIN FETCH prr.comments
        JOIN Team t ON prr.pullRequest.repository MEMBER OF t.repositories
        WHERE
            prr.submittedAt BETWEEN :after AND :before
            AND prr.author.type = 'USER'
            AND t.id = :teamId
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
        ORDER BY prr.submittedAt DESC
        """
    )
    List<PullRequestReview> findAllInTimeframeOfTeam(
        @Param("after") OffsetDateTime after,
        @Param("before") OffsetDateTime before,
        @Param("teamId") Long teamId
    );
}
