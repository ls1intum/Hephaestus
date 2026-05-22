package de.tum.cit.aet.hephaestus.agent.runtime.worker.testing;

import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerControlPublisher;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.WorkerControlFrame;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CapturingPublisher implements WorkerControlPublisher {

    public final List<WorkerControlFrame> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(WorkerControlFrame frame) {
        sent.add(frame);
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public Instant lastInboundAt() {
        return Instant.now();
    }
}
