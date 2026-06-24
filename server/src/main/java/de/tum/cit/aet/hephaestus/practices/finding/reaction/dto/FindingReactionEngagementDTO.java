package de.tum.cit.aet.hephaestus.practices.finding.reaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Reaction engagement for the current developer in a workspace, reported along two orthogonal axes: a
 * RESPONSE axis (what the developer did with the feedback) and a VALIDITY axis (whether the finding applied
 * at all).
 *
 * <p><b>{@code notApplicable} is a VALIDITY/scope signal, not an uptake count</b> — it MUST NOT be folded
 * into a response/uptake ratio with {@code addressed}/{@code disputed}. The uptake denominator is
 * {@code addressed + disputed} (the findings the developer engaged with as feedback); a high
 * {@code notApplicable} means the detector is mis-scoped, which is a separate signal.
 *
 * <p>Zero counts are returned as 0, not omitted.
 */
@Schema(description = "Reaction engagement for a developer, split into the response (uptake) and validity axes")
public record FindingReactionEngagementDTO(
    // --- RESPONSE axis (uptake): the findings the developer engaged with as feedback ---
    @Schema(
        description = "RESPONSE: findings the developer acted on (the recipience act, not the outcome)",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    long addressed,
    @Schema(
        description = "RESPONSE: findings the developer rejected with a reasoned explanation",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    long disputed,
    // --- VALIDITY axis (scope signal): NOT part of any uptake ratio ---
    @Schema(
        description = "VALIDITY: findings marked out-of-scope — a detector-scope signal, NOT an uptake count",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    long notApplicable
) {}
