package de.tum.cit.aet.hephaestus.practices.trace;

import de.tum.cit.aet.hephaestus.integration.core.spi.FoundBy;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState;
import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequestReason;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface ItemTraceRepository extends Repository<Review, UUID> {
    String EVENT_COLUMNS = "id,item_id AS itemId,name,item_version AS itemVersion,occurred_at AS occurredAt,"
            + "found_by AS foundBy,state,reason,decided_at AS decidedAt,review_id AS reviewId";

    @Query(value = "SELECT " + EVENT_COLUMNS + " FROM item_event WHERE workspace_id=:ws AND id=:id", nativeQuery = true)
    Optional<EventRow> event(long ws, UUID id);

    @Query(value = """
        SELECT q.* FROM review q WHERE q.workspace_id=:ws AND (q.item_id=:item OR EXISTS(
          SELECT 1 FROM review_item m WHERE m.workspace_id=q.workspace_id AND m.review_id=q.id AND m.item_id=:item))
        ORDER BY q.ordered_at DESC,q.id::text DESC LIMIT 100
        """, nativeQuery = true)
    List<Review> reviewsForItem(long ws, long item);

    @Query(
            value = "SELECT " + EVENT_COLUMNS
                    + " FROM item_event WHERE workspace_id=:ws AND item_id=:item ORDER BY occurred_at"
                    + " DESC,id::text DESC",
            countQuery = "SELECT count(*) FROM item_event WHERE workspace_id=:ws AND item_id=:item",
            nativeQuery = true)
    Page<EventRow> events(long ws, long item, Pageable page);

    interface EventRow {
        UUID getId();

        long getItemId();

        String getName();

        String getItemVersion();

        Instant getOccurredAt();

        FoundBy getFoundBy();

        ItemEventState getState();

        @Nullable
        ReviewRequestReason getReason();

        @Nullable
        Instant getDecidedAt();

        @Nullable
        UUID getReviewId();
    }
}
