package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

public record UpdateSyncJobRequestDTO(
    @NonNull
    @NotNull(message = "cancelRequested is required")
    @AssertTrue(message = "cancelRequested must be true")
    Boolean cancelRequested
) {}
