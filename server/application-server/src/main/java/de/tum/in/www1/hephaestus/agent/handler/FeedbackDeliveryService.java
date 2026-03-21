package de.tum.in.www1.hephaestus.agent.handler;

import de.tum.in.www1.hephaestus.account.UserPreferencesRepository;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import de.tum.in.www1.hephaestus.agent.job.DeliveryStatus;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/**
 * Orchestrates delivery of practice review feedback to PRs/MRs.
 *
 * <p>Handles all delivery prerequisites:
 * <ul>
 *   <li>PR state check (skip closed/merged/draft)</li>
 *   <li>User AI review preference (opt-out model)</li>
 *   <li>Re-analysis deduplication (update existing comment)</li>
 *   <li>Diff note skipping on re-analysis</li>
 * </ul>
 *
 * <p>Delivery is always best-effort (soft failure). Findings are already persisted
 * by {@link PracticeDetectionDeliveryService} before this is called.
 *
 * <p>Package-private — created as {@code @Bean} in {@link JobTypeHandlerConfiguration}.
 */
class FeedbackDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackDeliveryService.class);

    private final PullRequestCommentPoster commentPoster;
    private final DiffNotePoster diffNotePoster;
    private final UserPreferencesRepository userPreferencesRepository;
    private final PullRequestRepository pullRequestRepository;
    private final AgentJobRepository agentJobRepository;

    FeedbackDeliveryService(
        PullRequestCommentPoster commentPoster,
        DiffNotePoster diffNotePoster,
        UserPreferencesRepository userPreferencesRepository,
        PullRequestRepository pullRequestRepository,
        AgentJobRepository agentJobRepository
    ) {
        this.commentPoster = commentPoster;
        this.diffNotePoster = diffNotePoster;
        this.userPreferencesRepository = userPreferencesRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.agentJobRepository = agentJobRepository;
    }

    /**
     * Delivers feedback to the PR/MR. Best-effort: failures are logged but never thrown.
     *
     * @param job         the completed agent job
     * @param delivery    pre-rendered delivery content from agent output (null if absent)
     * @param hasNegative whether any NEGATIVE findings were persisted
     */
    void deliverFeedback(AgentJob job, @Nullable DeliveryContent delivery, boolean hasNegative) {
        if (delivery == null) {
            log.debug("No delivery content in agent output, skipping feedback: jobId={}", job.getId());
            return;
        }

        try {
            doDeliver(job, delivery, hasNegative);
        } catch (Exception e) {
            log.warn("Feedback delivery failed (non-fatal): jobId={}", job.getId(), e);
        }
    }

    private void doDeliver(AgentJob job, DeliveryContent delivery, boolean hasNegative) {
        // Load PR to check state
        var metadata = job.getMetadata();
        if (metadata == null || !metadata.has("pull_request_id")) {
            log.warn("Job metadata missing pull_request_id — skipping delivery: jobId={}", job.getId());
            return;
        }
        Long pullRequestId = metadata.get("pull_request_id").asLong();
        PullRequest pullRequest = pullRequestRepository.findByIdWithAuthor(pullRequestId).orElse(null);

        if (pullRequest == null) {
            log.warn(
                "Pull request not found for delivery — skipping: pullRequestId={}, jobId={}",
                pullRequestId,
                job.getId()
            );
            return;
        }

        // Skip closed/merged PRs
        if (pullRequest.getState() == Issue.State.CLOSED || pullRequest.getState() == Issue.State.MERGED) {
            log.info(
                "Skipping delivery — PR is {}: pullRequestId={}, jobId={}",
                pullRequest.getState(),
                pullRequestId,
                job.getId()
            );
            return;
        }

        // Skip draft PRs
        if (pullRequest.isDraft()) {
            log.info("Skipping delivery — PR is draft: pullRequestId={}, jobId={}", pullRequestId, job.getId());
            return;
        }

        // Check user preference (opt-out model: default true if no record)
        if (pullRequest.getAuthor() != null) {
            boolean aiReviewEnabled = userPreferencesRepository
                .findByUserId(pullRequest.getAuthor().getId())
                .map(prefs -> prefs.isAiReviewEnabled())
                .orElse(true);

            if (!aiReviewEnabled) {
                log.info(
                    "Skipping delivery — user opted out of AI review: userId={}, jobId={}",
                    pullRequest.getAuthor().getId(),
                    job.getId()
                );
                return;
            }
        }

        // Look up previous delivery comment for re-analysis dedup
        Long workspaceId = job.getWorkspace().getId();
        String previousCommentId = agentJobRepository
            .findPreviousDeliveryCommentId(workspaceId, pullRequestId, job.getId())
            .orElse(null);
        boolean isReAnalysis = previousCommentId != null;

        // Summary note (if negatives exist and mrNote provided)
        if (hasNegative && delivery.mrNote() != null) {
            String commentId = commentPoster.postPracticeNote(job, delivery.mrNote(), previousCommentId);
            if (commentId != null) {
                job.setDeliveryCommentId(commentId);
                job.setDeliveryStatus(DeliveryStatus.DELIVERED);
                log.info(
                    "Practice summary note {}: jobId={}, commentId={}",
                    isReAnalysis ? "updated" : "posted",
                    job.getId(),
                    commentId
                );
            }
        }

        // Diff notes (only when negative findings exist; skip on re-analysis)
        if (hasNegative && !delivery.diffNotes().isEmpty() && !isReAnalysis) {
            DiffNotePoster.DiffNoteResult diffResult = diffNotePoster.postDiffNotes(job, delivery.diffNotes());
            log.info(
                "Diff notes delivery: posted={}, failed={}, jobId={}",
                diffResult.posted(),
                diffResult.failed(),
                job.getId()
            );
        } else if (!delivery.diffNotes().isEmpty() && isReAnalysis) {
            log.debug("Skipping diff notes on re-analysis: jobId={}", job.getId());
        } else if (!delivery.diffNotes().isEmpty()) {
            log.debug("Skipping diff notes — no negative findings: jobId={}", job.getId());
        }
    }

    /**
     * Formats a practice summary note with marker, disclaimer, and metadata footer.
     * Unlike the generic review comment (which uses {@code <details>} collapse),
     * practice feedback is shown fully visible for maximum impact.
     *
     * @param sanitizedBody the sanitized mrNote content
     * @param job           the agent job (for metadata footer)
     * @return fully formatted comment body
     */
    static String formatPracticeNote(String sanitizedBody, AgentJob job) {
        var sb = new StringBuilder(sanitizedBody.length() + 512);

        // HTML comment marker for deduplication on re-analysis
        sb.append("<!-- hephaestus:practice-review:").append(job.getId()).append(" -->\n");

        // Bot disclaimer
        sb.append(
            "> **Note:** This is an automated practice review generated by an AI agent. It may contain inaccuracies.\n\n"
        );

        // Body (NOT in <details> — practice feedback should be visible)
        sb.append(sanitizedBody).append("\n\n");

        // Metadata footer
        PullRequestCommentPoster.appendMetadataFooter(sb, job);

        return sb.toString();
    }
}
