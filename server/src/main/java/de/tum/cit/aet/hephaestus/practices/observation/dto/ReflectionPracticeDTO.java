package de.tum.cit.aet.hephaestus.practices.observation.dto;

import de.tum.cit.aet.hephaestus.practices.observation.trend.TrendDirection;
import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.TrendSupportDTO;
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
 * learner" invariant). Items are the actual observations, deduped to each target's latest review and with the
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
        description = "Where the developer stands on this practice, or why no standing could be formed",
        allowableValues = { "DEVELOPING", "STRENGTH", "MIXED", "NOT_OBSERVED", "NO_OPPORTUNITY" }
    )
    Standing standing,
    @NonNull
    @Schema(description = "Specific feedback to act on (highest-impact first)")
    List<ReflectionItemDTO> toWorkOn,
    @NonNull @Schema(description = "What the developer already does well here") List<ReflectionItemDTO> strengths,
    @Nullable
    @Schema(
        description = "Opportunity-indexed direction of this practice's recent evidence",
        allowableValues = { "IMPROVING", "DECLINING", "UNCERTAIN", "INSUFFICIENT_EVIDENCE" }
    )
    TrendDirection trajectory,
    @Nullable @Schema(description = "Evidence support and provenance for the trajectory") TrendSupportDTO trendSupport
) {
    /**
     * Coarse, human standing derived from the developer's most recent evidence for this practice.
     *
     * <p>The first three are verdicts, read off the share of that evidence that was positive. The last two are
     * the reasons there is NO verdict, and they are deliberately separate: a learner cannot act on an empty
     * state that means "nothing was reviewed" and "your work offered no opportunity" interchangeably. The same
     * split runs through the area status and, as {@code UNCERTAIN} against {@code INSUFFICIENT_EVIDENCE},
     * through the trend — absence of a claim is not the same fact as a claim of absence.
     *
     * <p>Carrying the non-verdicts HERE rather than beside the cards is what lets the area be a pure roll-up of
     * its practices. It costs a longer list: a practice the workspace watches appears even when it has nothing
     * to say yet, which is also the honest answer to "what is being looked at".
     */
    public enum Standing {
        /** Recent evidence was mostly problems — the focus of attention. */
        DEVELOPING,
        /** Recent evidence was almost entirely positive — a confirmed good habit. */
        STRENGTH,
        /** Recent evidence carries both sides in comparable measure. */
        MIXED,
        /** No observation reached this practice at all. */
        NOT_OBSERVED,
        /**
         * The practice was evaluated but produced nothing to report: the reviewed work offered no relevant
         * opportunity, the evidence did not settle the question, or a defect-detector practice ran clean.
         */
        NO_OPPORTUNITY,
    }

    /** Whether this standing is a claim about the developer's work, rather than a reason none could be made. */
    public static boolean isVerdict(Standing standing) {
        return standing == Standing.DEVELOPING || standing == Standing.MIXED || standing == Standing.STRENGTH;
    }
}
