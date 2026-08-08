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
    RequiredCaptureQuality requiredQuality,
    /**
     * Whether {@code EXHAUSTIVE} may be taken towards
     * this source.
     *
     * <p>Not derivable from {@code requiredQuality}: two sources at the same floor can differ on whether
     * a complete capture is reachable at all. Unpublished, an authoring surface would offer "and nothing
     * is missing" for a source the validator then refuses.
     */
    @Schema(
        description = "Whether this source can be captured whole, and so whether a practice may rest a claim about what is absent from it on the capture",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    boolean supportsExhaustiveEvidence
) {}
