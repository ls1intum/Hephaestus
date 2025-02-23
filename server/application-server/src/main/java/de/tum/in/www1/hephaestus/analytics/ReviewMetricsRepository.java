package de.tum.in.www1.hephaestus.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReviewMetricsRepository extends JpaRepository<ReviewMetrics, Long> {
    List<ReviewMetrics> findByPullRequestId(Long pullRequestId);
    
    List<ReviewMetrics> findByReviewerId(Long reviewerId);

    @Query("SELECT rm FROM ReviewMetrics rm WHERE rm.pullRequest.repository.team.id = :teamId " +
           "AND rm.createdAt BETWEEN :startDate AND :endDate")
    List<ReviewMetrics> findByTeamAndDateRange(
        @Param("teamId") Long teamId,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );
}