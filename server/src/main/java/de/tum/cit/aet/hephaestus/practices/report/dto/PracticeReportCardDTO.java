package de.tum.cit.aet.hephaestus.practices.report.dto;

import de.tum.cit.aet.hephaestus.practices.report.PracticeStatus;
import de.tum.cit.aet.hephaestus.practices.report.PracticeTrend;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * One practice card on a developer's practice report — the third feedback channel (alongside the in-context
 * SCM notes and the conversational mentor), here as a self-paced surface where a developer READS the
 * feedback about a practice: why it matters, what good looks like, where they stand, what to act on, and
 * what they already do well.
 *
 * <p>The developer's own view and a mentor's drill-down render the same card, from one derivation.
 *
 * <p>Counts and observation enums are deliberately absent, and so is {@code criteria} — the
 * {@code whyItMatters} / {@code whatGoodLooksLike} learner framing is carried instead, preserving the
 * "criteria never reaches a learner" invariant.
 */
@Schema(description = "A developer's readable feedback for one practice")
public record PracticeReportCardDTO(
    @NonNull @Schema(description = "Practice slug") String slug,
    @NonNull @Schema(description = "Practice name") String name,
    @Nullable @Schema(description = "Area slug this practice belongs to, if any") String areaSlug,
    @Nullable @Schema(description = "Area name this practice belongs to, if any") String areaName,
    @Nullable @Schema(description = "Why this practice matters, in plain language") String whyItMatters,
    @Nullable @Schema(description = "A concrete picture of doing this well") String whatGoodLooksLike,
    @NonNull
    @Schema(
        description = "Where the developer stands on this practice, read against the practice's own standard",
        allowableValues = { "DEVELOPING", "STRENGTH", "MIXED" }
    )
    PracticeStatus status,
    @NonNull
    @Schema(
        description = "Direction versus the previous report window — the developer's own trajectory, never a peer comparison",
        allowableValues = { "IMPROVING", "WORSENING", "STEADY", "NEW" }
    )
    PracticeTrend trend,
    @NonNull
    @Schema(description = "Specific feedback to act on (highest-impact first)")
    List<PracticeReportItemDTO> toWorkOn,
    @NonNull @Schema(description = "What the developer already does well here") List<PracticeReportItemDTO> strengths
) {}
