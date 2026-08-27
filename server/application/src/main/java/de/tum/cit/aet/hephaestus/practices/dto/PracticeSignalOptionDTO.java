package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Schema(description = "A signal a practice can start an automated review on")
public record PracticeSignalOptionDTO(
        @NonNull @Schema(example = "scm.pull_request.ready") SignalName signal,
        @NonNull String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean recommended) {}
