package de.tum.cit.aet.hephaestus.practices.observation.trend;

import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.OutcomeVectorDTO;
import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.PracticeTrendDTO;
import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.TrendOpportunityDTO;
import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.TrendSupportDTO;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Complete internal trend result; learner DTOs intentionally omit its scalar posterior diagnostics. */
public final class PracticeTrend {

    private final String slug;
    private final TrendScope scope;
    private final TrendDirection direction;
    private final TrendSupport support;
    private final OutcomeVector currentOutcomes;
    private final OutcomeVector previousOutcomes;
    private final List<EvidenceOpportunity> opportunities;
    private final BetaPosterior.Difference difference;

    PracticeTrend(
        String slug,
        TrendScope scope,
        TrendDirection direction,
        TrendSupport support,
        @Nullable OutcomeVector currentOutcomes,
        @Nullable OutcomeVector previousOutcomes,
        List<EvidenceOpportunity> opportunities,
        BetaPosterior.Difference difference
    ) {
        this.slug = slug;
        this.scope = scope;
        this.direction = direction;
        this.support = support;
        this.currentOutcomes = currentOutcomes;
        this.previousOutcomes = previousOutcomes;
        this.opportunities = List.copyOf(opportunities);
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
     * How many of the newest evidence opportunities carry no problem, counted back from the latest.
     *
     * <p>Opportunity-indexed like the trend itself, so a single busy day cannot mint a streak and a quiet
     * week cannot break one — the unit is a reviewed work item, never a calendar bin. Opportunities that
     * carry no applicable outcome are neither clean nor dirty and are skipped rather than counted, so a
     * practice a review had no chance to exercise does not silently extend the streak.
     */
    public int trailingCleanOpportunities() {
        int streak = 0;
        for (int index = opportunities.size() - 1; index >= 0; index--) {
            EvidenceOpportunity opportunity = opportunities.get(index);
            if (!opportunity.applicable()) {
                continue;
            }
            if (opportunity.outcomes().negatives() > 0) {
                break;
            }
            streak++;
        }
        return streak;
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

    BetaPosterior.Difference difference() {
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
            java.util.stream.IntStream.range(0, opportunities.size())
                .mapToObj(index -> {
                    EvidenceOpportunity opportunity = opportunities.get(index);
                    return new TrendOpportunityDTO(
                        index,
                        opportunity.occurredAt(),
                        opportunity.artifactKind(),
                        opportunity.artifactId(),
                        new OutcomeVectorDTO(
                            opportunity.outcomes().demonstratedStrengths(),
                            opportunity.outcomes().safeAvoidances(),
                            opportunity.outcomes().commissionProblems(),
                            opportunity.outcomes().omissionGaps(),
                            opportunity.outcomes().notApplicable()
                        ),
                        opportunity.bundle()
                    );
                })
                .toList()
        );
    }
}
