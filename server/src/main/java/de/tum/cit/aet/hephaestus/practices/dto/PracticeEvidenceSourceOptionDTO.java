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
     * Whether {@link de.tum.cit.aet.hephaestus.practices.EvidenceStance#EXHAUSTIVE} may be taken towards
     * this source.
     *
     * <p>Not derivable from {@link #requiredQuality}: {@code scm.pull-request.comments} sits at
     * {@code ANY_CAPTURE} and can still be captured whole, while {@code scm.linked-work-items} sits at the
     * same floor and never can. Without it published, an authoring surface offering "and nothing is
     * missing" would produce a request the validator refuses, so the endpoint whose whole job is to say
     * what an author may choose would be under-reporting the choice.
     */
    @Schema(
        description = "Whether this source can be captured whole, and so whether a practice may rest a claim about what is absent from it on the capture",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    boolean supportsExhaustiveEvidence
) {}
