package de.tum.in.www1.hephaestus.analytics;

import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Analytics API for code review metrics and insights")
@SecurityRequirement(name = "bearer-key")
public class AnalyticsController {
    private final AnalyticsService analyticsService;
    private final TeamService teamService;

    @PostMapping("/process-review")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Process a review for analytics", 
              description = "Processes a pull request review and generates analytics data")
    public ResponseEntity<ReviewMetrics> processReview(@RequestBody PullRequestReview review) {
        ReviewMetrics metrics = analyticsService.processReview(review);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get team metrics", 
              description = "Retrieves review metrics for a team within a specified date range")
    public ResponseEntity<List<ReviewMetrics>> getMetrics(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        // Verify team access
        Team team = teamService.getTeamById(teamId);
        if (team == null) {
            return ResponseEntity.notFound().build();
        }

        List<ReviewMetrics> metrics = analyticsService.getTeamMetrics(teamId, startDate, endDate);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/insights/{timeRange}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get team insights", 
              description = "Retrieves analysis results for a team for a specific time range")
    public ResponseEntity<List<AnalysisResults>> getInsights(
            @RequestParam Long teamId,
            @PathVariable String timeRange) {
        // Verify team access
        Team team = teamService.getTeamById(teamId);
        if (team == null) {
            return ResponseEntity.notFound().build();
        }

        // Calculate date range based on timeRange parameter
        Instant endDate = Instant.now();
        Instant startDate = switch (timeRange.toLowerCase()) {
            case "week" -> endDate.minus(java.time.Duration.ofDays(7));
            case "month" -> endDate.minus(java.time.Duration.ofDays(30));
            case "quarter" -> endDate.minus(java.time.Duration.ofDays(90));
            default -> endDate.minus(java.time.Duration.ofDays(30)); // Default to month
        };

        List<AnalysisResults> insights = analyticsService.getTeamMetrics(teamId, startDate, endDate);
        return ResponseEntity.ok(insights);
    }

    @GetMapping("/recommendations")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get team recommendations", 
              description = "Retrieves insight recommendations for a team")
    public ResponseEntity<List<InsightRecommendation>> getRecommendations(
            @RequestParam Long teamId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        // Verify team access
        Team team = teamService.getTeamById(teamId);
        if (team == null) {
            return ResponseEntity.notFound().build();
        }

        // If since is not provided, default to last 30 days
        if (since == null) {
            since = Instant.now().minus(java.time.Duration.ofDays(30));
        }

        List<InsightRecommendation> recommendations = analyticsService.getTeamRecommendations(teamId, since);
        return ResponseEntity.ok(recommendations);
    }
}