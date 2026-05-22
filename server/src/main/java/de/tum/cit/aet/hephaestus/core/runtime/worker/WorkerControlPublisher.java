package de.tum.cit.aet.hephaestus.core.runtime.worker;

import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.WorkerControlFrame;
import java.time.Instant;

/**
 * Substrate ↔ transport seam. Methods are thread-safe; implementations queue and drain frames on
 * their own writer thread. Disconnected sends are dropped after a bounded queue — capacity
 * reports are latest-wins, session frames between reconnects are treated as session loss.
 */
public interface WorkerControlPublisher {
    void send(WorkerControlFrame frame);

    boolean isConnected();

    /** Sentinel {@link Instant#EPOCH} when no frame has been received yet. */
    Instant lastInboundAt();
}
