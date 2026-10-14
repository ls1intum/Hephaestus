package de.tum.cit.aet.hephaestus.core.llm.usage;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.modulith.NamedInterface;

/**
 * The parts of an UNPRICED {@code llm_usage_event} a reprice needs: the tokens to multiply, and
 * every clue the row carries about which catalogue entry it was charged against.
 *
 * <p>A projection rather than the entity, so the repricing pass cannot accidentally write anything
 * but the price through {@code applyResolvedPrice} — the ledger is append-only everywhere else, and
 * a managed entity in a repricing loop is one careless setter away from breaking that.
 */
@NamedInterface("llm-usage")
public record UnpricedLedgerRow(
        UUID id,
        Long workspaceId,
        @Nullable String model,
        UsageFundingSource fundingSource,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        long searchUnits,
        @Nullable Long appliedPriceId,
        @Nullable Long appliedWorkspaceModelId,
        MeasurementVerification measurementVerification) {

    public boolean hasTokenEvidence() {
        return inputTokens > 0 || outputTokens > 0 || cacheReadTokens > 0 || cacheWriteTokens > 0;
    }
}
