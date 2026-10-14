package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ReviewRequest(
        long workspaceId,
        long itemId,
        ReviewKind kind,
        @Nullable ItemVersion version,
        ReviewOrigin origin,
        @Nullable UUID eventId,
        @Nullable Long requestedById,
        @Nullable UUID batchId,
        @Nullable BigDecimal budgetUsd,
        @Nullable UUID requestKey,
        @Nullable String requestDigest,
        List<Long> practiceIds,
        boolean reuse) {
    public ReviewRequest {
        practiceIds = List.copyOf(practiceIds);
    }
}
