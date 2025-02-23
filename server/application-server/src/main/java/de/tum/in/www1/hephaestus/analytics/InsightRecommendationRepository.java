package de.tum.in.www1.hephaestus.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface InsightRecommendationRepository extends JpaRepository<InsightRecommendation, Long> {
    List<InsightRecommendation> findByTeamIdOrderByPriorityLevelDesc(Long teamId);

    @Query("SELECT ir FROM InsightRecommendation ir WHERE ir.team.id = :teamId " +
           "AND ir.createdAt >= :since ORDER BY ir.priorityLevel DESC")
    List<InsightRecommendation> findRecentByTeam(
        @Param("teamId") Long teamId,
        @Param("since") Instant since
    );
}