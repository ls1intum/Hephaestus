package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.evidence.PrivacyClass;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Schema(description = "An evidence source allowed by the selected evidence profile")
public record PracticeEvidenceSourceOptionDTO(
    @NonNull String sourceKind,
    @NonNull String displayName,
    @NonNull String description,
    @NonNull PrivacyClass privacyClass,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean supportsComplete,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean supportsCurrent,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean supportsEmpty,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean authorizedForAutomatedReview
) {}
