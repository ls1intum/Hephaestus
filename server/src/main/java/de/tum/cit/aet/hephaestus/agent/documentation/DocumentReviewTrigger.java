package de.tum.cit.aet.hephaestus.agent.documentation;

import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;

/**
 * Offers a freshly recorded document signal for review.
 *
 * <p>Agent-owned, like {@link DocumentProjection}: the module that mirrors documents knows when one
 * changed and nothing about reviews, while the module that submits reviews knows nothing about wikis.
 * {@code integration.outline} calls this after it has written the ledger row, outside its own transaction.
 *
 * <p>Recording and triggering stay separate: the ledger row is written whether or not anything comes of
 * it. An implementation is free to refuse, settling the same row with a reason — what makes "why did
 * nothing happen to my document?" a question with an answer.
 */
public interface DocumentReviewTrigger {
    /**
     * Considers the occurrence identified by {@code key} for a document review, and settles its ledger row
     * either way. Called only for newly recorded occurrences: a redelivered webhook deduplicates in the
     * ledger and never reaches here.
     */
    void onDocumentSignal(SignalKey key, DiscoveredVia discoveredVia);
}
