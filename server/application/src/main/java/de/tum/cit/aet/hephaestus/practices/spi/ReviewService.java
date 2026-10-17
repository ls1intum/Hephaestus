package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface ReviewService extends ReviewSnapshotQuery {
    UUID request(ReviewRequest request);

    boolean start(long workspaceId, UUID reviewId);

    void complete(long workspaceId, UUID reviewId, ReviewCompletion completion);

    boolean cancel(long workspaceId, UUID reviewId, @Nullable String reason);
}
