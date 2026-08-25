package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ExistingDeliveryLookup;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliverySuppressedException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyStage;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationTrendService;
import de.tum.cit.aet.hephaestus.practices.observation.TrendDelta;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import java.util.List;
import java.util.Set;
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
    private final PracticeFeedbackDispatchService dispatchService;

    FeedbackDeliveryService(
        PullRequestCommentPoster commentPoster,
        DiffNotePoster diffNotePoster,
        PracticeFeedbackDeliveryPolicy deliveryPolicy,
        PracticeReviewProperties reviewProperties,
        FeedbackLedgerRecorder feedbackLedgerRecorder,
        ObservationTrendService observationTrendService,
        PracticeFeedbackCommentFormatter commentFormatter,
        PracticeFeedbackDispatchService dispatchService
    ) {
        this.commentPoster = commentPoster;
        this.diffNotePoster = diffNotePoster;
        this.deliveryPolicy = deliveryPolicy;
        this.reviewProperties = reviewProperties;
        this.feedbackLedgerRecorder = feedbackLedgerRecorder;
        this.observationTrendService = observationTrendService;
        this.commentFormatter = commentFormatter;
        this.dispatchService = dispatchService;
    }

    void deliverFeedback(AgentJob job, @Nullable DeliveryContent delivery) {
        deliverFeedback(job, delivery, Set.of());
    }

    void recordProposal(
        AgentJob job,
        @Nullable DeliveryContent delivery,
        List<PracticeDetectionResultParser.ValidatedObservation> observations
    ) {
        feedbackLedgerRecorder.recordProposal(job, delivery, observations);
    }

    ExistingDeliveryLookup findExistingSummary(AgentJob job) {
        return commentPoster.findExistingSummaryComment(job);
    }

    void deliverFeedback(AgentJob job, @Nullable DeliveryContent delivery, Set<String> contributingPracticeSlugs) {
        if (delivery == null) {
            log.debug("No delivery content, skipping: jobId={}", job.getId());
            return;
        }

        PracticeFeedbackDeliveryPolicy.Decision<PullRequest> decision = deliveryPolicy.evaluatePullRequest(
            job,
            DeliveryPolicyStage.AUTOMATIC,
            null,
            contributingPracticeSlugs
        );
        if (!decision.allowed()) {
            if (decision.refusal() != null) {
                log.info("Delivery suppressed: reason={}, jobId={}", decision.refusal(), job.getId());
                recordGateSuppressed(job, delivery, decision.refusal());
            } else {
                log.info("Delivery disabled for workspace: jobId={}", job.getId());
            }
            return;
        }

        try {
            doDeliverEligible(job, delivery, decision.target(), contributingPracticeSlugs);
        } catch (DispatchPolicySuppressedException e) {
            log.info("Delivery suppressed by current dispatch policy: reason={}, jobId={}", e.reason, job.getId());
            recordGateSuppressed(job, delivery, e.reason);
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
                ArtifactKinds.PULL_REQUEST,
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
        PullRequest pullRequest,
        Set<String> contributingPracticeSlugs
    ) {
        TrendDelta trend = reviewProperties.progressFooter()
            ? observationTrendService
                  .computeForTarget(ArtifactKinds.PULL_REQUEST, pullRequest.getId(), job.getWorkspace().getId())
                  .orElse(null)
            : null;

        SummaryOutcome summaryOutcome = postSummaryNote(job, delivery, trend, contributingPracticeSlugs);
        DiffNotePoster.DiffNoteResult inlineResult;
        try {
            inlineResult = postDiffNotes(job, delivery, contributingPracticeSlugs);
        } catch (JobDeliverySuppressedException | DispatchPolicySuppressedException e) {
            FeedbackSuppressionReason reason =
                e instanceof DispatchPolicySuppressedException policySuppressed
                    ? policySuppressed.reason
                    : FeedbackSuppressionReason.INSTANCE_SILENCED;
            log.info("Inline delivery suppressed at egress: reason={}, jobId={}", reason, job.getId());
            if (summaryOutcome == SummaryOutcome.DELIVERED) {
                recordPartialSummaryDelivery(job, delivery);
                recordSuppressedRemainder(job, delivery, List.of());
            } else {
                recordGateSuppressed(job, delivery, reason);
            }
            return;
        }
        List<InlineFeedbackChannel.DeliveredSignal> inlineSignals = inlineResult.signals();

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
                    ArtifactKinds.PULL_REQUEST,
                    inlineSignals,
                    summaryOutcome == SummaryOutcome.DELIVERED,
                    inlineDelivered
                );
            } else {
                feedbackLedgerRecorder.record(
                    job,
                    delivery,
                    ArtifactKinds.PULL_REQUEST,
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
        if (inlineResult.failed() > 0) {
            // Raised last, so the ledger and both dispatches are already durable and a retry re-runs the
            // inline reconcile alone. The reconcile is keyed by recurrence, so re-running it posts nothing
            // twice — but nothing else retries it, and a note dropped in silence never arrives at all.
            throw new JobDeliveryException(
                "Inline notes were not fully delivered: failed=" + inlineResult.failed() + ", jobId=" + job.getId()
            );
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
        SKIPPED_EMPTY,
    }

    private SummaryOutcome postSummaryNote(
        AgentJob job,
        DeliveryContent delivery,
        @Nullable TrendDelta trend,
        Set<String> contributingPracticeSlugs
    ) {
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
        PracticeFeedbackDispatchService.Result result = dispatchService.dispatchAutomaticSummary(
            job,
            formatted,
            null,
            contributingPracticeSlugs
        );
        if (result.status() == PracticeFeedbackDispatchService.Result.Status.SUPPRESSED) {
            throw new DispatchPolicySuppressedException(result.refusal());
        }
        if (result.status() != PracticeFeedbackDispatchService.Result.Status.SENT || result.externalRef() == null) {
            throw new JobDeliveryException("Summary dispatch is awaiting reconciliation: jobId=" + job.getId());
        }
        String commentId = result.externalRef();
        job.setDeliveryCommentId(commentId);
        log.info("Practice summary note delivered: jobId={}, commentId={}", job.getId(), commentId);
        return SummaryOutcome.DELIVERED;
    }

    private static final class DispatchPolicySuppressedException extends RuntimeException {

        private final FeedbackSuppressionReason reason;

        private DispatchPolicySuppressedException(FeedbackSuppressionReason reason) {
            super("Policy suppressed the persisted summary dispatch: " + reason);
            this.reason = reason;
        }
    }

    private DiffNotePoster.DiffNoteResult postDiffNotes(
        AgentJob job,
        DeliveryContent delivery,
        Set<String> contributingPracticeSlugs
    ) {
        // Empty reconciliation must still remove stale inline notes after policy guards pass.
        PracticeFeedbackDeliveryPolicy.Decision<PullRequest> decision = deliveryPolicy.evaluatePullRequest(
            job,
            DeliveryPolicyStage.EGRESS,
            null,
            contributingPracticeSlugs
        );
        if (!decision.allowed()) {
            throw new DispatchPolicySuppressedException(decision.refusal());
        }
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
