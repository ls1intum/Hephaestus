package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.UUID;

/** Checks placement against the retained, pinned diff without treating valid evidence as a line anchor. */
public interface ReviewInlineAnchorQuery {
    boolean hasInlineAnchor(long workspaceId, UUID reviewId, UUID resultId);
}
