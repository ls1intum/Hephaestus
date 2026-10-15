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
        Map<FeedbackCheckStage, FeedbackChecks> checks,
        @Nullable FeedbackCompositionTrace composition,
        @Nullable Page page) {
    public record Page(String title, String body, String nextStep) {}

    public FeedbackDraft {
        if (page != null && (destination != FeedbackDestination.PRACTICE_PAGE || body != null))
            throw new IllegalArgumentException(
                    "Structured page feedback needs the practice-page destination and no rendered body.");
        basis = List.copyOf(basis);
        copies = List.copyOf(copies);
        checks = Map.copyOf(checks);
    }
}
