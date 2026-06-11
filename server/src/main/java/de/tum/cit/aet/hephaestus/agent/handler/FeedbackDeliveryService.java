package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.account.UserPreferencesRepository;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewSettings;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final WorkspaceRepository workspaceRepository;
    private final PracticeReviewProperties reviewProperties;

    FeedbackDeliveryService(
        PullRequestCommentPoster commentPoster,
        DiffNotePoster diffNotePoster,
        UserPreferencesRepository userPreferencesRepository,
        PullRequestRepository pullRequestRepository,
        WorkspaceRepository workspaceRepository,
        PracticeReviewProperties reviewProperties
    ) {
        this.commentPoster = commentPoster;
        this.diffNotePoster = diffNotePoster;
        this.userPreferencesRepository = userPreferencesRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.workspaceRepository = workspaceRepository;
        this.reviewProperties = reviewProperties;
    }

    /**
     * Delivers feedback to the PR/MR.
     *
     * <p>Two failure classes are treated differently. An <em>integrity</em> failure — null
     * {@code integrationKind}, no {@link de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel}
     * wired, or the summary post returning no id — means the contributor sees <em>nothing</em> despite
     * the agent having run (LLM cost incurred). Those surface as a {@link JobDeliveryException} so the
     * executor marks the job FAILED instead of silently reporting "DELIVERED". <em>Cosmetic</em>
     * failures (e.g. one diff note that fell outside a hunk) stay soft and are only logged.
     */
    void deliverFeedback(AgentJob job, @Nullable DeliveryContent delivery) {
        try {
            doDeliver(job, delivery);
        } catch (JobDeliveryException e) {
            // Integrity failure — propagate so the job is marked FAILED, not silently DELIVERED.
            throw e;
        } catch (Exception e) {
            log.warn("Feedback delivery failed (non-fatal): jobId={}", job.getId(), e);
        }
    }

    private void doDeliver(AgentJob job, @Nullable DeliveryContent delivery) {
        if (delivery == null) {
            log.debug("No delivery content, skipping: jobId={}", job.getId());
            return;
        }

        // Suppression guards

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
        // Resolve per-workspace overrides. getId() on the lazy proxy is safe outside a tx (the id is
        // known without initialisation); the settings query then runs in its own repository tx.
        PracticeReviewSettings settings = workspaceRepository
            .findById(job.getWorkspace().getId())
            .map(Workspace::getReviewSettings)
            .orElseGet(PracticeReviewSettings::new);
        if (
            pr.getState() == Issue.State.MERGED && !settings.resolveDeliverToMerged(reviewProperties.deliverToMerged())
        ) {
            log.info("Delivery suppressed: PR merged, jobId={}", job.getId());
            return;
        }
        if (settings.resolveSkipDrafts(reviewProperties.skipDrafts()) && pr.isDraft()) {
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

        // Always post new

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
        String formatted = formatPracticeNote(sanitized, job);
        String commentId = commentPoster.postFormattedBody(job, formatted);
        if (commentId == null) {
            // We had a real, non-blank summary to post but the provider returned no comment id —
            // the contributor sees nothing. Treat as an integrity failure so the job is marked FAILED.
            throw new JobDeliveryException(
                "Summary note post returned no comment id despite a non-empty body: jobId=" + job.getId()
            );
        }
        job.setDeliveryCommentId(commentId);
        log.info("Practice summary note posted: jobId={}, commentId={}", job.getId(), commentId);
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

    // Formatting

    static String formatPracticeNote(String sanitizedBody, AgentJob job) {
        var sb = new StringBuilder(sanitizedBody.length() + 512);
        sb.append("<!-- hephaestus:practice-review:").append(job.getId()).append(" -->\n");
        sb.append(sanitizedBody).append("\n\n");
        appendFooter(sb, job);
        return sb.toString();
    }

    private static void appendFooter(StringBuilder sb, AgentJob job) {
        sb.append("---\n");
        PullRequestCommentPoster.appendMetadataFooter(sb, job);
    }
}
