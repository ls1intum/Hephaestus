package de.tum.cit.aet.hephaestus.integration.core.signal;

import java.util.Objects;

/**
 * The ledger's identity for one occurrence of one signal on one artifact — the tuple behind
 * {@code uq_artifact_signal}, and therefore the unit of "we have already dealt with this".
 *
 * <p>The artifact kind is not a member: it is read off the signal name, so a caller cannot state a
 * kind that disagrees with the signal it is recording.
 */
public record SignalKey(long workspaceId, long artifactId, SignalName signalName, SignalRevision revision) {
    public SignalKey {
        Objects.requireNonNull(signalName, "signalName must not be null");
        Objects.requireNonNull(revision, "revision must not be null");
        if (workspaceId <= 0) {
            throw new IllegalArgumentException("workspaceId must be positive, got " + workspaceId);
        }
        if (artifactId <= 0) {
            throw new IllegalArgumentException("artifactId must be positive, got " + artifactId);
        }
    }

    public ArtifactKind artifactKind() {
        return signalName.artifactKind();
    }
}
