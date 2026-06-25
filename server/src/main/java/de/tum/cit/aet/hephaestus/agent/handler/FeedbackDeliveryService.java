package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.account.UserPreferencesRepository;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationTrendService;
import de.tum.cit.aet.hephaestus.practices.observation.TrendDelta;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final ObservationTrendService observationTrendService;

    FeedbackDeliveryService(
        PullRequestCommentPoster commentPoster,
        DiffNotePoster diffNotePoster,
        UserPreferencesRepository userPreferencesRepository,
        PullRequestRepository pullRequestRepository,
        WorkspaceRepository workspaceRepository,
        PracticeReviewProperties reviewProperties,
        FeedbackLedgerRecorder feedbackLedgerRecorder,
        ObservationTrendService observationTrendService
    ) {
        this.commentPoster = commentPoster;
        this.diffNotePoster = diffNotePoster;
        this.userPreferencesRepository = userPreferencesRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.workspaceRepository = workspaceRepository;
        this.reviewProperties = reviewProperties;
        this.feedbackLedgerRecorder = feedbackLedgerRecorder;
        this.observationTrendService = observationTrendService;
    }

    /**
     * Delivers feedback to the PR/MR.
     *
     * <p>Two failure classes are treated differently. An <em>integrity</em> failure — null
     * {@code integrationKind}, no {@link de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel}
     * wired, or the summary post returning no id — means the developer sees <em>nothing</em> despite
     * the agent having run (LLM cost incurred). Those surface as a {@link JobDeliveryException} so the
     * executor marks the job FAILED instead of silently reporting "DELIVERED". <em>Cosmetic</em>
     * failures (e.g. one diff note that fell outside a hunk) stay soft and are only logged.
     */
    void deliverFeedback(AgentJob job, @Nullable DeliveryContent delivery) {
        deliverFeedback(job, delivery, null);
    }

    /**
     * Recomputes the MR summary body once the per-finding inline-delivery signals are known, demoting every
     * inlinable finding whose inline comment actually landed to a "see inline comments" pointer (its detail
     * already lives on the diff) while a finding whose note failed keeps its full summary line. Bound by the
     * caller over the structured findings it composed from, so this service never sees them directly — the
     * summary recomposition stays in {@link DeliveryComposer} and this service only feeds it the delivered
     * keys and re-edits in place.
     */
    @FunctionalInterface
    interface SummaryRecomposer {
        /** @return the demoted summary body for the given delivered correlation keys, or {@code null} when there is none. */
        @Nullable
        String recompose(Set<String> deliveredObservationFingerprints);
    }

    void deliverFeedback(AgentJob job, @Nullable DeliveryContent delivery, @Nullable SummaryRecomposer recomposer) {
        try {
            doDeliver(job, delivery, recomposer);
        } catch (JobDeliveryException e) {
            // Integrity failure — propagate so the job is marked FAILED, not silently DELIVERED.
            throw e;
        } catch (Exception e) {
            log.warn("Feedback delivery failed (non-fatal): jobId={}", job.getId(), e);
        }
    }

    private void doDeliver(AgentJob job, @Nullable DeliveryContent delivery, @Nullable SummaryRecomposer recomposer) {
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
            ? observationTrendService
                  .computeForTarget(WorkArtifact.PULL_REQUEST, pullRequestId, job.getWorkspace().getId())
                  .orElse(null)
            : null;

        // Always post new

        postSummaryNote(job, delivery, trend);
        List<InlineFindingChannel.DeliveredSignal> inlineSignals = postDiffNotes(job, delivery);

        // The summary was composed+posted BEFORE the inline notes (the order the ledger + A4 ping depend on),
        // so its inline section listed every finding's full line. Now that the inline signals are known, demote
        // the findings whose inline comment actually landed to a "see inline comments" pointer by re-editing the
        // same summary in place (B4-safe updateFormattedBody). A finding whose note failed keeps its full line.
        reEditSummaryWithSignals(job, recomposer, inlineSignals, trend);

        // Record the delivered-feedback ledger (ADR 0021 C6) as a best-effort write-through side-effect:
        // REQUIRES_NEW inside the recorder + this try/catch mean a ledger failure can never alter or roll
        // back the delivery the developer already received. The inline signals carry each posted note's
        // durable handle (external_ref / thread_external_ref / disposition) so the placement rows record
        // what actually landed rather than an assumed POSTED + null.
        try {
            feedbackLedgerRecorder.record(job, delivery, WorkArtifact.PULL_REQUEST, inlineSignals);
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
        // line; the typed UpdateResult decides what happens when the edit can't land — crucially, a TRANSIENT
        // failure must NOT create-fallback (that double-posts a second summary), only a confirmed-gone one does.
        String priorRef = feedbackLedgerRecorder.priorLiveSummaryRef(job).orElse(null);
        PullRequestCommentPoster.UpdateResult update =
            priorRef != null ? commentPoster.updateFormattedBody(job, priorRef, formatted) : null;

        if (update != null && update.kind() == PullRequestCommentPoster.UpdateResult.Kind.TRANSIENT) {
            // The edit hit a recoverable error (rate limit / network). Keep the still-live prior summary; do NOT
            // post a fresh one. Nothing new is delivered this run — and an unchanged comment pings nobody, so the
            // A4 ping must stay silent too.
            job.setDeliveryCommentId(priorRef);
            log.warn(
                "Summary edit transient — kept prior summary, no fresh post: jobId={}, commentId={}",
                job.getId(),
                priorRef
            );
            return;
        }

        boolean editedInPlace = update != null && update.kind() == PullRequestCommentPoster.UpdateResult.Kind.EDITED;
        String commentId = editedInPlace ? update.externalId() : commentPoster.postFormattedBody(job, formatted);
        if (commentId == null) {
            // We had a real, non-blank summary to post but the provider returned no comment id —
            // the developer sees nothing. Treat as an integrity failure so the job is marked FAILED.
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

    /**
     * Demotes the just-posted summary's inline section in place once the inline-delivery signals are known.
     * Collects the correlation keys whose inline note actually LANDED — every disposition except
     * {@link InlineFindingChannel.Disposition#FAILED} carries a posted comment — asks the recomposer for the
     * demoted body, and edits the live summary comment in place via the B4-safe path. Best-effort and
     * narrowly guarded: a no-op (no recomposer, no posted summary id, no delivered key, an unchanged body, or
     * an edit that does not land) leaves the already-delivered full-line summary exactly as posted.
     */
    private void reEditSummaryWithSignals(
        AgentJob job,
        @Nullable SummaryRecomposer recomposer,
        List<InlineFindingChannel.DeliveredSignal> inlineSignals,
        @Nullable TrendDelta trend
    ) {
        String summaryRef = job.getDeliveryCommentId();
        if (recomposer == null || summaryRef == null) {
            return;
        }
        Set<String> deliveredKeys = inlineSignals
            .stream()
            .filter(s -> s.disposition() != InlineFindingChannel.Disposition.FAILED)
            .map(InlineFindingChannel.DeliveredSignal::findingFingerprint)
            .filter(key -> key != null && !key.isBlank())
            .collect(Collectors.toSet());
        if (deliveredKeys.isEmpty()) {
            // Nothing landed inline → no finding can be demoted; the full-line summary already posted is correct.
            return;
        }

        String demoted = recomposer.recompose(deliveredKeys);
        if (demoted == null) {
            return;
        }
        String sanitized = PullRequestCommentPoster.sanitize(demoted);
        if (sanitized.isBlank()) {
            return;
        }
        // Wrap identically to the first post (same footer + marker envelope) so only the inline section differs.
        String footer = ProgressFooterRenderer.render(trend);
        String body = footer.isEmpty() ? sanitized : sanitized + "\n\n" + footer;
        String formatted = formatPracticeNote(body, job);

        try {
            PullRequestCommentPoster.UpdateResult update = commentPoster.updateFormattedBody(
                job,
                summaryRef,
                formatted
            );
            if (update.kind() == PullRequestCommentPoster.UpdateResult.Kind.EDITED) {
                log.info(
                    "Summary demoted in place after inline delivery: jobId={}, commentId={}",
                    job.getId(),
                    summaryRef
                );
            } else {
                // GONE/TRANSIENT/UNSUPPORTED: the demotion is cosmetic, so keep the full-line summary already
                // delivered rather than re-posting a second comment. Logged for diagnosis, never fatal.
                log.debug(
                    "Summary demotion did not land ({}); keeping full-line summary: jobId={}",
                    update.kind(),
                    job.getId()
                );
            }
        } catch (RuntimeException e) {
            log.warn("Summary demotion failed (delivery unaffected): jobId={}, error={}", job.getId(), e.getMessage());
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

    /**
     * Reconciles this run's inline notes and returns the per-finding delivery signals so the ledger can
     * persist each placement's durable handle. NO empty-guard: a run that now produces zero inline notes must
     * still RECONCILE — clearing this run's stale notes from an earlier review (the empty-diff pathology).
     * reconcileInlineNotes clears first, then posts the (possibly empty) fresh set. Reached only AFTER the
     * suppression guards above, so a closed/merged/draft/opted-out PR is never wiped.
     */
    private List<InlineFindingChannel.DeliveredSignal> postDiffNotes(AgentJob job, DeliveryContent delivery) {
        DiffNotePoster.DiffNoteResult diffResult = diffNotePoster.reconcileInlineNotes(job, delivery.diffNotes());
        log.info(
            "Diff notes reconciled: posted={}, failed={}, total={}, jobId={}",
            diffResult.posted(),
            diffResult.failed(),
            delivery.diffNotes().size(),
            job.getId()
        );
        return diffResult.signals();
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
