package de.tum.cit.aet.hephaestus.practices.finding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * One practice card on the reflective dashboard — the third feedback channel (alongside the in-context SCM
 * notes and the conversational mentor), here as a self-paced surface where a developer READS the feedback
 * about a practice: why it matters, what good looks like, where they stand, what to act on, and what they
 * already do well.
 *
 * <p>It is the conversational/in-context substance reorganised by practice — NOT a scoreboard. Counts and
 * observation enums are deliberately absent; so is {@code criteria} (the {@code whyItMatters} /
 * {@code whatGoodLooksLike} learner framing is carried instead, preserving the "criteria never reaches a
 * learner" invariant). Items are the actual findings, deduped to each target's latest review and with the
 * "not applicable / no change needed" noise removed.
 */
@Schema(description = "A developer's readable feedback for one practice")
public record ReflectionPracticeDTO(
    @NonNull @Schema(description = "Practice slug") String slug,
    @NonNull @Schema(description = "Practice name") String name,
    @Nullable @Schema(description = "Area slug this practice belongs to, if any") String areaSlug,
    @Nullable @Schema(description = "Area name this practice belongs to, if any") String areaName,
    @Nullable @Schema(description = "Why this practice matters, in plain language") String whyItMatters,
    @Nullable @Schema(description = "A concrete picture of doing this well") String whatGoodLooksLike,
    @NonNull
    @Schema(
        description = "Where the developer stands on this practice",
        allowableValues = { "DEVELOPING", "STRENGTH", "MIXED" }
    )
    Standing standing,
    @NonNull
    @Schema(description = "Specific feedback to act on (highest-impact first)")
    List<ReflectionItemDTO> toWorkOn,
    @NonNull @Schema(description = "What the developer already does well here") List<ReflectionItemDTO> strengths
) {
    /** Coarse, human standing derived from the developer's latest-run feedback on this practice. */
    public enum Standing {
        /** Only problems, or problems outweigh strengths — the focus of attention. */
        DEVELOPING,
        /** Only strengths — a confirmed good habit. */
        STRENGTH,
        /** Both problems and strengths across the developer's work. */
        MIXED,
    }
}
