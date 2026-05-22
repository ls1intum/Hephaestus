package de.tum.cit.aet.hephaestus.core.runtime.worker;

import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.WorkerControlFrame;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link WorkerControlPublisher} when no control-channel endpoint is configured: frames
 * are logged at DEBUG, the channel reports DOWN. Replaced by {@code WorkerControlClient}
 * (a {@code @Primary} bean) whenever {@code hephaestus.worker.control.endpoint} is set.
 */
public class LoggingWorkerControlPublisher implements WorkerControlPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingWorkerControlPublisher.class);

    @Override
    public void send(WorkerControlFrame frame) {
        log.debug("worker.control.send type={}", frame.getClass().getSimpleName());
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public Instant lastInboundAt() {
        return Instant.EPOCH;
    }
}
