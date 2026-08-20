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
        deliverFeedback(job, delivery, null, Set.of());
    }

    void recordProposal(
        AgentJob job,
        @Nullable DeliveryContent delivery,
        List<PracticeDetectionResultParser.ValidatedObservation> observations
    ) {
        feedbackLedgerRecorder.recordProposal(job, delivery, observations);
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
        @Nullable InlineAwareSummaryComposer summaryComposer,
        Set<String> contributingPracticeSlugs
    ) {
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
            if (decision.suppressionReason() != null) {
                log.info("Delivery suppressed: reason={}, jobId={}", decision.suppressionReason(), job.getId());
                recordGateSuppressed(job, delivery, decision.suppressionReason());
            } else {
                log.info("Delivery disabled for workspace: jobId={}", job.getId());
            }
            return;
        }

        try {
            doDeliverEligible(job, delivery, summaryComposer, decision.artifact(), contributingPracticeSlugs);
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
        @Nullable InlineAwareSummaryComposer summaryComposer,
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

        if (summaryOutcome == SummaryOutcome.DELIVERED && !inlineResult.suppressed()) {
            reEditSummaryWithSignals(job, summaryComposer, inlineSignals, trend, contributingPracticeSlugs);
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
        String priorRef = feedbackLedgerRecorder.priorLiveSummaryRef(job).orElse(null);
        PracticeFeedbackDispatchService.Result result = dispatchService.dispatchAutomaticSummary(
            job,
            formatted,
            priorRef,
            contributingPracticeSlugs
        );
        if (result.status() == PracticeFeedbackDispatchService.Result.Status.SUPPRESSED) {
            throw new DispatchPolicySuppressedException(
                result.suppressionReason() == null
                    ? FeedbackSuppressionReason.INSTANCE_SILENCED
                    : result.suppressionReason()
            );
        }
        if (result.status() == PracticeFeedbackDispatchService.Result.Status.UNCERTAIN && priorRef != null) {
            job.setDeliveryCommentId(priorRef);
            return SummaryOutcome.TRANSIENT_NOOP;
        }
        if (result.status() != PracticeFeedbackDispatchService.Result.Status.SENT || result.externalRef() == null) {
            throw new JobDeliveryException("Summary dispatch is awaiting reconciliation: jobId=" + job.getId());
        }
        String commentId = result.externalRef();
        job.setDeliveryCommentId(commentId);
        boolean editedInPlace = priorRef != null && priorRef.equals(commentId);
        log.info(
            "Practice summary note delivered: jobId={}, commentId={}, editedInPlace={}",
            job.getId(),
            commentId,
            editedInPlace
        );
        if (editedInPlace && trend != null && trend.hasMeaningfulChange()) {
            postReReviewPing(job, trend, contributingPracticeSlugs);
        }
        return SummaryOutcome.DELIVERED;
    }

    private static final class DispatchPolicySuppressedException extends RuntimeException {

        private final FeedbackSuppressionReason reason;

        private DispatchPolicySuppressedException(FeedbackSuppressionReason reason) {
            super("Policy suppressed the persisted summary dispatch: " + reason);
            this.reason = reason;
        }
    }

    private void reEditSummaryWithSignals(
        AgentJob job,
        @Nullable InlineAwareSummaryComposer summaryComposer,
        List<InlineFeedbackChannel.DeliveredSignal> inlineSignals,
        @Nullable TrendDelta trend,
        Set<String> contributingPracticeSlugs
    ) {
        String summaryRef = job.getDeliveryCommentId();
        if (summaryComposer == null || summaryRef == null) {
            return;
        }
        Set<String> deliveredKeys = inlineSignals
            .stream()
            .filter(signal -> signal.disposition() != InlineFeedbackChannel.Disposition.FAILED)
            .map(InlineFeedbackChannel.DeliveredSignal::recurrenceKey)
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
            if (
                !deliveryPolicy
                    .evaluatePullRequest(job, DeliveryPolicyStage.EGRESS, null, contributingPracticeSlugs)
                    .allowed()
            ) return;
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

    private void postReReviewPing(AgentJob job, TrendDelta trend, Set<String> contributingPracticeSlugs) {
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
            if (
                !deliveryPolicy
                    .evaluatePullRequest(job, DeliveryPolicyStage.EGRESS, null, contributingPracticeSlugs)
                    .allowed()
            ) return;
            String pingId = commentPoster.postFormattedBody(job, body);
            log.info("Re-review ping posted: jobId={}, pingCommentId={}", job.getId(), pingId);
        } catch (RuntimeException e) {
            log.warn("Re-review ping failed (delivery unaffected): jobId={}, error={}", job.getId(), e.getMessage());
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
            throw new DispatchPolicySuppressedException(decision.suppressionReason());
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
