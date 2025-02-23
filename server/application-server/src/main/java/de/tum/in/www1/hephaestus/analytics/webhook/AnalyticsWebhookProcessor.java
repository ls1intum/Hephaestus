package de.tum.in.www1.hephaestus.analytics.webhook;

import de.tum.in.www1.hephaestus.analytics.AnalyticsService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import io.nats.client.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Processes GitHub webhooks for analytics purposes.
 * Handles review events and generates insights and recommendations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsWebhookProcessor {
    private final AnalyticsService analyticsService;

    private static final Duration ANALYSIS_PERIOD = Duration.ofDays(30);
    private static final double LOW_REVIEW_TIME_THRESHOLD = 10.0;
    private static final double HIGH_REVIEW_TIME_THRESHOLD = 60.0;
    private static final double LOW_COMMENT_THRESHOLD = 2.0;
    private static final double LOW_QUALITY_SCORE_THRESHOLD = 3.0;

    /**
     * Processes a pull request review webhook event.
     * Generates metrics, analysis, and recommendations.
     *
     * @param message The NATS message containing the webhook data
     * @param review The pull request review to process
     */
    public void processPullRequestReviewWebhook(Message message, PullRequestReview review) {
        try {
            Assert.notNull(review, "Review cannot be null");
            Assert.notNull(review.getPullRequest(), "Pull request cannot be null");
            Assert.notNull(review.getPullRequest().getRepository(), "Repository cannot be null");
            Assert.notNull(review.getPullRequest().getRepository().getTeam(), "Team cannot be null");

            // Process the review
            analyticsService.processReview(review);

            // Generate analysis for the configured time period
            Team team = review.getPullRequest().getRepository().getTeam();
            Instant endDate = Instant.now();
            Instant startDate = endDate.minus(ANALYSIS_PERIOD);

            Map<String, Object> analytics = analyticsService.generateTeamAnalytics(team, startDate, endDate);
            var analysis = analyticsService.createAnalysis(
                team,
                "REVIEW_METRICS",
                startDate,
                endDate,
                analytics
            );

            generateRecommendations(team, analysis, analytics);

        } catch (Exception e) {
            log.error("Error processing pull request review webhook for analytics: {}", e.getMessage(), e);
            // Consider implementing retry logic or dead letter queue for failed processing
        }
    }

    /**
     * Generates recommendations based on analysis results.
     * Creates prioritized recommendations for various metrics.
     */
    private void generateRecommendations(Team team, 
                                       de.tum.in.www1.hephaestus.analytics.AnalysisResults analysis,
                                       Map<String, Object> analytics) {
        try {
            double avgReviewTime = (double) analytics.get("averageReviewTimeMinutes");
            double avgComments = (double) analytics.get("averageCommentsPerReview");
            double avgQualityScore = (double) analytics.get("averageQualityScore");

            generateReviewTimeRecommendations(team, analysis, avgReviewTime);
            generateCommentQuantityRecommendations(team, analysis, avgComments);
            generateQualityScoreRecommendations(team, analysis, avgQualityScore);

        } catch (Exception e) {
            log.error("Error generating recommendations: {}", e.getMessage(), e);
        }
    }

    private void generateReviewTimeRecommendations(Team team, 
                                                 de.tum.in.www1.hephaestus.analytics.AnalysisResults analysis,
                                                 double avgReviewTime) {
        if (avgReviewTime < LOW_REVIEW_TIME_THRESHOLD) {
            analyticsService.createRecommendation(
                team,
                analysis,
                "REVIEW_TIME",
                "Reviews are being completed very quickly. Consider spending more time on detailed code analysis.",
                1
            );
        } else if (avgReviewTime > HIGH_REVIEW_TIME_THRESHOLD) {
            analyticsService.createRecommendation(
                team,
                analysis,
                "REVIEW_TIME",
                "Reviews are taking longer than optimal. Consider breaking down PRs into smaller chunks.",
                2
            );
        }
    }

    private void generateCommentQuantityRecommendations(Team team, 
                                                      de.tum.in.www1.hephaestus.analytics.AnalysisResults analysis,
                                                      double avgComments) {
        if (avgComments < LOW_COMMENT_THRESHOLD) {
            analyticsService.createRecommendation(
                team,
                analysis,
                "COMMENT_QUANTITY",
                "Reviews have few comments. Encourage more detailed feedback and discussions.",
                1
            );
        }
    }

    private void generateQualityScoreRecommendations(Team team, 
                                                   de.tum.in.www1.hephaestus.analytics.AnalysisResults analysis,
                                                   double avgQualityScore) {
        if (avgQualityScore < LOW_QUALITY_SCORE_THRESHOLD) {
            analyticsService.createRecommendation(
                team,
                analysis,
                "REVIEW_QUALITY",
                "Review quality scores are below target. Focus on providing more constructive feedback and code suggestions.",
                1
            );
        }
    }
}