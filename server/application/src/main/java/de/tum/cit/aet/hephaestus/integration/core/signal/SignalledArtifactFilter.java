package de.tum.cit.aet.hephaestus.integration.core.signal;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Which artifacts {@link ArtifactSignalRepository#findSignalledArtifacts} lists for one workspace: optionally one kind
 * or one artifact, the signals that occurred in the half-open window {@code [from, to)}, and with
 * {@code withoutReview} only the artifacts none of whose signals in that window started a review. A null bound drops
 * out of the predicate.
 */
public record SignalledArtifactFilter(
        Long workspaceId,
        @Nullable String artifactKind,
        @Nullable Long artifactId,
        @Nullable Instant from,
        @Nullable Instant to,
        boolean withoutReview) {}
