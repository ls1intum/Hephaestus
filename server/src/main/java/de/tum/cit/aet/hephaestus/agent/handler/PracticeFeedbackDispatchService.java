package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.ExistingDeliveryLookup;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliverySuppressedException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyStage;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatch;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchCompletion;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchDestination;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchInsert;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
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

    PracticeFeedbackDispatchService(
        FeedbackDispatchRepository repository,
        PracticeFeedbackDeliveryPolicy policy,
        PullRequestCommentPoster commentPoster,
        TransactionTemplate transactionTemplate,
        MeterRegistry meterRegistry,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.policy = policy;
        this.commentPoster = commentPoster;
        this.transactionTemplate = transactionTemplate;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    Result dispatchAutomaticSummary(
        AgentJob job,
        String body,
        @Nullable String priorExternalRef,
        Set<String> contributingPracticeSlugs
    ) {
        return dispatch(
            ensure(
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
            ensure(
                job,
                feedback.getId(),
                "approved:" + feedback.getId(),
                FeedbackDispatchDestination.APPROVED_ARTIFACT_COMMENT,
                feedback.getBody(),
                null,
                Set.of()
            ),
            job
        );
    }

    private FeedbackDispatch ensure(
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
        // The row that won the insert is the body of record, and the caller's is discarded. A retry
        // recomposes the summary from observations this run has since persisted, so the two differ by
        // the progress footer alone — refusing the mismatch would strand the delivery permanently.
        return dispatch;
    }

    private Result dispatch(FeedbackDispatch dispatch, AgentJob job) {
        if (dispatch.getState() == FeedbackDispatchState.SENT) {
            return Result.sent(dispatch.getDeliveredExternalRef());
        }
        if (dispatch.getState() == FeedbackDispatchState.SUPPRESSED) {
            return Result.suppressed(null);
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
            return finish(
                    dispatch,
                    owner,
                    FeedbackDispatchState.SUPPRESSED,
                    null,
                    nameOf(decision.suppressionReason()),
                    null
                )
                ? Result.suppressed(decision.suppressionReason())
                : Result.inProgress();
        }

        ExistingDeliveryLookup existing =
            dispatch.getDestination() == FeedbackDispatchDestination.ARTIFACT_SUMMARY
                ? commentPoster.findExistingSummaryComment(job)
                : commentPoster.findApprovedProposal(job, dispatch.getFeedbackId());
        if (existing.kind() == ExistingDeliveryLookup.Kind.UNKNOWN) {
            return retry(dispatch, owner, "Provider lookup was inconclusive");
        }
        if (existing.kind() == ExistingDeliveryLookup.Kind.FOUND) {
            return finish(dispatch, owner, FeedbackDispatchState.SENT, existing.commentId(), null, null)
                ? Result.sent(existing.commentId())
                : Result.inProgress();
        }

        decision = evaluateAtEgress(dispatch, job);
        if (!decision.allowed()) {
            return finish(
                    dispatch,
                    owner,
                    FeedbackDispatchState.SUPPRESSED,
                    null,
                    nameOf(decision.suppressionReason()),
                    null
                )
                ? Result.suppressed(decision.suppressionReason())
                : Result.inProgress();
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
            return finish(dispatch, owner, FeedbackDispatchState.SENT, externalRef, null, null)
                ? Result.sent(externalRef)
                : Result.inProgress();
        } catch (JobDeliverySuppressedException exception) {
            return finish(dispatch, owner, FeedbackDispatchState.SUPPRESSED, null, exception.getMessage(), null)
                ? Result.suppressed(FeedbackSuppressionReason.INSTANCE_SILENCED)
                : Result.inProgress();
        } catch (RuntimeException exception) {
            return retry(dispatch, owner, bounded(exception.getMessage()));
        }
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
        if (dispatch.getDestination() == FeedbackDispatchDestination.APPROVED_ARTIFACT_COMMENT) {
            if (job.getMetadata() != null && job.getMetadata().has("issue_number")) {
                return commentPoster.postIssueApprovedProposal(job, dispatch.getFeedbackId(), dispatch.getBody());
            }
            return commentPoster.postApprovedProposal(job, dispatch.getFeedbackId(), dispatch.getBody());
        }
        // Editing the summary this review supersedes comes first, for issues as much as for pull requests:
        // an artifact reviewed five times is one evolving comment, not five.
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

    private Result retry(FeedbackDispatch dispatch, String owner, @Nullable String error) {
        int attempt = dispatch.getAttemptCount() + 1;
        if (attempt >= MAX_ATTEMPTS) {
            return finish(dispatch, owner, FeedbackDispatchState.FAILED, null, error, null)
                ? Result.failed()
                : Result.inProgress();
        }
        return finish(
                dispatch,
                owner,
                FeedbackDispatchState.UNCERTAIN,
                null,
                error,
                Instant.now().plus(backoff(attempt))
            )
            ? Result.uncertain()
            : Result.inProgress();
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

    private static @Nullable String nameOf(@Nullable FeedbackSuppressionReason reason) {
        return reason == null ? null : reason.name();
    }

    record Result(Status status, @Nullable String externalRef, @Nullable FeedbackSuppressionReason suppressionReason) {
        static Result sent(@Nullable String ref) {
            return new Result(Status.SENT, ref, null);
        }

        static Result suppressed(@Nullable FeedbackSuppressionReason reason) {
            return new Result(Status.SUPPRESSED, null, reason);
        }

        static Result uncertain() {
            return new Result(Status.UNCERTAIN, null, null);
        }

        static Result inProgress() {
            return new Result(Status.IN_PROGRESS, null, null);
        }

        static Result failed() {
            return new Result(Status.FAILED, null, null);
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
