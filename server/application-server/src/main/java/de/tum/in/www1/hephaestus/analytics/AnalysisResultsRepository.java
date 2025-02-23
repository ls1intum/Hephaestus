package de.tum.in.www1.hephaestus.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AnalysisResultsRepository extends JpaRepository<AnalysisResults, Long> {
    List<AnalysisResults> findByTeamId(Long teamId);

    @Query("SELECT ar FROM AnalysisResults ar WHERE ar.team.id = :teamId " +
           "AND ar.analysisType = :analysisType " +
           "AND ar.timePeriodStart >= :startDate " +
           "AND ar.timePeriodEnd <= :endDate")
    List<AnalysisResults> findByTeamAndTypeAndPeriod(
        @Param("teamId") Long teamId,
        @Param("analysisType") String analysisType,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );
}