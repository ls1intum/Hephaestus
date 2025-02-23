package de.tum.in.www1.hephaestus.analytics;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for processing and managing analytics data related to code reviews.
 * Provides functionality for metrics calculation, analysis generation, and recommendation management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final ReviewMetricsRepository reviewMetricsRepository;
    private final AnalysisResultsRepository analysisResultsRepository;
    private final InsightRecommendationRepository insightRecommendationRepository;

    private static final int MAX_QUALITY_SCORE = 5;
    private static final double COMMENT_SCORE_WEIGHT = 0.5;
    private static final double SUGGESTION_SCORE_WEIGHT = 1.0;

    /**
     * Processes a pull request review and generates metrics.
     * Updates caches for related team data.
     *
     * @param review The pull request review to process
     * @return Generated review metrics
     * @throws IllegalArgumentException if review or its related entities are null
     */
    @Transactional
    @CacheEvict(cacheNames = {"teamMetrics", "analysisResults"}, key = "#review.pullRequest.repository.team.id")
    public ReviewMetrics processReview(PullRequestReview review) {
        Assert.notNull(review, "Review cannot be null");
        Assert.notNull(review.getPullRequest(), "Pull request cannot be null");
        Assert.notNull(review.getUser(), "Reviewer cannot be null");

        ReviewMetrics metrics = createReviewMetrics(review);
        return reviewMetricsRepository.save(metrics);
    }

    /**
     * Retrieves metrics for a specific team within a date range.
     * Results are cached for performance.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "teamMetrics", key = "#teamId + '_' + #startDate + '_' + #endDate")
    public List<ReviewMetrics> getTeamMetrics(Long teamId, Instant startDate, Instant endDate) {
        Assert.notNull(teamId, "Team ID cannot be null");
        Assert.notNull(startDate, "Start date cannot be null");
        Assert.notNull(endDate, "End date cannot be null");
        Assert.isTrue(!endDate.isBefore(startDate), "End date must be after start date");

        return reviewMetricsRepository.findByTeamAndDateRange(teamId, startDate, endDate);
    }

    /**
     * Creates a new analysis result for a team.
     * Invalidates related caches.
     */
    @Transactional
    @CacheEvict(cacheNames = {"analysisResults", "recommendations"}, key = "#team.id")
    public AnalysisResults createAnalysis(Team team, String analysisType, 
                                        Instant startDate, Instant endDate, 
                                        Map<String, Object> analysisData) {
        Assert.notNull(team, "Team cannot be null");
        Assert.hasText(analysisType, "Analysis type cannot be empty");
        Assert.notNull(analysisData, "Analysis data cannot be null");

        AnalysisResults results = new AnalysisResults();
        results.setTeam(team);
        results.setAnalysisType(analysisType);
        results.setTimePeriodStart(startDate);
        results.setTimePeriodEnd(endDate);
        results.setAnalysisData(analysisData);

        return analysisResultsRepository.save(results);
    }

    /**
     * Creates a new recommendation based on analysis results.
     * Invalidates recommendation cache for the team.
     */
    @Transactional
    @CacheEvict(cacheNames = "recommendations", key = "#team.id")
    public InsightRecommendation createRecommendation(Team team, AnalysisResults analysis,
                                                    String type, String text, Integer priority) {
        Assert.notNull(team, "Team cannot be null");
        Assert.notNull(analysis, "Analysis cannot be null");
        Assert.hasText(type, "Recommendation type cannot be empty");
        Assert.hasText(text, "Recommendation text cannot be empty");

        InsightRecommendation recommendation = new InsightRecommendation();
        recommendation.setTeam(team);
        recommendation.setAnalysisResult(analysis);
        recommendation.setRecommendationType(type);
        recommendation.setRecommendationText(text);
        recommendation.setPriorityLevel(priority);

        return insightRecommendationRepository.save(recommendation);
    }

    /**
     * Retrieves recent recommendations for a team.
     * Results are cached for performance.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "recommendations", key = "#teamId + '_' + #since")
    public List<InsightRecommendation> getTeamRecommendations(Long teamId, Instant since) {
        Assert.notNull(teamId, "Team ID cannot be null");
        Assert.notNull(since, "Since date cannot be null");

        return insightRecommendationRepository.findRecentByTeam(teamId, since);
    }

    /**
     * Generates analytics data for a team within a specified time period.
     * Calculates various metrics including review times, comment counts, and quality scores.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> generateTeamAnalytics(Team team, Instant startDate, Instant endDate) {
        Assert.notNull(team, "Team cannot be null");
        Assert.notNull(startDate, "Start date cannot be null");
        Assert.notNull(endDate, "End date cannot be null");

        List<ReviewMetrics> metrics = getTeamMetrics(team.getId(), startDate, endDate);
        return calculateTeamMetrics(metrics);
    }

    private ReviewMetrics createReviewMetrics(PullRequestReview review) {
        ReviewMetrics metrics = new ReviewMetrics();
        metrics.setPullRequest(review.getPullRequest());
        metrics.setReviewer(review.getUser());
        
        metrics.setReviewTimeMinutes(calculateReviewTime(review));
        metrics.setCommentCount(countReviewComments(review));
        metrics.setCodeSuggestionsCount(countCodeSuggestions(review));
        metrics.setReviewQualityScore(calculateReviewQualityScore(review));

        return metrics;
    }

    private Integer calculateReviewTime(PullRequestReview review) {
        if (review.getSubmittedAt() != null && review.getStartedAt() != null) {
            return (int) Duration.between(review.getStartedAt(), review.getSubmittedAt()).toMinutes();
        }
        return null;
    }

    private int countReviewComments(PullRequestReview review) {
        return review.getComments().size();
    }

    private int countCodeSuggestions(PullRequestReview review) {
        return (int) review.getComments().stream()
            .filter(comment -> comment.getBody().contains("```suggestion"))
            .count();
    }

    private BigDecimal calculateReviewQualityScore(PullRequestReview review) {
        double score = 1.0; // Base score for completing a review
        score += countReviewComments(review) * COMMENT_SCORE_WEIGHT;
        score += countCodeSuggestions(review) * SUGGESTION_SCORE_WEIGHT;
        
        return BigDecimal.valueOf(Math.min(MAX_QUALITY_SCORE, score));
    }

    private Map<String, Object> calculateTeamMetrics(List<ReviewMetrics> metrics) {
        Map<String, Object> analytics = new HashMap<>();
        
        analytics.put("averageReviewTimeMinutes", calculateAverageReviewTime(metrics));
        analytics.put("averageCommentsPerReview", calculateAverageComments(metrics));
        analytics.put("averageQualityScore", calculateAverageQualityScore(metrics));
        analytics.put("totalReviews", metrics.size());
        
        return analytics;
    }

    private double calculateAverageReviewTime(List<ReviewMetrics> metrics) {
        return metrics.stream()
            .filter(m -> m.getReviewTimeMinutes() != null)
            .mapToInt(ReviewMetrics::getReviewTimeMinutes)
            .average()
            .orElse(0.0);
    }

    private double calculateAverageComments(List<ReviewMetrics> metrics) {
        return metrics.stream()
            .mapToInt(ReviewMetrics::getCommentCount)
            .average()
            .orElse(0.0);
    }

    private double calculateAverageQualityScore(List<ReviewMetrics> metrics) {
        return metrics.stream()
            .map(ReviewMetrics::getReviewQualityScore)
            .mapToDouble(BigDecimal::doubleValue)
            .average()
            .orElse(0.0);
    }
}