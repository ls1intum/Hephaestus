package de.tum.cit.aet.hephaestus.core.runtime.hub;

import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.CancelJob;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Commit cancellation before requesting a worker stop; disconnected workers remain subject to reconciliation.
public class WorkerJobCancelDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WorkerJobCancelDispatcher.class);

    private final WorkerSessionRegistry registry;

    public WorkerJobCancelDispatcher(WorkerSessionRegistry registry) {
        this.registry = registry;
    }

    /**
     * Send a {@link CancelJob} to {@code workerId} if it has a live session on this hub.
     *
     * @return {@code true} if the cancellation request was sent to a connected worker
     */
    public boolean sendCancel(@Nullable String workerId, UUID jobId, String reason) {
        if (workerId == null || workerId.isBlank()) {
            return false;
        }
        return registry.findByWorkerId(workerId)
                .map(session -> {
                    session.send(new CancelJob(jobId.toString(), reason));
                    log.info("Sent cancellation request for {} to worker {}", jobId, workerId);
                    return true;
                })
                .orElseGet(() -> {
                    log.debug("Worker {} is disconnected; cancellation request for {} was not sent", workerId, jobId);
                    return false;
                });
    }
}
