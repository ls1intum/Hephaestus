package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface FeedbackHistoryQuery {
    List<FeedbackHistoryEntry> forDeveloper(long workspaceId, long developerAccountId, Instant since, int limit);

    List<FeedbackHistoryEntry> forItem(long workspaceId, long itemId, long viewerAccountId, int limit);

    /** The publication context of the same read; issue it inside the snapshot that reads the history page. */
    String publicationContext(long workspaceId, long itemId, UUID reviewId, Collection<Long> developerIds);

    /** Why a public piece composed from {@code publicationContext} cannot be prepared now. */
    Optional<FeedbackStopReason> publicationRefusal(
            long workspaceId, long itemId, UUID reviewId, long developerId, @Nullable String publicationContext);
}
