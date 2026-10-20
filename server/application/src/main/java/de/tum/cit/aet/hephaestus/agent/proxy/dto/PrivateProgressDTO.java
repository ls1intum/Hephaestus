package de.tum.cit.aet.hephaestus.agent.proxy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

/**
 * What an earlier attempt of this review already prepared privately: the admitted results its pieces decide, and the
 * pieces composed for delivery on each private destination. It carries no words.
 */
public record PrivateProgressDTO(
        @NonNull List<UUID> decidedResultIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int practicePageDeliveries,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int conversationDeliveries) {
    public PrivateProgressDTO {
        decidedResultIds = List.copyOf(decidedResultIds);
    }
}
