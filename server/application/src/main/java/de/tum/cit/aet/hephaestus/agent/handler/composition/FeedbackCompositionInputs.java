package de.tum.cit.aet.hephaestus.agent.handler.composition;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.ObjectMapper;

public final class FeedbackCompositionInputs {
    private FeedbackCompositionInputs() {}

    public static void stage(
            Map<String, byte[]> files,
            ReviewKind kind,
            ReviewOrigin origin,
            Set<FeedbackDestination> destinations,
            ObjectMapper mapper) {
        if (kind == ReviewKind.SCAN || origin == ReviewOrigin.BACKFILL || destinations.isEmpty()) return;
        files.put(
                SandboxLayout.FEEDBACK_COMPOSITION_PATH,
                mapper.writeValueAsBytes(Map.of(
                        "contractVersion",
                        3,
                        "enabled",
                        true,
                        "destinations",
                        destinations.stream().map(Enum::name).sorted().toList(),
                        "minDistinctItems",
                        2)));
    }
}
