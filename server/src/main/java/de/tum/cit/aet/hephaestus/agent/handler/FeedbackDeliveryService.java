package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.account.UserPreferencesRepository;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.practices.finding.FindingTrendService;
import de.tum.cit.aet.hephaestus.practices.finding.TrendDelta;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewSettings;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delivers practice review feedback to PRs/MRs.
 *
 * <p>The MR summary is EDITED IN PLACE across re-reviews (ADR 0021 re-review UX): a re-reviewed PR keeps
 * one evolving overview comment rather than accumulating a fresh one per run, falling back to a new post
 * only when the prior comment is gone. Inline diff notes are reconciled separately. Findings are already
 * persisted by {@link PracticeDetectionDeliveryService} before this is called.
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
    private final FeedbackLedgerRecorder feedbackLedgerRecorder;
    private final FindingTrendService findingTrendService;

    FeedbackDeliveryService(
        PullRequestCommentPoster commentPoster,
        DiffNotePoster diffNotePoster,
        UserPreferencesRepository userPreferencesRepository,
        PullRequestRepository pullRequestRepository,
        WorkspaceRepository workspaceRepository,
        PracticeReviewProperties reviewProperties,
        FeedbackLedgerRecorder feedbackLedgerRecorder,
        FindingTrendService findingTrendService
    ) {
        this.commentPoster = commentPoster;
        this.diffNotePoster = diffNotePoster;
        this.userPreferencesRepository = userPreferencesRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.workspaceRepository = workspaceRepository;
        this.reviewProperties = reviewProperties;
        this.feedbackLedgerRecorder = feedbackLedgerRecorder;
        this.findingTrendService = findingTrendService;
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

        // Cross-run trend (ADR 0021, B1/B3/A4) — flag-gated; needs ≥2 runs to render anything. Computed here
        // (the target + workspace are known) and threaded into the summary so DeliveryComposer stays pure.
        TrendDelta trend = reviewProperties.progressFooter()
            ? findingTrendService
                  .computeForTarget(WorkArtifact.PULL_REQUEST, pullRequestId, job.getWorkspace().getId())
                  .orElse(null)
            : null;

        // Always post new

        postSummaryNote(job, delivery, trend);
        postDiffNotes(job, delivery);

        // Record the delivered-feedback ledger (ADR 0021 C6) as a best-effort write-through side-effect:
        // REQUIRES_NEW inside the recorder + this try/catch mean a ledger failure can never alter or roll
        // back the delivery the contributor already received.
        try {
            feedbackLedgerRecorder.record(job, delivery, WorkArtifact.PULL_REQUEST);
        } catch (RuntimeException e) {
            log.warn(
                "Feedback ledger record failed (delivery unaffected): jobId={}, error={}",
                job.getId(),
                e.getMessage()
            );
        }
    }

    private void postSummaryNote(AgentJob job, DeliveryContent delivery, @Nullable TrendDelta trend) {
        if (delivery.mrNote() == null) {
            return;
        }
        String sanitized = PullRequestCommentPoster.sanitize(delivery.mrNote());
        if (sanitized.isBlank()) {
            log.debug("Practice note was empty after sanitization, skipping post: jobId={}", job.getId());
            return;
        }
        // B1/B3: append the collapsed progress-delta footer (empty string when nothing meaningfully changed).
        String footer = ProgressFooterRenderer.render(trend);
        String body = footer.isEmpty() ? sanitized : sanitized + "\n\n" + footer;
        String formatted = formatPracticeNote(body, job);

        // Re-review UX (ADR 0021): edit the persistent summary IN PLACE across re-reviews so the PR keeps ONE
        // evolving overview comment instead of accumulating a fresh one each run (the Qodo persistent_comment /
        // CodeRabbit model). The recorder returns the current live summary's comment id for this continuity
        // line; if an edit can't land (append-only channel, or a human deleted the prior comment) we fall back
        // to a new post. The recorder supersedes the prior ledger unit when it records this run.
        String priorRef = feedbackLedgerRecorder.priorLiveSummaryRef(job).orElse(null);
        String commentId = priorRef != null ? commentPoster.updateFormattedBody(job, priorRef, formatted) : null;
        boolean editedInPlace = commentId != null;
        if (commentId == null) {
            commentId = commentPoster.postFormattedBody(job, formatted);
        }
        if (commentId == null) {
            // We had a real, non-blank summary to post but the provider returned no comment id —
            // the contributor sees nothing. Treat as an integrity failure so the job is marked FAILED.
            throw new JobDeliveryException(
                "Summary note post returned no comment id despite a non-empty body: jobId=" + job.getId()
            );
        }
        job.setDeliveryCommentId(commentId);
        log.info(
            "Practice summary note delivered: jobId={}, commentId={}, editedInPlace={}",
            job.getId(),
            commentId,
            editedInPlace
        );

        // A4: an edit-in-place pings nobody, so a re-review that actually moved the needle would be invisible.
        // When (and only when) we edited a prior summary AND the finding set meaningfully changed, post ONE
        // short notifying note — the edit keeps the canonical single overview, this generates the one
        // notification that matters. Byte-identical / no-change re-reviews stay silent.
        if (editedInPlace && trend != null && trend.hasMeaningfulChange()) {
            postReReviewPing(job, trend);
        }
    }

    /** A4: a short, marker-tagged notifying note pointing at the freshly-edited summary. Best-effort. */
    private void postReReviewPing(AgentJob job, TrendDelta trend) {
        List<String> parts = new ArrayList<>();
        if (trend.countResolved() > 0) {
            parts.add(trend.countResolved() + " resolved");
        }
        if (trend.countNew() > 0) {
            parts.add(trend.countNew() + " new");
        }
        if (trend.countRegressed() > 0) {
            parts.add(trend.countRegressed() + " slipped back");
        }
        String body =
            "<!-- hephaestus:re-review-ping:" +
            job.getId() +
            " -->\n🔁 **Re-reviewed** — " +
            String.join(", ", parts) +
            ". See the updated review summary above.";
        try {
            String pingId = commentPoster.postFormattedBody(job, body);
            log.info("Re-review ping posted: jobId={}, pingCommentId={}", job.getId(), pingId);
        } catch (RuntimeException e) {
            log.warn("Re-review ping failed (delivery unaffected): jobId={}, error={}", job.getId(), e.getMessage());
        }
    }

    private void postDiffNotes(AgentJob job, DeliveryContent delivery) {
        // NO empty-guard: a run that now produces zero inline notes must still RECONCILE — clearing this
        // run's stale notes from an earlier review (the empty-diff pathology). reconcileInlineNotes clears
        // first, then posts the (possibly empty) fresh set. Reached only AFTER the suppression guards above,
        // so a closed/merged/draft/opted-out PR is never wiped.
        DiffNotePoster.DiffNoteResult diffResult = diffNotePoster.reconcileInlineNotes(job, delivery.diffNotes());
        log.info(
            "Diff notes reconciled: posted={}, failed={}, total={}, jobId={}",
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
