package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Exact support and quiet decisions retained with the immutable preparation receipt. */
public record FeedbackCompositionTrace(
        List<CopySupport> copies,
        List<QuietDecision> withheld,
        @Nullable UUID replacesId,
        @Nullable UUID primaryResultId) {
    public FeedbackCompositionTrace {
        copies = List.copyOf(copies);
        withheld = List.copyOf(withheld);
    }

    public record Support(UUID resultId, @Nullable UUID problemId) {}

    public record CopySupport(FeedbackCopyKind kind, int position, List<Support> basedOn) {
        public CopySupport {
            basedOn = List.copyOf(basedOn);
            if (position < 0 || basedOn.isEmpty())
                throw new IllegalArgumentException("A copy needs its exact support and position.");
        }
    }

    public record QuietDecision(List<Support> basedOn, String reason, List<String> witnessIds) {
        public QuietDecision {
            basedOn = List.copyOf(basedOn);
            witnessIds = List.copyOf(witnessIds);
            if (basedOn.isEmpty()
                    || !List.of("BELOW_BAR", "ALREADY_SAID", "NO_MATERIAL_CHANGE")
                            .contains(reason)
                    || (!reason.equals("BELOW_BAR") && witnessIds.isEmpty()))
                throw new IllegalArgumentException("A quiet decision needs its reason and prior-advice witness.");
        }
    }
}
