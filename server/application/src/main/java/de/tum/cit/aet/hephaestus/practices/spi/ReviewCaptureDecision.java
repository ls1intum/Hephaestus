package de.tum.cit.aet.hephaestus.practices.spi;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public record ReviewCaptureDecision(
        long practiceId,
        ReviewPracticeStatus status,
        @Nullable String reason,
        JsonNode facts) {
    public ReviewCaptureDecision {
        facts = facts.deepCopy();
    }

    @Override
    public JsonNode facts() {
        return facts.deepCopy();
    }
}
