package de.tum.cit.aet.hephaestus.practices.result.trend;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

record EvidenceOpportunity(
        @NonNull ItemKind itemKind,
        long itemId,
        @NonNull UUID reviewId,
        @NonNull Instant occurredAt,
        @NonNull OutcomeVector outcomes,
        @NonNull TrendBundle bundle) {
    long key() {
        return itemId;
    }

    boolean decided() {
        return outcomes.decided() > 0;
    }

    boolean clean() {
        return decided() && outcomes.notMet() == 0;
    }

    EvidenceOpportunity withBundle(TrendBundle value) {
        return new EvidenceOpportunity(itemKind, itemId, reviewId, occurredAt, outcomes, value);
    }
}
