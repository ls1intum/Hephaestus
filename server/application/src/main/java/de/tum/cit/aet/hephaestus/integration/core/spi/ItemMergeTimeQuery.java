package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Actual merge timestamps from the source owner, keyed by canonical workspace item id. */
public interface ItemMergeTimeQuery {
    Map<Long, Instant> mergedAt(long workspaceId, List<ItemRef> items);
}
