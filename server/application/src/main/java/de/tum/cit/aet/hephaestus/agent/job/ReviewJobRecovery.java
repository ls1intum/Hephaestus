package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.handler.ResultAdmissionService;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentResult;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Recovers lost attempts without repeating admitted results or losing their measured spend. */
@Component
@ConditionalOnCaptureRole
@WorkspaceAgnostic("Recovery uses the supplied job id, owning worker, and attempt fence")
public class ReviewJobRecovery {
    enum Outcome {
        NONE,
        REQUEUED,
        FAILED,
        CANCELLED
    }

    private static final Logger log = LoggerFactory.getLogger(ReviewJobRecovery.class);
    private final ReviewJobRepository jobs;
    private final AgentProperties properties;
    private final ObjectMapper mapper;
    private final LlmUsageRecorder usage;
    private final Optional<ReviewCompositionContinuation> continuation;
    private final Optional<PracticeScriptRunRecorder> scriptRuns;

    public ReviewJobRecovery(
            ReviewJobRepository jobs,
            AgentProperties properties,
            ObjectMapper mapper,
            LlmUsageRecorder usage,
            Optional<ReviewCompositionContinuation> continuation,
            Optional<PracticeScriptRunRecorder> scriptRuns) {
        this.jobs = jobs;
        this.properties = properties;
        this.mapper = mapper;
        this.usage = usage;
        this.continuation = continuation;
        this.scriptRuns = scriptRuns;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome recover(
            UUID jobId, String workerId, int attempt, @Nullable ReviewJobCancellationReason cancellation) {
        continuation.ifPresent(ReviewCompositionContinuation::holdRecoveryCustody);
        ReviewJob job = jobs.lockById(jobId).orElse(null);
        if (!owns(job, workerId, attempt)) return Outcome.NONE;
        ReviewJob owned = Objects.requireNonNull(job);
        ReviewJobLlmUsage counts = counts(owned);
        Instant now = Instant.now();
        Instant availableAt = now.plus(ReviewJobBackoff.compute(attempt + 1));
        int changed = attempt < properties.maxRetries()
                ? jobs.requeueOrphan(jobId, workerId, properties.maxRetries(), availableAt)
                : 0;
        boolean admitted = ResultAdmissionService.isAdmitted(owned);
        if (changed == 0 && admitted && continuation.isPresent()) {
            changed = continuation
                    .get()
                    .requeue(
                            owned,
                            workerId,
                            new ReviewJobRepository.CompositionRequeue(
                                    attempt, properties.maxRetries(), now, availableAt));
        }
        if (changed > 0) {
            bill(owned, TerminalUsage.fromProxy(counts));
            return Outcome.REQUEUED;
        }
        if (!admitted && attempt < properties.maxRetries()) return Outcome.NONE;
        if (admitted) {
            changed = jobs.failAdmittedOwnedBy(
                    jobId, workerId, attempt, now, ResultAdmissionService.INTERRUPTED_AFTER_ADMISSION);
        } else if (cancellation != null) {
            changed = jobs.transitionToCancelledOwnedBy(
                    jobId,
                    now,
                    "The worker stopped this attempt.",
                    cancellation,
                    Set.of(ReviewJobStatus.RUNNING),
                    workerId);
        } else {
            changed = jobs.transitionStatusOwnedBy(
                    jobId,
                    ReviewJobStatus.FAILED,
                    now,
                    "The worker that owned this job was lost. The retry limit is reached.",
                    Set.of(ReviewJobStatus.RUNNING),
                    workerId);
        }
        if (changed == 0) return Outcome.NONE;
        bill(owned, TerminalUsage.fromProxy(counts));
        return !admitted && cancellation != null ? Outcome.CANCELLED : Outcome.FAILED;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean retryUnadmitted(ReviewJob job, String workerId, boolean bill) {
        ReviewJob locked = jobs.lockById(job.getId()).orElse(null);
        if (locked == null || !owns(locked, workerId, job.getAttempt()) || ResultAdmissionService.isAdmitted(locked))
            return false;
        ReviewJobLlmUsage counts = bill ? counts(job) : null;
        int changed = jobs.requeueOrphan(
                job.getId(),
                workerId,
                properties.maxRetries(),
                Instant.now().plus(ReviewJobBackoff.compute(job.getAttempt() + 1)));
        if (changed > 0 && bill) bill(job, TerminalUsage.fromProxy(counts));
        return changed > 0;
    }

    /**
     * Continues an admitted attempt whose private feedback composition the run stopped. The locked row decides: an
     * accepted public review, so no attempt composes public feedback again, and no prepared private piece, so none is
     * composed twice. The attempt returned, so it is billed once from its own report, selected as at a terminal write,
     * and keeps the practice script runs it reported.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean continueComposition(
            ReviewJob job,
            String workerId,
            AgentResult.@Nullable LlmUsage runnerUsage,
            byte @Nullable [] scriptReport) {
        if (continuation.isEmpty()) return false;
        continuation.get().holdRecoveryCustody();
        ReviewJob locked = jobs.lockById(job.getId()).orElse(null);
        if (!owns(locked, workerId, job.getAttempt())) return false;
        ReviewJob owned = Objects.requireNonNull(locked);
        if (!ResultAdmissionService.isAdmitted(owned)
                || owned.getPublicReviewDigest() == null
                || ResultAdmissionService.privatePrepared(owned)) return false;
        TerminalUsage spent = TerminalUsage.resolve(
                runnerUsage,
                counts(owned),
                jobs.sumPracticeScriptUsageByKind(owned.getWorkspaceId(), owned.getId(), owned.getAttempt()));
        Instant now = Instant.now();
        int changed = continuation
                .get()
                .requeue(
                        owned,
                        workerId,
                        new ReviewJobRepository.CompositionRequeue(
                                owned.getAttempt(),
                                properties.maxRetries(),
                                now,
                                now.plus(ReviewJobBackoff.compute(owned.getAttempt() + 1))));
        if (changed > 0) {
            bill(owned, spent);
            // The row locked as attempt N keys and admits the runs; the requeue detached it unchanged. The attempt that
            // continues it measures nothing, so no later report replaces them.
            scriptRuns.ifPresent(runs -> {
                ConfigSnapshot snapshot = frozen(owned);
                runs.record(owned, scriptReport, snapshot == null ? Map.of() : snapshot.practiceScriptModels(), now);
            });
        }
        return changed > 0;
    }

    private static boolean owns(@Nullable ReviewJob job, String workerId, int attempt) {
        return job != null
                && job.getStatus() == ReviewJobStatus.RUNNING
                && workerId.equals(job.getWorkerId())
                && job.getAttempt() == attempt;
    }

    private @Nullable ReviewJobLlmUsage counts(ReviewJob job) {
        return job.getExecutionStartedAt() != null
                ? jobs.findLlmUsageById(job.getId()).orElse(null)
                : null;
    }

    private void bill(ReviewJob job, TerminalUsage spent) {
        if (job.getExecutionStartedAt() == null) return;
        ConfigSnapshot snapshot = frozen(job);
        LlmPriceSnapshot price = snapshot != null && snapshot.model().priceSnapshot() != null
                ? snapshot.model().priceSnapshot()
                : LlmPriceSnapshot.unpricedInstance();
        spent.appendTo(usage, jobs, job.getWorkspaceId(), job, snapshot, price);
    }

    private @Nullable ConfigSnapshot frozen(ReviewJob job) {
        try {
            return ConfigSnapshot.fromJson(job.getConfigSnapshot(), mapper);
        } catch (RuntimeException failure) {
            log.warn("Cannot read frozen model for recovered job {}", job.getId(), failure);
            return null;
        }
    }
}
