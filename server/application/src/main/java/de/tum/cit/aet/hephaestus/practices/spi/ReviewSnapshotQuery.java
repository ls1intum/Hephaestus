package de.tum.cit.aet.hephaestus.practices.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Frozen product facts needed to prepare a review on the server or worker. */
public interface ReviewSnapshotQuery {
    Optional<ReviewSnapshot> find(long workspaceId, UUID reviewId);

    Optional<Instant> publicReviewedAt(long workspaceId, UUID reviewId);

    Optional<BigDecimal> budgetUsd(long workspaceId, UUID reviewId);
}
