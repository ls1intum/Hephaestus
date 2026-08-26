package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.ExistingDeliveryLookup;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliverySuppressedException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyStage;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatch;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchCompletion;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchDestination;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchInsert;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementType;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
class PracticeFeedbackDispatchService {

    static final Duration LEASE = Duration.ofMinutes(5);
    static final int MAX_ATTEMPTS = 8;

    private static final Duration BASE_BACKOFF = Duration.ofSeconds(15);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(15);

    private final FeedbackDispatchRepository repository;
    private final PracticeFeedbackDeliveryPolicy policy;
    private final PullRequestCommentPoster commentPoster;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final FeedbackRepository feedbackRepository;
    private final DiffNotePoster diffNotePoster;
    private final FeedbackLedgerRecorder ledgerRecorder;
    private final PracticeFeedbackCommentFormatter commentFormatter;

    PracticeFeedbackDispatchService(
        FeedbackDispatchRepository repository,
        PracticeFeedbackDeliveryPolicy policy,
        PullRequestCommentPoster commentPoster,
        TransactionTemplate transactionTemplate,
        MeterRegistry meterRegistry,
        ObjectMapper objectMapper,
        FeedbackRepository feedbackRepository,
        DiffNotePoster diffNotePoster,
        FeedbackLedgerRecorder ledgerRecorder,
        PracticeFeedbackCommentFormatter commentFormatter
    ) {
        this.repository = repository;
        this.policy = policy;
        this.commentPoster = commentPoster;
        this.transactionTemplate = transactionTemplate;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.feedbackRepository = feedbackRepository;
        this.diffNotePoster = diffNotePoster;
        this.ledgerRecorder = ledgerRecorder;
        this.commentFormatter = commentFormatter;
    }

    Result dispatchAutomaticSummary(
        AgentJob job,
        String body,
        @Nullable String priorExternalRef,
        Set<String> contributingPracticeSlugs
    ) {
        return dispatch(
            insertIfAbsentAndLoad(
                job,
                null,
                "summary:" + job.getId(),
                FeedbackDispatchDestination.ARTIFACT_SUMMARY,
                body,
                priorExternalRef,
                contributingPracticeSlugs
            ),
            job
        );
    }

    Result dispatchApproved(AgentJob job, Feedback feedback) {
        return dispatch(
            insertIfAbsentAndLoad(
                job,
                feedback.getId(),
                "approved:" + feedback.getId(),
                FeedbackDispatchDestination.APPROVED_REVIEW_PACKAGE,
                java.util.Objects.requireNonNull(feedback.getBody(), "an approved proposal always has a body"),
                null,
                Set.copyOf(feedback.getProposedPracticeSlugs())
            ),
            job
        );
    }

    private FeedbackDispatch insertIfAbsentAndLoad(
        AgentJob job,
        @Nullable UUID feedbackId,
        String key,
        FeedbackDispatchDestination destination,
        String body,
        @Nullable String targetExternalRef,
        Set<String> contributingPracticeSlugs
    ) {
        Long workspaceId = job.getWorkspace().getId();
        transactionTemplate.executeWithoutResult(status ->
            repository.insertIfAbsent(
                new FeedbackDispatchInsert(
                    UUID.randomUUID(),
                    key,
                    workspaceId,
                    job.getId(),
                    feedbackId,
                    destination.name(),
                    body,
                    targetExternalRef,
                    objectMapper.valueToTree(contributingPracticeSlugs.stream().sorted().toList()).toString()
                )
            )
        );
        FeedbackDispatch dispatch = repository
            .findByDestinationKeyAndWorkspaceId(key, workspaceId)
            .orElseThrow(() -> new JobDeliveryException("Dispatch intent was not persisted: " + key));
        if (dispatch.getDestination() != destination) {
            throw new JobDeliveryException("Dispatch key was reused for a different destination: " + key);
        }
        return dispatch;
    }

    private Result dispatch(FeedbackDispatch dispatch, AgentJob job) {
        if (dispatch.getState() == FeedbackDispatchState.SENT) {
            return Result.sent(dispatch.getDeliveredExternalRef());
        }
        if (dispatch.getState() == FeedbackDispatchState.SUPPRESSED) {
            return Result.suppressed(storedReason(dispatch));
        }
        if (dispatch.getState() == FeedbackDispatchState.FAILED) {
            return Result.failed();
        }

        String owner = UUID.randomUUID().toString();
        Integer claimed = transactionTemplate.execute(status ->
            repository.claim(
                dispatch.getId(),
                dispatch.getWorkspaceId(),
                owner,
                Instant.now().plus(LEASE),
                MAX_ATTEMPTS
            )
        );
        if (claimed == null || claimed == 0) {
            return Result.inProgress();
        }

        PracticeFeedbackDeliveryPolicy.Decision<?> decision = evaluateAtEgress(dispatch, job);
        if (!decision.allowed()) {
            return refuse(dispatch, owner, decision.refusal());
        }

        if (dispatch.getDestination() == FeedbackDispatchDestination.APPROVED_REVIEW_PACKAGE) {
            return dispatchApprovedPackage(dispatch, job, owner, decision);
        }

        ExistingDeliveryLookup existing = commentPoster.findExistingSummaryComment(job);
        if (existing.kind() == ExistingDeliveryLookup.Kind.UNKNOWN) {
            return retry(dispatch, owner, "Provider lookup was inconclusive");
        }
        if (existing.kind() == ExistingDeliveryLookup.Kind.FOUND) {
            return finish(dispatch, owner, FeedbackDispatchState.SENT, existing.commentId(), null, null, null)
                ? Result.sent(existing.commentId())
                : Result.inProgress();
        }

        decision = evaluateAtEgress(dispatch, job);
        if (!decision.allowed()) {
            return refuse(dispatch, owner, decision.refusal());
        }

        if (dispatch.getWriteStarted()) {
            return retry(dispatch, owner, "A prior provider write has not been reconciled");
        }
        Integer began = transactionTemplate.execute(status ->
            repository.beginWrite(dispatch.getId(), dispatch.getWorkspaceId(), owner)
        );
        if (began == null || began != 1) {
            return Result.inProgress();
        }

        try {
            String externalRef = post(dispatch, job);
            if (externalRef == null || externalRef.isBlank()) {
                return retry(dispatch, owner, "Provider returned no external id");
            }
            return finish(dispatch, owner, FeedbackDispatchState.SENT, externalRef, null, null, null)
                ? Result.sent(externalRef)
                : Result.inProgress();
        } catch (JobDeliverySuppressedException exception) {
            return refuse(dispatch, owner, FeedbackSuppressionReason.INSTANCE_SILENCED);
        } catch (RuntimeException exception) {
            return retry(dispatch, owner, bounded(exception.getMessage()));
        }
    }

    private Result dispatchApprovedPackage(
        FeedbackDispatch dispatch,
        AgentJob job,
        String owner,
        PracticeFeedbackDeliveryPolicy.Decision<?> initialDecision
    ) {
        Feedback feedback = feedbackRepository
            .findByIdAndWorkspaceId(dispatch.approvedFeedbackId(), dispatch.getWorkspaceId())
            .orElse(null);
        if (feedback == null || feedback.getBody() == null || !feedback.getBody().equals(dispatch.getBody())) {
            return retry(dispatch, owner, "Approved feedback is missing or no longer matches its immutable body");
        }
        if (!reviewedRevisionMatches(feedback, initialDecision)) {
            return refuse(dispatch, owner, FeedbackSuppressionReason.APPROVAL_STALE);
        }

        try {
            ExistingDeliveryLookup existing = commentPoster.findApprovedProposal(job, dispatch.approvedFeedbackId());
            if (existing.kind() == ExistingDeliveryLookup.Kind.UNKNOWN) {
                return retry(dispatch, owner, "Provider lookup was inconclusive");
            }

            String summaryRef;
            if (existing.kind() == ExistingDeliveryLookup.Kind.FOUND) {
                summaryRef = java.util.Objects.requireNonNull(existing.commentId());
            } else {
                PracticeFeedbackDeliveryPolicy.Decision<?> decision = evaluateAtEgress(dispatch, job);
                if (!decision.allowed()) return refuse(dispatch, owner, decision.refusal());
                if (!reviewedRevisionMatches(feedback, decision)) {
                    return refuse(dispatch, owner, FeedbackSuppressionReason.APPROVAL_STALE);
                }
                if (dispatch.getWriteStarted()) {
                    return retry(dispatch, owner, "A prior provider write has not been reconciled");
                }
                Integer began = transactionTemplate.execute(status ->
                    repository.beginWrite(dispatch.getId(), dispatch.getWorkspaceId(), owner)
                );
                if (began == null || began != 1) return Result.inProgress();
                summaryRef = java.util.Objects.requireNonNull(
                    commentPoster.postApprovedProposal(
                        job,
                        dispatch.approvedFeedbackId(),
                        commentFormatter.appendDisclosure(dispatch.getBody(), job)
                    )
                );
            }

            ledgerRecorder.recordApprovedPlacements(feedback, summaryRef, java.util.List.of());
            var inlineNotes = feedback
                .getProposedPlacements()
                .stream()
                .filter(placement -> placement.type() == PlacementType.INLINE)
                .map(placement ->
                    new PracticeDetectionResultParser.DiffNote(
                        java.util.Objects.requireNonNull(placement.path()),
                        java.util.Objects.requireNonNull(placement.startLine()),
                        placement.endLine(),
                        placement.body(),
                        placement.recurrenceKey()
                    )
                )
                .toList();
            if (!inlineNotes.isEmpty()) {
                PracticeFeedbackDeliveryPolicy.Decision<?> decision = evaluateAtEgress(dispatch, job);
                if (!decision.allowed()) return refuse(dispatch, owner, decision.refusal(), summaryRef);
                if (!reviewedRevisionMatches(feedback, decision)) {
                    return refuse(dispatch, owner, FeedbackSuppressionReason.APPROVAL_STALE, summaryRef);
                }
                DiffNotePoster.DiffNoteResult inline = diffNotePoster.reconcileApprovedInlineNotes(
                    job,
                    feedback.getId(),
                    inlineNotes
                );
                ledgerRecorder.recordApprovedPlacements(feedback, summaryRef, inline.signals());
                if (inline.failed() > 0 || inline.suppressed()) {
                    return retryPackage(dispatch, owner, "Approved review package remains incomplete", summaryRef);
                }
            }
            return finish(dispatch, owner, FeedbackDispatchState.SENT, summaryRef, null, null, null)
                ? Result.sent(summaryRef)
                : Result.inProgress();
        } catch (JobDeliverySuppressedException exception) {
            return refuse(dispatch, owner, FeedbackSuppressionReason.INSTANCE_SILENCED);
        } catch (RuntimeException exception) {
            return retry(dispatch, owner, bounded(exception.getMessage()));
        }
    }

    private static boolean reviewedRevisionMatches(
        Feedback feedback,
        PracticeFeedbackDeliveryPolicy.Decision<?> decision
    ) {
        if (feedback.getReviewedRevision() == null) return true;
        return (
            decision.target() instanceof PullRequest pullRequest &&
            feedback.getReviewedRevision().equals(pullRequest.getHeadRefOid())
        );
    }

    private PracticeFeedbackDeliveryPolicy.Decision<?> evaluateAtEgress(FeedbackDispatch dispatch, AgentJob job) {
        Set<String> practiceSlugs = dispatch
            .getPracticeSlugs()
            .valueStream()
            .filter(tools.jackson.databind.JsonNode::isString)
            .map(tools.jackson.databind.JsonNode::asString)
            .collect(Collectors.toUnmodifiableSet());
        if (job.getMetadata() != null && job.getMetadata().has("issue_number")) {
            return policy.evaluateIssue(job, DeliveryPolicyStage.EGRESS, dispatch.getFeedbackId(), practiceSlugs);
        }
        return policy.evaluatePullRequest(job, DeliveryPolicyStage.EGRESS, dispatch.getFeedbackId(), practiceSlugs);
    }

    private @Nullable String post(FeedbackDispatch dispatch, AgentJob job) {
        if (dispatch.getDestination() == FeedbackDispatchDestination.APPROVED_REVIEW_PACKAGE) {
            if (job.getMetadata() != null && job.getMetadata().has("issue_number")) {
                return commentPoster.postIssueApprovedProposal(job, dispatch.approvedFeedbackId(), dispatch.getBody());
            }
            return commentPoster.postApprovedProposal(job, dispatch.approvedFeedbackId(), dispatch.getBody());
        }
        boolean issue = job.getMetadata() != null && job.getMetadata().has("issue_number");
        String target = dispatch.getTargetExternalRef();
        if (target != null) {
            PullRequestCommentPoster.UpdateResult update = issue
                ? commentPoster.updateIssueFormattedBody(job, target, dispatch.getBody())
                : commentPoster.updateFormattedBody(job, target, dispatch.getBody());
            if (update.kind() == PullRequestCommentPoster.UpdateResult.Kind.TRANSIENT) {
                return null;
            }
            if (update.kind() == PullRequestCommentPoster.UpdateResult.Kind.EDITED) {
                return update.externalId();
            }
        }
        return issue
            ? commentPoster.postIssueFormattedBody(job, dispatch.getBody())
            : commentPoster.postFormattedBody(job, dispatch.getBody());
    }

    private boolean finish(
        FeedbackDispatch dispatch,
        String owner,
        FeedbackDispatchState state,
        @Nullable String externalRef,
        @Nullable String error,
        @Nullable FeedbackSuppressionReason suppressionReason,
        @Nullable Instant nextAttemptAt
    ) {
        Integer affected = transactionTemplate.execute(status ->
            repository.finish(
                new FeedbackDispatchCompletion(
                    dispatch.getId(),
                    dispatch.getWorkspaceId(),
                    owner,
                    state.name(),
                    externalRef,
                    bounded(error),
                    suppressionReason == null ? null : suppressionReason.name(),
                    nextAttemptAt == null ? Instant.now() : nextAttemptAt
                )
            )
        );
        if (affected == null || affected != 1) return false;
        meterRegistry
            .counter(
                "practice.feedback.dispatch",
                "destination",
                dispatch.getDestination().name(),
                "state",
                state.name()
            )
            .increment();
        return true;
    }

    private Result refuse(FeedbackDispatch dispatch, String owner, FeedbackSuppressionReason reason) {
        return refuse(dispatch, owner, reason, null);
    }

    private Result refuse(
        FeedbackDispatch dispatch,
        String owner,
        FeedbackSuppressionReason reason,
        @Nullable String externalRef
    ) {
        return finish(dispatch, owner, FeedbackDispatchState.SUPPRESSED, externalRef, null, reason, null)
            ? Result.suppressed(reason, externalRef)
            : Result.inProgress();
    }

    private static @Nullable FeedbackSuppressionReason storedReason(FeedbackDispatch dispatch) {
        String stored = dispatch.getSuppressionReason();
        return stored == null ? null : FeedbackSuppressionReason.valueOf(stored);
    }

    private Result retry(FeedbackDispatch dispatch, String owner, @Nullable String error) {
        return retry(dispatch, owner, error, null);
    }

    private Result retry(
        FeedbackDispatch dispatch,
        String owner,
        @Nullable String error,
        @Nullable String externalRef
    ) {
        int attempt = dispatch.getAttemptCount() + 1;
        if (attempt >= MAX_ATTEMPTS && !dispatch.getWriteStarted()) {
            return finish(dispatch, owner, FeedbackDispatchState.FAILED, null, error, null, null)
                ? Result.failed()
                : Result.inProgress();
        }
        return finish(
                dispatch,
                owner,
                FeedbackDispatchState.UNCERTAIN,
                externalRef,
                error,
                null,
                Instant.now().plus(backoff(attempt))
            )
            ? Result.uncertain(externalRef)
            : Result.inProgress();
    }

    private Result retryPackage(FeedbackDispatch dispatch, String owner, String error, String externalRef) {
        int attempt = dispatch.getAttemptCount() + 1;
        if (attempt >= MAX_ATTEMPTS) {
            return finish(dispatch, owner, FeedbackDispatchState.FAILED, externalRef, error, null, null)
                ? Result.failed(externalRef)
                : Result.inProgress();
        }
        return retry(dispatch, owner, error, externalRef);
    }

    static Duration backoff(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 10);
        Duration candidate = BASE_BACKOFF.multipliedBy(multiplier);
        double jitter = ThreadLocalRandom.current().nextDouble(0.75, 1.25);
        Duration jittered = Duration.ofMillis((long) (candidate.toMillis() * jitter));
        return jittered.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : jittered;
    }

    Result recover(FeedbackDispatch dispatch, AgentJob job) {
        return dispatch(dispatch, job);
    }

    void fail(FeedbackDispatch dispatch, String error) {
        transactionTemplate.executeWithoutResult(status ->
            repository.fail(dispatch.getId(), dispatch.getWorkspaceId(), bounded(error))
        );
    }

    private static @Nullable String bounded(@Nullable String value) {
        if (value == null || value.length() <= 512) return value;
        return value.substring(0, 512);
    }

    record Result(Status status, @Nullable String externalRef, @Nullable FeedbackSuppressionReason suppressionReason) {
        FeedbackSuppressionReason refusal() {
            return java.util.Objects.requireNonNull(suppressionReason, "a refused dispatch always names its reason");
        }

        String sentRef() {
            return java.util.Objects.requireNonNull(externalRef, "a sent dispatch always has a provider id");
        }

        static Result sent(@Nullable String ref) {
            return new Result(Status.SENT, ref, null);
        }

        static Result suppressed(@Nullable FeedbackSuppressionReason reason) {
            return suppressed(reason, null);
        }

        static Result suppressed(@Nullable FeedbackSuppressionReason reason, @Nullable String ref) {
            return new Result(Status.SUPPRESSED, ref, reason);
        }

        static Result uncertain(@Nullable String ref) {
            return new Result(Status.UNCERTAIN, ref, null);
        }

        static Result uncertain() {
            return uncertain(null);
        }

        static Result inProgress() {
            return new Result(Status.IN_PROGRESS, null, null);
        }

        static Result failed() {
            return new Result(Status.FAILED, null, null);
        }

        static Result failed(@Nullable String ref) {
            return new Result(Status.FAILED, ref, null);
        }

        enum Status {
            SENT,
            SUPPRESSED,
            UNCERTAIN,
            IN_PROGRESS,
            FAILED,
        }
    }
}
