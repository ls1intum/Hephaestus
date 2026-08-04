package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.evidence.PrivacyClass;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Schema(description = "An evidence source that a practice may require or use as optional context")
public record PracticeEvidenceSourceOptionDTO(
    @NonNull String sourceKind,
    @NonNull String description,
    @NonNull PrivacyClass privacyClass,
    boolean supportsComplete,
    boolean supportsCurrent,
    boolean supportsEmpty,
    boolean authorizedForDetection
) {}
