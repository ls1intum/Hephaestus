package de.tum.cit.aet.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Schema(description = "An event that can start an automated practice review")
public record PracticeTriggerEventOptionDTO(
    @NonNull String event,
    @NonNull String displayName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean recommended
) {}
