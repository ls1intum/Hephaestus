package de.tum.cit.aet.hephaestus.core.runtime.worker.protocol;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * First worker-originated frame after WSS upgrade. A non-overlapping {@code supportedVersions}
 * closes the socket with WS code 4400.
 */
public record WorkerHello(
    String workerId,
    List<Integer> supportedVersions,
    @Nullable String runtimeVersion
) implements WorkerControlFrame {
    public WorkerHello {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        if (supportedVersions == null || supportedVersions.isEmpty()) {
            throw new IllegalArgumentException("supportedVersions must not be empty");
        }
        supportedVersions = List.copyOf(supportedVersions);
    }
}
