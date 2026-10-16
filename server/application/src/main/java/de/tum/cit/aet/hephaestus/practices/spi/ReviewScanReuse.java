package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.UUID;

public interface ReviewScanReuse {
    /** Attempts each listed pair once, with durable progress, outside the driver's review transaction. */
    void reuseListed(long workspaceId, UUID reviewId, Instant deadline);
}
