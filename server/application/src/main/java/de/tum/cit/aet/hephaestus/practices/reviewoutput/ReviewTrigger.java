package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import org.jspecify.annotations.Nullable;

/**
 * What started a review, read from the first moment on the work that names the review. How the moment was found
 * comes first: a requested review, a backfill or a scheduled check is that, whatever the work did. Otherwise
 * the moment's own occasion says it.
 */
public enum ReviewTrigger {
    OPENED,
    READY,
    PUSHED,
    EDITED,
    REVIEWED,
    MERGED,
    CLOSED,
    PUBLISHED,
    SETTLED,
    REQUESTED,
    BACKFILL,
    SWEEP;

    /** Null for an occasion this list does not name, so the reader sees no trigger rather than a wrong one. */
    static @Nullable ReviewTrigger of(String signalName, DiscoveredVia discoveredVia) {
        switch (discoveredVia) {
            case MANUAL -> {
                return REQUESTED;
            }
            case BACKFILL -> {
                return BACKFILL;
            }
            case SWEEP -> {
                return SWEEP;
            }
            case EVENT, SYNC -> {
                // Named by the occasion below.
            }
        }
        return occasion(signalName);
    }

    /**
     * What a moment on the work was, whoever found it: a backfill that found a pull request opened says opened. A
     * requested review is a moment of its own. Null for an occasion this list does not name.
     */
    static @Nullable ReviewTrigger moment(String signalName, DiscoveredVia discoveredVia) {
        return discoveredVia == DiscoveredVia.MANUAL ? REQUESTED : occasion(signalName);
    }

    private static @Nullable ReviewTrigger occasion(String signalName) {
        return switch (signalName.substring(signalName.lastIndexOf('.') + 1)) {
            case "opened" -> OPENED;
            case "ready" -> READY;
            case "synchronized" -> PUSHED;
            case "edited", "updated", "linked_issue_updated" -> EDITED;
            case "reviewed" -> REVIEWED;
            case "merged" -> MERGED;
            case "closed" -> CLOSED;
            case "published" -> PUBLISHED;
            case "settled" -> SETTLED;
            case "manual_review" -> REQUESTED;
            default -> null;
        };
    }
}
