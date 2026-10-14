package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.List;

public record FeedbackChecks(List<FeedbackCheck> checks, Instant evaluatedAt) {
    public FeedbackChecks {
        checks = List.copyOf(checks);
    }
}
