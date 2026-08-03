package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ExistingDeliveryLookup;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliverySuppressedException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationTrendService;
import de.tum.cit.aet.hephaestus.practices.observation.TrendDelta;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates policy-gated delivery of practice-review summaries and inline feedback. */
class FeedbackDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackDeliveryService.class);

    private final PullRequestCommentPoster commentPoster;
    private final DiffNotePoster diffNotePoster;
    private final PracticeFeedbackDeliveryPolicy deliveryPolicy;
    private final PracticeReviewProperties reviewProperties;
    private final FeedbackLedgerRecorder feedbackLedgerRecorder;
    private final ObservationTrendService observationTrendService;
    private final PracticeFeedbackCommentFormatter commentFormatter;

    FeedbackDeliveryService(
        PullRequestCommentPoster commentPoster,
        DiffNotePoster diffNotePoster,
        PracticeFeedbackDeliveryPolicy deliveryPolicy,
        PracticeReviewProperties reviewProperties,
        FeedbackLedgerRecorder feedbackLedgerRecorder,
        ObservationTrendService observationTrendService,
        PracticeFeedbackCommentFormatter commentFormatter
    ) {
        this.commentPoster = commentPoster;
        this.diffNotePoster = diffNotePoster;
        this.deliveryPolicy = deliveryPolicy;
        this.reviewProperties = reviewProperties;
        this.feedbackLedgerRecorder = feedbackLedgerRecorder;
        this.observationTrendService = observationTrendService;
        this.commentFormatter = commentFormatter;
    }

    void deliverFeedback(AgentJob job, @Nullable DeliveryContent delivery) {
        deliverFeedback(job, delivery, null);
    }

    ExistingDeliveryLookup findExistingDeliveryCommentId(AgentJob job) {
        return commentPoster.findExistingSummaryComment(job);
    }

    @FunctionalInterface
    interface InlineAwareSummaryComposer {
        @Nullable
        String compose(Set<String> deliveredObservationFingerprints);
    }

    void deliverFeedback(
        AgentJob job,
        @Nullable DeliveryContent delivery,
        @Nullable InlineAwareSummaryComposer summaryComposer
    ) {
        if (delivery == null) {
            log.debug("No delivery content, skipping: jobId={}", job.getId());
            return;
        }

        PracticeFeedbackDeliveryPolicy.Decision<PullRequest> decision = deliveryPolicy.evaluatePullRequest(job);
        if (!decision.allowed()) {
            if (decision.suppressionReason() != null) {
                log.info("Delivery suppressed: reason={}, jobId={}", decision.suppressionReason(), job.getId());
                recordGateSuppressed(job, delivery, decision.suppressionReason());
            } else {
                log.info("Delivery disabled for workspace: jobId={}", job.getId());
            }
            return;
        }

        try {
            doDeliverEligible(job, delivery, summaryComposer, decision.artifact());
        } catch (JobDeliverySuppressedException e) {
            log.info("Delivery suppressed at egress: jobId={}", job.getId());
            recordGateSuppressed(job, delivery, FeedbackSuppressionReason.INSTANCE_SILENCED);
        } catch (JobDeliveryException e) {
            if (job.getDeliveryCommentId() == null) {
                recordUndelivered(job, delivery);
            }
            throw e;
        } catch (Exception e) {
            log.warn("Feedback delivery failed (non-fatal): jobId={}", job.getId(), e);
        }
    }

    private void recordPartialSummaryDelivery(AgentJob job, DeliveryContent delivery) {
        try {
            feedbackLedgerRecorder.recordWithoutConversation(
                job,
                delivery,
                WorkArtifact.PULL_REQUEST,
                List.of(),
                true,
                false
            );
        } catch (RuntimeException e) {
            log.warn("Partial delivery ledger record failed: jobId={}, error={}", job.getId(), e.getMessage());
        }
    }

    private void doDeliverEligible(
        AgentJob job,
        DeliveryContent delivery,
        @Nullable InlineAwareSummaryComposer summaryComposer,
        PullRequest pullRequest
    ) {
        TrendDelta trend = reviewProperties.progressFooter()
            ? observationTrendService
                  .computeForTarget(WorkArtifact.PULL_REQUEST, pullRequest.getId(), job.getWorkspace().getId())
                  .orElse(null)
            : null;

        SummaryOutcome summaryOutcome = postSummaryNote(job, delivery, trend);
        DiffNotePoster.DiffNoteResult inlineResult;
        try {
            inlineResult = postDiffNotes(job, delivery);
        } catch (JobDeliverySuppressedException e) {
            log.info("Inline delivery suppressed at egress: jobId={}", job.getId());
            if (summaryOutcome == SummaryOutcome.DELIVERED) {
                recordPartialSummaryDelivery(job, delivery);
                recordSuppressedRemainder(job, delivery, List.of());
            } else {
                recordGateSuppressed(job, delivery, FeedbackSuppressionReason.INSTANCE_SILENCED);
            }
            return;
        }
        List<InlineFindingChannel.DeliveredSignal> inlineSignals = inlineResult.signals();

        if (summaryOutcome == SummaryOutcome.DELIVERED && !inlineResult.suppressed()) {
            reEditSummaryWithSignals(job, summaryComposer, inlineSignals, trend);
        }
        boolean inlineDelivered = inlineResult.posted() > 0;
        if (inlineResult.suppressed() && summaryOutcome != SummaryOutcome.DELIVERED && !inlineDelivered) {
            recordGateSuppressed(job, delivery, FeedbackSuppressionReason.INSTANCE_SILENCED);
            return;
        }
        if (summaryOutcome == SummaryOutcome.SKIPPED_EMPTY && !inlineDelivered) {
            recordGateSuppressed(job, delivery, FeedbackSuppressionReason.EMPTY_AFTER_SANITIZE);
            return;
        }
        if (summaryOutcome == SummaryOutcome.NOT_REQUIRED && !inlineDelivered) {
            return;
        }

        try {
            if (inlineResult.suppressed()) {
                feedbackLedgerRecorder.recordWithoutConversation(
                    job,
                    delivery,
                    WorkArtifact.PULL_REQUEST,
                    inlineSignals,
                    summaryOutcome == SummaryOutcome.DELIVERED,
                    inlineDelivered
                );
            } else {
                feedbackLedgerRecorder.record(
                    job,
                    delivery,
                    WorkArtifact.PULL_REQUEST,
                    inlineSignals,
                    summaryOutcome == SummaryOutcome.DELIVERED,
                    inlineDelivered
                );
            }
        } catch (RuntimeException e) {
            log.warn(
                "Feedback ledger record failed (delivery unaffected): jobId={}, error={}",
                job.getId(),
                e.getMessage()
            );
        }
        if (inlineResult.suppressed()) {
            recordSuppressedRemainder(job, delivery, inlineResult.suppressedRecurrenceKeys());
        }
    }

    private void recordSuppressedRemainder(
        AgentJob job,
        DeliveryContent delivery,
        List<String> suppressedRecurrenceKeys
    ) {
        try {
            feedbackLedgerRecorder.recordSuppressedRemainder(
                job,
                delivery,
                FeedbackSuppressionReason.INSTANCE_SILENCED,
                suppressedRecurrenceKeys
            );
        } catch (RuntimeException e) {
            log.warn("Suppressed delivery ledger record failed: jobId={}, error={}", job.getId(), e.getMessage());
        }
    }

    private void recordGateSuppressed(
        AgentJob job,
        DeliveryContent delivery,
        @Nullable FeedbackSuppressionReason reason
    ) {
        if (reason == null) {
            throw new IllegalArgumentException("Suppressed delivery requires a reason");
        }
        try {
            feedbackLedgerRecorder.recordSuppressedUnit(job, delivery, reason);
        } catch (RuntimeException e) {
            log.warn(
                "Gate-suppressed ledger record failed (delivery unaffected): jobId={}, reason={}, error={}",
                job.getId(),
                reason,
                e.getMessage()
            );
        }
    }

    private enum SummaryOutcome {
        DELIVERED,
        NOT_REQUIRED,
        TRANSIENT_NOOP,
        SKIPPED_EMPTY,
    }

    private SummaryOutcome postSummaryNote(AgentJob job, DeliveryContent delivery, @Nullable TrendDelta trend) {
        if (delivery.mrNote() == null) {
            return SummaryOutcome.NOT_REQUIRED;
        }
        String sanitized = PullRequestCommentPoster.sanitize(delivery.mrNote());
        if (sanitized.isBlank()) {
            log.debug("Practice note was empty after sanitization, skipping post: jobId={}", job.getId());
            return SummaryOutcome.SKIPPED_EMPTY;
        }

        String footer = ProgressFooterRenderer.render(trend);
        String body = footer.isEmpty() ? sanitized : sanitized + "\n\n" + footer;
        String formatted = commentFormatter.format(body, job);
        String priorRef = feedbackLedgerRecorder.priorLiveSummaryRef(job).orElse(null);
        PullRequestCommentPoster.UpdateResult update =
            priorRef == null ? null : commentPoster.updateFormattedBody(job, priorRef, formatted);

        if (update != null && update.kind() == PullRequestCommentPoster.UpdateResult.Kind.TRANSIENT) {
            // A transient edit failure must not create a duplicate summary.
            job.setDeliveryCommentId(priorRef);
            log.warn(
                "Summary edit transient — kept prior summary, no fresh post: jobId={}, commentId={}",
                job.getId(),
                priorRef
            );
            return SummaryOutcome.TRANSIENT_NOOP;
        }

        boolean editedInPlace = update != null && update.kind() == PullRequestCommentPoster.UpdateResult.Kind.EDITED;
        String commentId = editedInPlace ? update.externalId() : commentPoster.postFormattedBody(job, formatted);
        if (commentId == null) {
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
        if (editedInPlace && trend != null && trend.hasMeaningfulChange()) {
            postReReviewPing(job, trend);
        }
        return SummaryOutcome.DELIVERED;
    }

    private void reEditSummaryWithSignals(
        AgentJob job,
        @Nullable InlineAwareSummaryComposer summaryComposer,
        List<InlineFindingChannel.DeliveredSignal> inlineSignals,
        @Nullable TrendDelta trend
    ) {
        String summaryRef = job.getDeliveryCommentId();
        if (summaryComposer == null || summaryRef == null) {
            return;
        }
        Set<String> deliveredKeys = inlineSignals
            .stream()
            .filter(signal -> signal.disposition() != InlineFindingChannel.Disposition.FAILED)
            .map(InlineFindingChannel.DeliveredSignal::recurrenceKey)
            .filter(key -> key != null && !key.isBlank())
            .collect(Collectors.toSet());
        if (deliveredKeys.isEmpty()) {
            return;
        }

        String demoted = summaryComposer.compose(deliveredKeys);
        if (demoted == null) {
            return;
        }
        String sanitized = PullRequestCommentPoster.sanitize(demoted);
        if (sanitized.isBlank()) {
            return;
        }
        String footer = ProgressFooterRenderer.render(trend);
        String body = footer.isEmpty() ? sanitized : sanitized + "\n\n" + footer;
        String formatted = commentFormatter.format(body, job);

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

    private DiffNotePoster.DiffNoteResult postDiffNotes(AgentJob job, DeliveryContent delivery) {
        // Empty reconciliation must still remove stale inline notes after policy guards pass.
        DiffNotePoster.DiffNoteResult diffResult = diffNotePoster.reconcileInlineNotes(job, delivery.diffNotes());
        log.info(
            "Diff notes reconciled: posted={}, failed={}, total={}, jobId={}",
            diffResult.posted(),
            diffResult.failed(),
            delivery.diffNotes().size(),
            job.getId()
        );
        return diffResult;
    }

    private void recordUndelivered(AgentJob job, DeliveryContent delivery) {
        try {
            feedbackLedgerRecorder.recordUndelivered(job, delivery);
        } catch (RuntimeException e) {
            log.warn(
                "Undelivered-feedback ledger record failed (delivery unaffected): jobId={}, error={}",
                job.getId(),
                e.getMessage()
            );
        }
    }
}
