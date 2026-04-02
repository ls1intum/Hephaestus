package de.tum.in.www1.hephaestus.agent.handler;

import de.tum.in.www1.hephaestus.account.UserPreferencesRepository;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.practices.review.PracticeReviewProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/**
 * Delivers practice review feedback to PRs/MRs.
 *
 * <p>Every delivery is independent: always posts a new MR summary comment + all diff notes.
 * No edit-in-place, no deduplication. Findings are already persisted by
 * {@link PracticeDetectionDeliveryService} before this is called.
 *
 * <p>Delivery is best-effort (soft failure).
 *
 * <p>Package-private — created as {@code @Bean} in {@link JobTypeHandlerConfiguration}.
 */
class FeedbackDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackDeliveryService.class);

    private final PullRequestCommentPoster commentPoster;
    private final DiffNotePoster diffNotePoster;
    private final UserPreferencesRepository userPreferencesRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PracticeReviewProperties reviewProperties;

    FeedbackDeliveryService(
        PullRequestCommentPoster commentPoster,
        DiffNotePoster diffNotePoster,
        UserPreferencesRepository userPreferencesRepository,
        PullRequestRepository pullRequestRepository,
        PracticeReviewProperties reviewProperties
    ) {
        this.commentPoster = commentPoster;
        this.diffNotePoster = diffNotePoster;
        this.userPreferencesRepository = userPreferencesRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.reviewProperties = reviewProperties;
    }

    /**
     * Delivers feedback to the PR/MR. Best-effort: failures are logged but never thrown.
     */
    void deliverFeedback(AgentJob job, @Nullable DeliveryContent delivery, boolean hasNegative) {
        try {
            doDeliver(job, delivery, hasNegative);
        } catch (Exception e) {
            log.warn("Feedback delivery failed (non-fatal): jobId={}", job.getId(), e);
        }
    }

    private void doDeliver(AgentJob job, @Nullable DeliveryContent delivery, boolean hasNegative) {
        if (delivery == null) {
            log.debug("No delivery content, skipping: jobId={}", job.getId());
            return;
        }
        if (!hasNegative) {
            log.debug("All findings positive, skipping delivery: jobId={}", job.getId());
            return;
        }

        // ── Suppression guards ──────────────────────────────────────────────

        var metadata = job.getMetadata();
        Long pullRequestId = (metadata != null && metadata.has("pull_request_id"))
            ? metadata.get("pull_request_id").asLong()
            : null;

        PullRequest pr =
            pullRequestId != null ? pullRequestRepository.findByIdWithAuthor(pullRequestId).orElse(null) : null;

        if (pr == null) {
            log.info("Delivery suppressed: PR not found, jobId={}", job.getId());
            return;
        }
        if (pr.getState() == Issue.State.CLOSED) {
            log.info("Delivery suppressed: PR closed, jobId={}", job.getId());
            return;
        }
        if (pr.getState() == Issue.State.MERGED && !reviewProperties.deliverToMerged()) {
            log.info("Delivery suppressed: PR merged, jobId={}", job.getId());
            return;
        }
        if (reviewProperties.skipDrafts() && pr.isDraft()) {
            log.info("Delivery suppressed: PR is draft, jobId={}", job.getId());
            return;
        }
        if (pr.getAuthor() != null) {
            boolean enabled = userPreferencesRepository
                .findByUserId(pr.getAuthor().getId())
                .map(prefs -> prefs.isAiReviewEnabled())
                .orElse(true);
            if (!enabled) {
                log.info("Delivery suppressed: user opted out, jobId={}", job.getId());
                return;
            }
        }

        // ── Always post new ─────────────────────────────────────────────────

        postSummaryNote(job, delivery);
        postDiffNotes(job, delivery);
    }

    private void postSummaryNote(AgentJob job, DeliveryContent delivery) {
        if (delivery.mrNote() == null) {
            return;
        }
        String sanitized = PullRequestCommentPoster.sanitize(delivery.mrNote());
        if (sanitized.isBlank()) {
            log.debug("Practice note was empty after sanitization, skipping post: jobId={}", job.getId());
            return;
        }
        String formatted = formatPracticeNote(sanitized, job, reviewProperties.appBaseUrl());
        String commentId = commentPoster.postFormattedBody(job, formatted);
        if (commentId != null) {
            job.setDeliveryCommentId(commentId);
            log.info("Practice summary note posted: jobId={}, commentId={}", job.getId(), commentId);
        }
    }

    private void postDiffNotes(AgentJob job, DeliveryContent delivery) {
        if (delivery.diffNotes().isEmpty()) {
            return;
        }
        DiffNotePoster.DiffNoteResult diffResult = diffNotePoster.postDiffNotes(job, delivery.diffNotes());
        log.info(
            "Diff notes delivery: posted={}, failed={}, total={}, jobId={}",
            diffResult.posted(),
            diffResult.failed(),
            delivery.diffNotes().size(),
            job.getId()
        );
    }

    // ── Formatting ──────────────────────────────────────────────────────────

    static String formatPracticeNote(String sanitizedBody, AgentJob job, @Nullable String appBaseUrl) {
        var sb = new StringBuilder(sanitizedBody.length() + 512);
        sb.append("<!-- hephaestus:practice-review:").append(job.getId()).append(" -->\n");
        sb.append(sanitizedBody).append("\n\n");
        appendFooter(sb, job, appBaseUrl);
        return sb.toString();
    }

    private static void appendFooter(StringBuilder sb, AgentJob job, @Nullable String appBaseUrl) {
        sb.append("---\n");
        if (appBaseUrl != null && !appBaseUrl.isBlank()) {
            sb.append("*[Hephaestus](").append(appBaseUrl).append(")");
            sb.append(" · [Configure AI review preferences](").append(appBaseUrl).append("/settings)*\n\n");
        }
        PullRequestCommentPoster.appendMetadataFooter(sb, job);
    }
}
