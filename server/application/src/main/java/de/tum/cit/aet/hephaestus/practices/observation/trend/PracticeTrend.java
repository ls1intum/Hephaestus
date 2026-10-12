package de.tum.cit.aet.hephaestus.practices.observation.trend;

import de.tum.cit.aet.hephaestus.practices.observation.ReviewedWorkKey;
import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.OutcomeVectorDTO;
import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.PracticeTrendDTO;
import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.TrendOpportunityDTO;
import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.TrendSupportDTO;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/** Complete internal trend result; developer-facing DTOs intentionally omit its scalar posterior diagnostics. */
public final class PracticeTrend {

    private final String slug;
    private final TrendScope scope;
    private final TrendDirection direction;
    private final TrendSupport support;
    /** Absent while the evidence is not comparable — the accessors below say the same. */
    private final @Nullable OutcomeVector currentOutcomes;

    private final @Nullable OutcomeVector previousOutcomes;
    private final List<EvidenceOpportunity> opportunities;

    /**
     * Every piece of work in the look-back with a decided opportunity, uncapped: {@link #opportunities} is cut to
     * what a chart draws, and a group's count of reviewed work must still reach past that cut.
     */
    private final Set<ReviewedWorkKey> reviewedWork;

    /** Absent for INSUFFICIENT_EVIDENCE, and for a group result, which aggregates rather than diffs. */
    private final BetaPosterior.@Nullable Difference difference;

    PracticeTrend(
            String slug,
            TrendScope scope,
            TrendDirection direction,
            TrendSupport support,
            @Nullable OutcomeVector currentOutcomes,
            @Nullable OutcomeVector previousOutcomes,
            List<EvidenceOpportunity> opportunities,
            Set<ReviewedWorkKey> reviewedWork,
            BetaPosterior.@Nullable Difference difference) {
        this.slug = slug;
        this.scope = scope;
        this.direction = direction;
        this.support = support;
        this.currentOutcomes = currentOutcomes;
        this.previousOutcomes = previousOutcomes;
        this.opportunities = List.copyOf(opportunities);
        this.reviewedWork = Set.copyOf(reviewedWork);
        this.difference = difference;
    }

    public String slug() {
        return slug;
    }

    public TrendDirection direction() {
        return direction;
    }

    public TrendSupport support() {
        return support;
    }

    /**
     * The recency-weighted share of MET outcomes across the newest {@code window} evidence opportunities,
     * or empty when none of them produced a verdict.
     *
     * <p>Opportunity-indexed like the trend itself, so a single busy day cannot manufacture a standing and a
     * quiet week cannot erode one — the unit is a reviewed work item, never a calendar bin. Opportunities that
     * carry no decided outcome are skipped rather than counted as either side, so a review that had no
     * chance to exercise the practice neither helps nor hurts, and it does not push genuine evidence out of
     * the window either.
     *
     * <p>Each opportunity contributes its own {@link OutcomeVector#metShare()}, not a clean/dirty bit, so
     * a piece of reviewed work that went half well is counted as half well rather than rounded to a problem.
     *
     * <p>Weights fall geometrically with age ({@code decay^0, decay^1, …} from the newest), so recent evidence
     * dominates: a fixed lapse is acknowledged within a couple of reviews without a separate clean-work
     * override, and a fresh regression is visible just as fast.
     * A {@code decay} strictly below 0.5 is what makes the two newest opportunities outweigh everything older
     * — see the caller that chooses it.
     *
     * <p>The share is what is left after the problems, measured against the weight of a full window: {@code 1 −
     * Σ weight·(1 − metShare) / Σ decay^age} over every age the window has, read or not. With fewer decided
     * opportunities than the window, the ages not reached yet count as neither a problem nor a success, so the
     * same problems weigh the same whatever the count. One problem on the newest piece of work therefore scores
     * the same with one, two, three or four pieces decided. Dividing by the weight of the opportunities read
     * instead would score one problem among few pieces lower, so the label would change with the count alone.
     *
     * @param window how many of the newest decided opportunities to consider, at least one
     * @param decay per-opportunity weight factor in {@code (0,1]}; 1.0 is an unweighted mean
     */
    public OptionalDouble recentMetShare(int window, double decay) {
        return recentMetShare(opportunities, window, decay);
    }

    /** {@link #recentMetShare(int, double)} over any opportunities, oldest first. */
    static OptionalDouble recentMetShare(List<EvidenceOpportunity> opportunities, int window, double decay) {
        List<EvidenceOpportunity> decided =
                opportunities.stream().filter(EvidenceOpportunity::decided).toList();
        if (decided.isEmpty()) {
            return OptionalDouble.empty();
        }
        List<EvidenceOpportunity> recent = decided.subList(Math.max(0, decided.size() - window), decided.size());
        double missed = 0.0;
        for (int index = recent.size() - 1, age = 0; index >= 0; index--, age++) {
            missed += Math.pow(decay, age) * (1.0 - recent.get(index).outcomes().metShare());
        }
        double fullWeight = 0.0;
        for (int age = 0; age < window; age++) {
            fullWeight += Math.pow(decay, age);
        }
        return OptionalDouble.of(1.0 - missed / fullWeight);
    }

    TrendScope scope() {
        return scope;
    }

    @Nullable
    OutcomeVector currentOutcomes() {
        return currentOutcomes;
    }

    @Nullable
    OutcomeVector previousOutcomes() {
        return previousOutcomes;
    }

    List<EvidenceOpportunity> opportunities() {
        return opportunities;
    }

    Set<ReviewedWorkKey> reviewedWork() {
        return reviewedWork;
    }

    BetaPosterior.@Nullable Difference difference() {
        return difference;
    }

    public PracticeTrendDTO toDto() {
        return new PracticeTrendDTO(
                slug,
                scope,
                direction,
                TrendSupportDTO.from(support),
                OutcomeVectorDTO.from(currentOutcomes),
                OutcomeVectorDTO.from(previousOutcomes),
                IntStream.range(0, opportunities.size())
                        .mapToObj(index -> {
                            EvidenceOpportunity opportunity = opportunities.get(index);
                            return new TrendOpportunityDTO(
                                    index,
                                    opportunity.occurredAt(),
                                    opportunity.artifactKind(),
                                    opportunity.artifactId(),
                                    new OutcomeVectorDTO(
                                            opportunity.outcomes().met(),
                                            opportunity.outcomes().notMet(),
                                            opportunity.outcomes().notApplicable(),
                                            opportunity.outcomes().undetermined()),
                                    opportunity.bundle());
                        })
                        .toList());
    }
}
