package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.composition.FeedbackCompositionResultParser;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobService;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.agent.job.DeliveryStatus;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFeedbackChannel.DeliveredSignal;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyStage;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatch;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

class FeedbackDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackDeliveryService.class);

    private final PracticeFeedbackDeliveryPolicy deliveryPolicy;
    private final FeedbackLedgerRecorder feedbackLedgerRecorder;
    private final PracticeFeedbackCommentFormatter commentFormatter;
    private final PracticeFeedbackDispatchService dispatchService;
    private final AgentJobRepository agentJobRepository;
    private final PublicReviewCompletionRepository completions;

    FeedbackDeliveryService(
            PracticeFeedbackDeliveryPolicy deliveryPolicy,
            FeedbackLedgerRecorder feedbackLedgerRecorder,
            PracticeFeedbackCommentFormatter commentFormatter,
            PracticeFeedbackDispatchService dispatchService,
            AgentJobRepository agentJobRepository,
            PublicReviewCompletionRepository completions) {
        this.deliveryPolicy = deliveryPolicy;
        this.feedbackLedgerRecorder = feedbackLedgerRecorder;
        this.commentFormatter = commentFormatter;
        this.dispatchService = dispatchService;
        this.agentJobRepository = agentJobRepository;
        this.completions = completions;
    }

    /**
     * Whether the review on the work was already accepted while the job ran. Its outcome then went on its way with that
     * acceptance, so the terminal output neither decides it again nor posts it.
     */
    boolean publicReviewAccepted(AgentJob job) {
        return completions.existsByWorkspaceIdAndJobId(job.getWorkspace().getId(), job.getId());
    }

    /**
     * Records what an accepted review on the work puts on its way, in the accepting transaction and without any
     * provider call or lane wake: the approval item, the quiet or withheld rows, or the automatic package's dispatch
     * intent. The delivery policy is left to the dispatcher's egress check and final reservation, which decide the
     * write. Every write here joins the caller's transaction.
     *
     * @return the dispatch intent to send once the transaction commits, or null when nothing is to be written
     */
    @Nullable
    FeedbackDispatch prepareAcceptedOutcome(AgentJob job, ArtifactKind artifact, AdmittedDelivery decision) {
        return switch (decision) {
            case AdmittedDelivery.Proposed proposed -> {
                feedbackLedgerRecorder.recordProposalInCurrentTransaction(job, proposed.content());
                yield null;
            }
            case AdmittedDelivery.Automatic automatic ->
                prepareAutomatic(job, artifact, automatic.content(), automatic.contributingPracticeSlugs());
        };
    }

    private @Nullable FeedbackDispatch prepareAutomatic(
            AgentJob job,
            ArtifactKind artifact,
            @Nullable DeliveryContent delivery,
            Set<String> contributingPracticeSlugs) {
        boolean issue = ArtifactKinds.ISSUE.equals(artifact);
        if (delivery == null
                || (delivery.mrNote() == null && (issue || delivery.diffNotes().isEmpty()))) {
            feedbackLedgerRecorder.recordNothingToPostWithinTransaction(job, delivery);
            return null;
        }
        // No policy read here: it records its evaluation in a transaction of its own, which would wait on the job this
        // one holds. The dispatcher evaluates the same policy at egress and again at its final reservation, and a
        // refusal there settles the package and its ledger row as it would for any automatic package.
        @Nullable
        DeliveryContent providerPackage =
                issue ? issuePackage(commentFormatter, job, delivery) : providerPackage(job, delivery);
        if (providerPackage == null
                || (providerPackage.mrNote() == null
                        && providerPackage.diffNotes().stream()
                                .allMatch(note -> note.body().isBlank()))) {
            feedbackLedgerRecorder.recordSuppressedUnitInCurrentTransaction(
                    job, delivery, FeedbackSuppressionReason.EMPTY_AFTER_SANITIZE);
            return null;
        }
        return dispatchService.automaticIntent(job, providerPackage, contributingPracticeSlugs);
    }

    /**
     * Sends a committed accepted package through the dispatcher and records what it placed, as the recovery scan would.
     * Any failure leaves the intent to that scan; the acceptance stands either way.
     */
    void sendAcceptedPackage(AgentJob job, FeedbackDispatch intent) {
        try {
            dispatchService.recover(intent, job);
            recordAutomaticPackage(job, dispatchService.automaticPackage(job));
        } catch (RuntimeException e) {
            log.warn("Accepted review package left to dispatch recovery: jobId={}", job.getId(), e);
        }
    }

    /**
     * The issue comment as the provider will show it: the sanitized summary with its disclosure. Null when nothing is
     * left to post; an issue takes no line notes.
     */
    static @Nullable DeliveryContent issuePackage(
            PracticeFeedbackCommentFormatter formatter, AgentJob job, DeliveryContent delivery) {
        String sanitized = delivery.mrNote() == null ? "" : PullRequestCommentPoster.sanitize(delivery.mrNote());
        if (sanitized.isBlank()) return null;
        return new DeliveryContent(
                formatter.format(sanitized, job),
                delivery.diffNotes(),
                delivery.withheld(),
                delivery.summaryContributors());
    }

    void deliverFeedback(AgentJob job, @Nullable DeliveryContent delivery) {
        deliverFeedback(job, delivery, Set.of());
    }

    void recordProposal(AgentJob job, @Nullable DeliveryContent delivery) {
        feedbackLedgerRecorder.recordProposal(job, delivery);
    }

    /**
     * A transaction-open failure must not hold back public delivery; the preparation sweeper recovers the lanes.
     */
    void requestPrivatePreparation(AgentJob job) {
        try {
            feedbackLedgerRecorder.requestPreparation(job);
        } catch (RuntimeException e) {
            log.warn("Feedback-lane preparation request failed (delivery continues): jobId={}", job.getId(), e);
        }
    }

    boolean recoverAutomaticPackageIfPresent(AgentJob job) {
        FeedbackDispatch existing = dispatchService.findAutomaticPackage(job).orElse(null);
        if (existing == null) return false;
        PracticeFeedbackDispatchService.Result result = dispatchService.recover(existing, job);
        FeedbackDispatch recovered = dispatchService.automaticPackage(job);
        recordAutomaticPackage(job, recovered);
        if (result.status() == PracticeFeedbackDispatchService.Result.Status.SENT) {
            job.setDeliveryCommentId(result.externalRef());
            return true;
        }
        if (result.status() == PracticeFeedbackDispatchService.Result.Status.SUPPRESSED) return true;
        throw new JobDeliveryException(
                "The dispatch of the review package waits for reconciliation. jobId=" + job.getId());
    }

    void deliverFeedback(AgentJob job, @Nullable DeliveryContent delivery, Set<String> contributingPracticeSlugs) {
        if (delivery == null
                || (delivery.mrNote() == null && delivery.diffNotes().isEmpty())) {
            feedbackLedgerRecorder.recordNothingToPost(job, delivery);
            return;
        }

        PracticeFeedbackDeliveryPolicy.Decision<PullRequest> decision =
                deliveryPolicy.evaluatePullRequest(job, DeliveryPolicyStage.AUTOMATIC, null, contributingPracticeSlugs);
        if (!decision.allowed()) {
            FeedbackSuppressionReason reason = decision.refusal();
            if (reason != null) recordGateSuppressed(job, delivery, reason);
            return;
        }

        DeliveryContent providerPackage = providerPackage(job, delivery);
        // Blank notes keep their place so each note keeps its key and support; they carry nothing to post.
        if (providerPackage.mrNote() == null
                && providerPackage.diffNotes().stream()
                        .allMatch(note -> note.body().isBlank())) {
            recordGateSuppressed(job, delivery, FeedbackSuppressionReason.EMPTY_AFTER_SANITIZE);
            return;
        }
        PracticeFeedbackDispatchService.Result result =
                dispatchService.dispatchAutomaticPackage(job, providerPackage, contributingPracticeSlugs);
        FeedbackDispatch dispatch = dispatchService.automaticPackage(job);
        recordAutomaticPackage(job, dispatch);

        if (result.status() == PracticeFeedbackDispatchService.Result.Status.SENT) {
            job.setDeliveryCommentId(result.externalRef());
            return;
        }
        if (result.status() == PracticeFeedbackDispatchService.Result.Status.SUPPRESSED) return;
        throw new JobDeliveryException(
                "The dispatch of the review package waits for reconciliation. jobId=" + job.getId());
    }

    /**
     * Projects a settled package; one still settling records only the copies it has placed so far, so a correction
     * can reach them before the rest of the package is known.
     */
    void recordAutomaticPackage(AgentJob job, FeedbackDispatch dispatch) {
        if (isTerminal(dispatch.getState())) {
            projectAutomaticPackage(job, dispatch);
            return;
        }
        List<DeliveredSignal> signals = dispatchService.deliveredSignals(dispatch);
        boolean inlineDelivered = signals.stream().anyMatch(DeliveredSignal::acknowledged);
        if (dispatch.getDeliveredExternalRef() == null && !inlineDelivered) return;
        feedbackLedgerRecorder.recordWithoutConversation(
                job,
                dispatchService.packageContent(dispatch),
                artifactKind(job),
                signals,
                dispatch.getDeliveredExternalRef(),
                dispatch.getDeliveredExternalUrl());
    }

    void projectAutomaticPackage(AgentJob job, FeedbackDispatch dispatch) {
        dispatchService.projectRecovered(dispatch, () -> {
            DeliveryContent delivery = dispatchService.packageContent(dispatch);
            List<DeliveredSignal> signals = dispatchService.deliveredSignals(dispatch);
            var artifactKind = artifactKind(job);
            boolean summaryDelivered = dispatch.getDeliveredExternalRef() != null;
            boolean inlineDelivered = signals.stream().anyMatch(DeliveredSignal::acknowledged);

            if (dispatch.getState() == FeedbackDispatchState.SENT) {
                feedbackLedgerRecorder.record(
                        job,
                        delivery,
                        artifactKind,
                        signals,
                        dispatch.getDeliveredExternalRef(),
                        dispatch.getDeliveredExternalUrl());
                reconcileJob(job, dispatch, DeliveryStatus.DELIVERED);
                return;
            }
            if (dispatch.getState() == FeedbackDispatchState.SUPPRESSED) {
                FeedbackSuppressionReason reason =
                        FeedbackSuppressionReason.valueOf(Objects.requireNonNull(dispatch.getSuppressionReason()));
                if (!summaryDelivered && !inlineDelivered) {
                    feedbackLedgerRecorder.recordSuppressedUnit(job, delivery, reason);
                    reconcileJob(job, dispatch, DeliveryStatus.DELIVERED);
                    return;
                }
                feedbackLedgerRecorder.recordWithoutConversation(
                        job,
                        delivery,
                        artifactKind,
                        signals,
                        dispatch.getDeliveredExternalRef(),
                        dispatch.getDeliveredExternalUrl());
                feedbackLedgerRecorder.recordSuppressedRemainder(
                        job, delivery, reason, missingInlineKeys(delivery, signals));
                reconcileJob(job, dispatch, DeliveryStatus.DELIVERED);
                return;
            }
            if (dispatch.getState() == FeedbackDispatchState.FAILED) {
                if (summaryDelivered || inlineDelivered) {
                    feedbackLedgerRecorder.recordWithoutConversation(
                            job,
                            delivery,
                            artifactKind,
                            signals,
                            dispatch.getDeliveredExternalRef(),
                            dispatch.getDeliveredExternalUrl());
                    feedbackLedgerRecorder.recordUndeliveredRemainder(
                            job, delivery, missingInlineKeys(delivery, signals));
                } else {
                    feedbackLedgerRecorder.recordUndelivered(job, delivery);
                }
                reconcileJob(job, dispatch, DeliveryStatus.FAILED);
            }
        });
    }

    private void reconcileJob(AgentJob job, FeedbackDispatch dispatch, DeliveryStatus status) {
        DeliveryStatus effectiveStatus = FeedbackCompositionResultParser.compositionStatus(job.getOutput())
                        .failed()
                ? DeliveryStatus.FAILED
                : status;
        agentJobRepository.reconcileDispatchDeliveryStatus(
                dispatch.getAgentJobId(),
                dispatch.getWorkspaceId(),
                effectiveStatus,
                dispatch.getDeliveredExternalRef());
    }

    static boolean canReconcileProjectedReceipt(AgentJob job) {
        if (job.getStatus() != AgentJobStatus.COMPLETED
                || (job.getDeliveryStatus() != DeliveryStatus.PENDING
                        && job.getDeliveryStatus() != DeliveryStatus.FAILED)
                || FeedbackCompositionResultParser.compositionStatus(job.getOutput())
                        == FeedbackCompositionResultParser.CompositionStatus.LEGACY_UNKNOWN
                || ObservationAdmissionService.observationsWereRefused(job)) return false;
        ObservationAdmissionService.requireMatchingCompositionDigest(job);
        return true;
    }

    /** Repairs only job metadata from a current projected receipt; delivery and ledger projection are not replayed. */
    @Transactional
    public void reconcileProjectedAutomaticPackage(
            UUID jobId, Long workspaceId, UUID receiptId, int expectedRetryCount, short expectedDeliveryAttempts) {
        AgentJob current =
                agentJobRepository.findByIdWithWorkspaceForUpdate(jobId).orElse(null);
        if (current == null
                || !workspaceId.equals(current.getWorkspace().getId())
                || current.getRetryCount() != expectedRetryCount
                || current.getDeliveryAttempts() != expectedDeliveryAttempts
                || !canReconcileProjectedReceipt(current)) return;
        FeedbackDispatch receipt = dispatchService.findAutomaticPackage(current).orElse(null);
        if (receipt == null
                || !receiptId.equals(receipt.getId())
                || receipt.getProjectedAt() == null
                || !isTerminal(receipt.getState())) return;
        // The managed row remains locked through commit: status, output and both attempt counters stay current.
        DeliveryStatus status = receipt.getState() == FeedbackDispatchState.FAILED
                        || FeedbackCompositionResultParser.compositionStatus(current.getOutput())
                                .failed()
                ? DeliveryStatus.FAILED
                : DeliveryStatus.DELIVERED;
        current.setDeliveryStatus(status);
        current.setDeliveryCommentId(receipt.getDeliveredExternalRef());
    }

    /**
     * The package as the provider will show it, sealed before it is persisted: the summary with its disclosure, each
     * line note's provider-safe text with its own, and the marker that scopes this package's inline copies. A retry
     * posts and reads back exactly this text instead of rebuilding it.
     */
    private DeliveryContent providerPackage(AgentJob job, DeliveryContent delivery) {
        List<ReviewResultParser.DiffNote> notes = delivery.diffNotes().stream()
                .map(note -> {
                    String sanitized = PullRequestCommentPoster.sanitize(note.body());
                    return new ReviewResultParser.DiffNote(
                            note.filePath(),
                            note.startLine(),
                            note.endLine(),
                            sanitized.isBlank() ? "" : commentFormatter.appendInlineFeedbackPrompt(sanitized, job),
                            note.deliveryKey(),
                            note.contributors());
                })
                .toList();
        String marker = InlinePackageScope.automaticMarker(job.getId());
        String summary = delivery.mrNote();
        String sanitized = summary == null ? "" : PullRequestCommentPoster.sanitize(summary);
        if (sanitized.isBlank())
            return new DeliveryContent(
                    null,
                    notes,
                    delivery.withheld(),
                    delivery.summaryContributors() == null ? null : List.of(),
                    marker);
        return new DeliveryContent(
                commentFormatter.format(sanitized, job),
                notes,
                delivery.withheld(),
                delivery.summaryContributors(),
                marker);
    }

    private static List<String> missingInlineKeys(DeliveryContent delivery, List<DeliveredSignal> signals) {
        Set<String> delivered = signals.stream()
                .filter(DeliveredSignal::acknowledged)
                .map(DeliveredSignal::deliveryKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return delivery.diffNotes().stream()
                .map(ReviewResultParser.DiffNote::deliveryKey)
                .filter(Objects::nonNull)
                .filter(key -> !delivered.contains(key))
                .toList();
    }

    private static boolean isTerminal(FeedbackDispatchState state) {
        return (state == FeedbackDispatchState.SENT
                || state == FeedbackDispatchState.SUPPRESSED
                || state == FeedbackDispatchState.FAILED);
    }

    private static ArtifactKind artifactKind(AgentJob job) {
        var artifact = AgentJobService.artifactKindFor(Objects.requireNonNull(job.getJobType()));
        if (artifact.equals(ArtifactKinds.PULL_REQUEST) || artifact.equals(ArtifactKinds.ISSUE)) return artifact;
        throw new JobDeliveryException("Artifact package projection does not support " + artifact.value());
    }

    private void recordGateSuppressed(AgentJob job, DeliveryContent delivery, FeedbackSuppressionReason reason) {
        try {
            feedbackLedgerRecorder.recordSuppressedUnit(job, delivery, reason);
        } catch (RuntimeException exception) {
            log.warn(
                    "Gate-suppressed ledger record failed: jobId={}, reason={}, error={}",
                    job.getId(),
                    reason,
                    exception.getMessage());
        }
    }
}
