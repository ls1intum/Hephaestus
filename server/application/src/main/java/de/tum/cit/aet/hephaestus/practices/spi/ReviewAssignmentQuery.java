package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface ReviewAssignmentQuery {
    Optional<ReviewAssignmentSnapshot> find(long workspaceId, ReviewAssignmentId assignment);

    List<ReviewAssignmentId> executable(long workspaceId, UUID reviewId, long itemId);

    /**
     * Who a retained assignment was about, for history only: the item and the developer its review row names. Unlike
     * {@link #find}, it needs no executable practice version, so a retired practice still has its history.
     *
     * @param developerId the developer the review attributed the item to; {@code null} for a scan, or where the review
     *     attributed it to nobody
     */
    record Retained(
            ReviewAssignmentId id, ItemRef item, @Nullable Long developerId) {}

    /**
     * The retained assignments of one job of a review: those its frozen {@code ids} name or, when it recorded none, those
     * its review bound to {@code jobId}. Returns no assignments if any selected row or item is gone, a recorded key
     * cannot be read, or attribution is unreadable. A partial selection never stands for a complete run.
     */
    List<Retained> retained(long workspaceId, UUID reviewId, UUID jobId, Collection<ReviewAssignmentId> ids);
}
