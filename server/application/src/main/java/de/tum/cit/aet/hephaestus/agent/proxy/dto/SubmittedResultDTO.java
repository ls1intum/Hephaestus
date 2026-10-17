package de.tum.cit.aet.hephaestus.agent.proxy.dto;

import de.tum.cit.aet.hephaestus.practices.spi.ReadReceipt;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

public record SubmittedResultDTO(
        String practiceSlug, String itemId, String itemVersion, JsonNode candidate, List<ReadReceipt> readReceipts) {
    public SubmittedResultDTO {
        Objects.requireNonNull(practiceSlug, "practiceSlug");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(itemVersion, "itemVersion");
        Objects.requireNonNull(candidate, "candidate");
        readReceipts = List.copyOf(readReceipts);
        if (!practiceSlug.matches("[a-z0-9][a-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("Invalid practice slug");
        }
        if (!itemId.matches("[1-9][0-9]*")) throw new IllegalArgumentException("Invalid item id");
        if (itemVersion.isBlank() || itemVersion.length() > 1024) {
            throw new IllegalArgumentException("Invalid item version");
        }
    }
}
