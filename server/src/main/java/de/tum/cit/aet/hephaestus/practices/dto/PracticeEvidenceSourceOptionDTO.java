package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.evidence.PrivacyClass;
import de.tum.cit.aet.hephaestus.evidence.RequiredCaptureQuality;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Schema(description = "An evidence source a practice on this kind of work may read")
public record PracticeEvidenceSourceOptionDTO(
    @NonNull String sourceKind,
    @NonNull String displayName,
    @NonNull String description,
    @NonNull PrivacyClass privacyClass,
    @NonNull
    @Schema(description = "What requiring this source demands of its capture; fixed by the source contract")
    RequiredCaptureQuality requiredQuality
) {}
