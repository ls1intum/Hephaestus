package de.tum.in.www1.hephaestus.agent.handler;

import de.tum.in.www1.hephaestus.account.UserPreferencesRepository;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.practices.review.DeliveryDecision;
import de.tum.in.www1.hephaestus.practices.review.PracticeReviewDeliveryGate;
import de.tum.in.www1.hephaestus.practices.review.PracticeReviewProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/**
 * Orchestrates delivery of practice review feedback to PRs/MRs.
 *
 * <p>Loads required data (PR state, user preferences, dedup lookup), delegates the
 * delivery decision to {@link PracticeReviewDeliveryGate}, then executes the result by dispatching
 * to {@link PullRequestCommentPoster} and {@link DiffNotePoster}.
 *
 * <p>Delivery is always best-effort (soft failure). Findings are already persisted
 * by {@link PracticeDetectionDeliveryService} before this is called.
 *
 * <p>Package-private — created as {@code @Bean} in {@link JobTypeHandlerConfiguration}.
 */
class FeedbackDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackDeliveryService.class);

    private final PracticeReviewDeliveryGate deliveryGate;
    private final PullRequestCommentPoster commentPoster;
    private final DiffNotePoster diffNotePoster;
    private final UserPreferencesRepository userPreferencesRepository;
    private final PullRequestRepository pullRequestRepository;
    private final AgentJobRepository agentJobRepository;
    private final PracticeReviewProperties reviewProperties;

    FeedbackDeliveryService(
        PracticeReviewDeliveryGate deliveryGate,
        PullRequestCommentPoster commentPoster,
        DiffNotePoster diffNotePoster,
        UserPreferencesRepository userPreferencesRepository,
        PullRequestRepository pullRequestRepository,
        AgentJobRepository agentJobRepository,
        PracticeReviewProperties reviewProperties
    ) {
        this.deliveryGate = deliveryGate;
        this.commentPoster = commentPoster;
        this.diffNotePoster = diffNotePoster;
        this.userPreferencesRepository = userPreferencesRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.agentJobRepository = agentJobRepository;
        this.reviewProperties = reviewProperties;
    }

    /**
     * Delivers feedback to the PR/MR. Best-effort: failures are logged but never thrown.
     *
     * @param job         the completed agent job
     * @param delivery    pre-rendered delivery content from agent output (null if absent)
     * @param hasNegative whether any NEGATIVE findings were persisted
     */
    void deliverFeedback(AgentJob job, @Nullable DeliveryContent delivery, boolean hasNegative) {
        try {
            doDeliver(job, delivery, hasNegative);
        } catch (Exception e) {
            log.warn("Feedback delivery failed (non-fatal): jobId={}", job.getId(), e);
        }
    }

    private void doDeliver(AgentJob job, @Nullable DeliveryContent delivery, boolean hasNegative) {
        // Short-circuit: no delivery content AND has negative findings → nothing useful to do.
        // The agent should have provided delivery content for negative findings; this is a malformed output.
        // When delivery is null and all findings are positive, we proceed to check for a previous
        // comment — the "all resolved" message is system-generated, not agent-generated.
        if (delivery == null && hasNegative) {
            log.debug("No delivery content in agent output despite negative findings, skipping: jobId={}", job.getId());
            return;
        }

        // ── Load data for gate evaluation ────────────────────────────────────

        var metadata = job.getMetadata();
        Long pullRequestId = (metadata != null && metadata.has("pull_request_id"))
            ? metadata.get("pull_request_id").asLong()
            : null;

        PullRequest pullRequest =
            pullRequestId != null ? pullRequestRepository.findByIdWithAuthor(pullRequestId).orElse(null) : null;

        boolean userAiReviewEnabled = resolveUserAiReviewEnabled(pullRequest);

        String previousCommentId = (pullRequestId != null)
            ? agentJobRepository
                  .findPreviousDeliveryCommentId(job.getWorkspace().getId(), pullRequestId, job.getId())
                  .orElse(null)
            : null;

        // ── Gate decision ────────────────────────────────────────────────────

        DeliveryDecision decision = deliveryGate.evaluate(
            pullRequest,
            hasNegative,
            delivery != null,
            userAiReviewEnabled,
            previousCommentId
        );

        // ── Execute decision ─────────────────────────────────────────────────

        switch (decision) {
            case DeliveryDecision.StoreOnly skip -> log.info(
                "Delivery suppressed: reason={}, jobId={}",
                skip.reason(),
                job.getId()
            );
            case DeliveryDecision.PostNew postNew -> {
                postSummaryNote(job, delivery, null);
                postDiffNotes(job, delivery);
            }
            case DeliveryDecision.EditExisting edit -> {
                postSummaryNote(job, delivery, edit.commentId());
            }
            case DeliveryDecision.EditAllResolved resolved -> postAllResolvedNote(job, resolved.commentId());
        }
    }

    private boolean resolveUserAiReviewEnabled(@Nullable PullRequest pullRequest) {
        if (pullRequest == null || pullRequest.getAuthor() == null) {
            return true; // opt-out model: default enabled
        }
        return userPreferencesRepository
            .findByUserId(pullRequest.getAuthor().getId())
            .map(prefs -> prefs.isAiReviewEnabled())
            .orElse(true);
    }

    private void postSummaryNote(AgentJob job, DeliveryContent delivery, @Nullable String existingCommentId) {
        if (delivery.mrNote() == null) {
            return;
        }
        String sanitized = PullRequestCommentPoster.sanitize(delivery.mrNote());
        if (sanitized.isBlank()) {
            log.debug("Practice note was empty after sanitization, skipping post: jobId={}", job.getId());
            return;
        }
        String formatted = formatPracticeNote(sanitized, job, reviewProperties.appBaseUrl());
        String commentId = commentPoster.postFormattedBody(job, formatted, existingCommentId);
        if (commentId != null) {
            // Set commentId on entity — read by AgentJobExecutor.persistDeliveryStatus() via shared reference.
            // DeliveryStatus is set by the executor (not here) based on whether deliver() threw.
            job.setDeliveryCommentId(commentId);
            log.info(
                "Practice summary note {}: jobId={}, commentId={}",
                existingCommentId != null ? "updated" : "posted",
                job.getId(),
                commentId
            );
        }
    }

    private void postDiffNotes(AgentJob job, DeliveryContent delivery) {
        if (delivery.diffNotes().isEmpty()) {
            return;
        }
        int maxNotes = reviewProperties.maxInlineNotes();
        var cappedNotes =
            delivery.diffNotes().size() <= maxNotes ? delivery.diffNotes() : delivery.diffNotes().subList(0, maxNotes);

        DiffNotePoster.DiffNoteResult diffResult = diffNotePoster.postDiffNotes(job, cappedNotes);
        log.info(
            "Diff notes delivery: posted={}, failed={}, capped={}, jobId={}",
            diffResult.posted(),
            diffResult.failed(),
            delivery.diffNotes().size() > maxNotes,
            job.getId()
        );
    }

    private void postAllResolvedNote(AgentJob job, String existingCommentId) {
        String body = formatAllResolvedNote(job, reviewProperties.appBaseUrl());
        String commentId = commentPoster.postFormattedBody(job, body, existingCommentId);
        if (commentId != null) {
            job.setDeliveryCommentId(commentId);
            log.info("Practice note updated to all-resolved: jobId={}, commentId={}", job.getId(), commentId);
        }
    }

    // ── Formatting ──────────────────────────────────────────────────────────

    /**
     * Formats a practice summary note with marker, disclaimer, preferences footer,
     * and metadata footer. Unlike the generic review comment (which uses
     * {@code <details>} collapse), practice feedback is shown fully visible.
     *
     * @param sanitizedBody the sanitized mrNote content
     * @param job           the agent job (for metadata footer)
     * @param appBaseUrl    base URL for preferences link (empty/null to omit)
     * @return fully formatted comment body
     */
    static String formatPracticeNote(String sanitizedBody, AgentJob job, @Nullable String appBaseUrl) {
        var sb = new StringBuilder(sanitizedBody.length() + 512);

        // HTML comment marker for deduplication on re-analysis
        sb.append("<!-- hephaestus:practice-review:").append(job.getId()).append(" -->\n");

        // Bot disclaimer
        sb.append(
            "> **Note:** This is an automated practice review generated by an AI agent. It may contain inaccuracies.\n\n"
        );

        // Body (NOT in <details> — practice feedback should be visible)
        sb.append(sanitizedBody).append("\n\n");

        // Preferences + metadata footer
        appendFooter(sb, job, appBaseUrl);

        return sb.toString();
    }

    /**
     * Formats an "all resolved" note for re-analysis where all issues are now positive.
     */
    static String formatAllResolvedNote(AgentJob job, @Nullable String appBaseUrl) {
        var sb = new StringBuilder(512);

        sb.append("<!-- hephaestus:practice-review:").append(job.getId()).append(" -->\n");
        sb.append("> All previously identified issues have been resolved. Nice work! 🎉\n\n");

        appendFooter(sb, job, appBaseUrl);

        return sb.toString();
    }

    /**
     * Appends the combined footer: preferences link (if configured) + metadata.
     */
    private static void appendFooter(StringBuilder sb, AgentJob job, @Nullable String appBaseUrl) {
        sb.append("---\n");
        if (appBaseUrl != null && !appBaseUrl.isBlank()) {
            sb.append("*[Hephaestus](").append(appBaseUrl).append(")");
            sb.append(" · [Configure AI review preferences](").append(appBaseUrl).append("/settings)*\n\n");
        }
        PullRequestCommentPoster.appendMetadataFooter(sb, job);
    }
}
