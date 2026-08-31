package de.tum.cit.aet.hephaestus.practices.observation.trend;

import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.PracticeGroupTrendDTO;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PracticeTrendService {

    private final TrendProperties properties;
    private final Clock clock;

    public Map<String, PracticeTrend> calculatePractices(Map<String, List<Observation>> evidenceByPractice) {
        Map<String, PracticeTrend> trends = new LinkedHashMap<>();
        var cutoff = clock.instant().minus(properties.getHorizonDays(), ChronoUnit.DAYS);
        evidenceByPractice.forEach((slug, evidence) ->
                trends.put(slug, PracticeTrendCalculator.calculatePractice(slug, evidence, cutoff, properties)));
        return trends;
    }

    public PracticeTrend calculateGroup(
            String groupSlug, Collection<String> eligiblePracticeSlugs, Collection<PracticeTrend> practiceTrends) {
        return GroupTrendAggregator.aggregate(groupSlug, eligiblePracticeSlugs, practiceTrends, properties);
    }

    public PracticeGroupTrendDTO detail(
            String groupSlug,
            Collection<String> eligiblePracticeSlugs,
            Map<String, List<Observation>> evidenceByPractice) {
        Map<String, PracticeTrend> all = calculatePractices(evidenceByPractice);
        List<PracticeTrend> practices = eligiblePracticeSlugs.stream()
                .map(slug -> all.getOrDefault(
                        slug, PracticeTrendCalculator.calculatePractice(slug, List.of(), clock.instant(), properties)))
                .toList();
        PracticeTrend group = calculateGroup(groupSlug, eligiblePracticeSlugs, practices);
        return new PracticeGroupTrendDTO(
                group.toDto(), practices.stream().map(PracticeTrend::toDto).toList());
    }
}
