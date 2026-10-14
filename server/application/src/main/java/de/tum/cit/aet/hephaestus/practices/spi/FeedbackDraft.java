package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record FeedbackDraft(
        UUID id,
        long workspaceId,
        UUID reviewId,
        @Nullable Long itemId,
        long developerId,
        FeedbackDestination destination,
        FeedbackState state,
        @Nullable FeedbackStopReason stopReason,
        @Nullable String body,
        List<FeedbackBasisInput> basis,
        List<FeedbackCopyInput> copies,
        Map<FeedbackCheckStage, FeedbackChecks> checks) {
    public FeedbackDraft {
        basis = List.copyOf(basis);
        copies = List.copyOf(copies);
        checks = Map.copyOf(checks);
    }
}
