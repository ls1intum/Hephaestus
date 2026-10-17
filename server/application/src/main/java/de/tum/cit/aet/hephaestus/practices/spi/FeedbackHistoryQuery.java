package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.List;

public interface FeedbackHistoryQuery {
    List<FeedbackHistoryEntry> forDeveloper(long workspaceId, long developerAccountId, Instant since, int limit);

    List<FeedbackHistoryEntry> forItem(long workspaceId, long itemId, long viewerAccountId, int limit);
}
