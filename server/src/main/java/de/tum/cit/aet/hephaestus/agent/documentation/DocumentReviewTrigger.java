package de.tum.cit.aet.hephaestus.agent.documentation;

import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;

/**
 * Offers a freshly recorded document signal for review.
 *
 * <p>Agent-owned, like {@link DocumentProjection}, and for the same reason: the module that mirrors
 * documents knows when one changed and nothing about reviews, while the module that submits reviews
 * knows nothing about wikis. The edge runs one way — {@code integration.outline} calls this after it has
 * written the ledger row, outside its own transaction, because submission opens one of its own.
 *
 * <p><strong>Recording and triggering stay separate.</strong> The ledger row is written whether or not
 * anything comes of it; this is the second, policy half. An implementation is free to refuse, and when
 * it does it settles the same ledger row with a reason — which is what makes "why did nothing happen to
 * my document?" a question with an answer.
 */
public interface DocumentReviewTrigger {
    /**
     * Consider the occurrence identified by {@code key} for a document review, and settle its ledger row
     * either way.
     *
     * <p>Called only for occurrences that were newly recorded: a redelivered webhook deduplicates in the
     * ledger and never reaches here.
     *
     * @param key the ledger identity of the occurrence — workspace, mirrored document, signal, revision
     * @param discoveredVia how the occurrence was learned about, which decides the population the
     *     resulting measurements belong to
     */
    void onDocumentSignal(SignalKey key, DiscoveredVia discoveredVia);
}
