package de.tum.cit.aet.hephaestus.agent.sandbox.kubernetes;

import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.agent.metrics.AgentMetrics;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxException;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes the review Jobs and Heph host Pods that nothing uses any more, at startup and then periodically, as
 * {@code SandboxReconciler} does for Docker. Turn sandboxes are their worker's ({@code TurnSandboxes}).
 *
 * <ul>
 *   <li>A review Job goes, with its Pod, when an earlier worker process created it, or when its job is neither queued
 *       nor running and the Job is past its grace.
 *   <li>A host Pod goes when an earlier worker process created it.
 * </ul>
 *
 * <p>What an earlier process created is no running worker's ({@link KubernetesWorker}): its gateway session ended with
 * that process. An unreadable job list, Job list or Pod list skips the sweep.
 */
@WorkspaceAgnostic("Sandbox reconciler operates on Kubernetes workloads, not workspace-scoped data")
public class KubernetesSandboxReconciler {

    private static final Logger log = LoggerFactory.getLogger(KubernetesSandboxReconciler.class);

    /** A Job younger than this is never reaped for its job, so a starting attempt is not mistaken for an ended one. */
    private static final Duration REAP_GRACE = Duration.ofMinutes(2);

    private final ReviewJobRepository jobRepository;
    private final KubernetesPods pods;
    private final KubernetesReviewJobs reviewJobs;
    private final KubernetesWorker worker;
    private final Clock clock;
    private final Counter orphanedPods;
    private final Counter orphanedJobs;
    private final Counter completedSweeps;
    private final Counter skippedSweeps;

    public KubernetesSandboxReconciler(
            ReviewJobRepository jobRepository,
            KubernetesPods pods,
            KubernetesReviewJobs reviewJobs,
            KubernetesWorker worker,
            MeterRegistry meterRegistry,
            Clock clock) {
        this.jobRepository = jobRepository;
        this.pods = pods;
        this.reviewJobs = reviewJobs;
        this.worker = worker;
        this.clock = clock;
        this.orphanedPods = Counter.builder(AgentMetrics.SANDBOX_RECONCILER_ORPHANED)
                .tag("resource", "pod")
                .description("Orphaned sandbox Pods removed")
                .register(meterRegistry);
        this.orphanedJobs = Counter.builder(AgentMetrics.SANDBOX_RECONCILER_ORPHANED)
                .tag("resource", "job")
                .description("Orphaned review Jobs removed, each with its Pod")
                .register(meterRegistry);
        this.completedSweeps = Counter.builder(AgentMetrics.SANDBOX_RECONCILER_SWEEPS)
                .tag("outcome", "completed")
                .description("Reconciliation sweeps that ran to completion")
                .register(meterRegistry);
        this.skippedSweeps = Counter.builder(AgentMetrics.SANDBOX_RECONCILER_SWEEPS)
                .tag("outcome", "skipped")
                .description("Reconciliation sweeps skipped because an inventory they depend on was unreadable")
                .register(meterRegistry);
    }

    public void sweep() {
        Set<String> active;
        List<Job> jobs;
        List<Pod> hosts;
        try {
            // The job set is read before the Jobs, so a Job created in between is young, not unknown.
            active = jobRepository.findByStatusIn(List.of(ReviewJobStatus.QUEUED, ReviewJobStatus.RUNNING)).stream()
                    .map(ReviewJob::getId)
                    .map(Object::toString)
                    .collect(Collectors.toSet());
            jobs = reviewJobs.list();
            hosts = pods.list(KubernetesWorker.KIND_HOST);
        } catch (RuntimeException e) {
            log.warn(
                    "Skipping sandbox reconciliation — jobs, sandbox Jobs or Pods are unreadable: errorType={}",
                    e.getClass().getName());
            skippedSweeps.increment();
            return;
        }
        for (Job job : jobs) {
            Map<String, String> labels = labels(job);
            String jobId = labels.get(KubernetesWorker.JOB_ID);
            boolean ended = jobId != null
                    && !active.contains(jobId)
                    && !isYoung(job.getMetadata().getCreationTimestamp());
            if (!worker.createdBySelf(labels) || ended) {
                remove(job, reviewJobs::delete, orphanedJobs);
            }
        }
        for (Pod host : hosts) {
            if (!worker.createdBySelf(labels(host))) {
                remove(host, name -> pods.delete(name, 0), orphanedPods);
            }
        }
        completedSweeps.increment();
    }

    private static void remove(HasMetadata object, Consumer<String> delete, Counter removed) {
        String name = object.getMetadata().getName();
        try {
            delete.accept(name);
            log.warn("Removed an orphaned sandbox {}: {}", object.getKind(), name);
            removed.increment();
        } catch (SandboxException e) {
            log.warn(
                    "Failed to remove orphaned sandbox {} {}: errorType={}",
                    object.getKind(),
                    name,
                    e.getClass().getName());
        }
    }

    private boolean isYoung(@Nullable String creationTimestamp) {
        if (creationTimestamp == null) {
            return true;
        }
        try {
            return Instant.parse(creationTimestamp).isAfter(clock.instant().minus(REAP_GRACE));
        } catch (DateTimeParseException e) {
            return true;
        }
    }

    private static Map<String, String> labels(HasMetadata object) {
        Map<String, String> labels = object.getMetadata().getLabels();
        return labels == null ? Map.of() : labels;
    }
}
