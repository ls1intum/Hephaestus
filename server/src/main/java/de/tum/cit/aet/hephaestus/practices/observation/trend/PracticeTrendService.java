package de.tum.cit.aet.hephaestus.practices.observation.trend;

import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.PracticeAreaTrendDTO;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The application's only producer of longitudinal practice and area trend results. */
@Service
@RequiredArgsConstructor
public class PracticeTrendService {

    private final TrendProperties properties;
    private final Clock clock;

    public Map<String, PracticeTrend> calculatePractices(Map<String, List<Observation>> evidenceByPractice) {
        Map<String, PracticeTrend> trends = new LinkedHashMap<>();
        var cutoff = clock.instant().minus(properties.getHorizonDays(), ChronoUnit.DAYS);
        evidenceByPractice.forEach((slug, evidence) ->
            trends.put(slug, PracticeTrendCalculator.calculatePractice(slug, evidence, cutoff, properties))
        );
        return trends;
    }

    public PracticeTrend calculateArea(
        String areaSlug,
        Collection<String> eligiblePracticeSlugs,
        Collection<PracticeTrend> practiceTrends,
        Map<String, Double> weights
    ) {
        return PracticeTrendCalculator.aggregateArea(
            areaSlug,
            eligiblePracticeSlugs,
            practiceTrends,
            weights,
            properties
        );
    }

    public PracticeAreaTrendDTO detail(
        String areaSlug,
        Collection<String> eligiblePracticeSlugs,
        Map<String, List<Observation>> evidenceByPractice,
        Map<String, Double> weights
    ) {
        Map<String, PracticeTrend> all = calculatePractices(evidenceByPractice);
        List<PracticeTrend> practices = eligiblePracticeSlugs
            .stream()
            .map(slug ->
                all.getOrDefault(
                    slug,
                    PracticeTrendCalculator.calculatePractice(slug, List.of(), clock.instant(), properties)
                )
            )
            .toList();
        PracticeTrend area = calculateArea(areaSlug, eligiblePracticeSlugs, practices, weights);
        return new PracticeAreaTrendDTO(area.toDto(), practices.stream().map(PracticeTrend::toDto).toList());
    }
}
