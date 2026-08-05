package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure domain calculator for day-to-day practice trajectories. */
final class PracticeTrajectoryCalculator {

    private PracticeTrajectoryCalculator() {}

    /**
     * Calculates one trajectory per practice. Each artifact first receives a score in {@code [-1, 1]}
     * ({@code GOOD=+1}, {@code BAD=-1}); the daily score is the mean of its artifact scores. Comparing daily
     * means keeps a busy day from looking worse merely because it produced more observations. A practice with
     * only one evidence-bearing day has a current snapshot but deliberately no direction yet.
     */
    static Map<String, PracticeTrajectory> calculate(Map<String, List<Observation>> evidenceByPractice) {
        Map<String, PracticeTrajectory> trajectories = new LinkedHashMap<>();
        for (Map.Entry<String, List<Observation>> entry : evidenceByPractice.entrySet()) {
            List<DailyScore> dailyScores = dailyScores(entry.getValue());
            if (dailyScores.isEmpty()) {
                continue;
            }
            DailyScore current = dailyScores.get(0);
            if (dailyScores.size() == 1) {
                trajectories.put(
                    entry.getKey(),
                    new PracticeTrajectory(entry.getKey(), null, 0.0, current.evidenceCount(), 0, current.date(), null)
                );
                continue;
            }
            DailyScore previous = dailyScores.get(1);
            double delta = current.score() - previous.score();
            trajectories.put(
                entry.getKey(),
                new PracticeTrajectory(
                    entry.getKey(),
                    AreaTrajectory.fromDelta(delta),
                    delta,
                    current.evidenceCount(),
                    previous.evidenceCount(),
                    current.date(),
                    previous.date()
                )
            );
        }
        return trajectories;
    }

    private static List<DailyScore> dailyScores(List<Observation> observations) {
        Map<LocalDate, Map<ArtifactKey, List<Assessment>>> byDayAndArtifact = new LinkedHashMap<>();
        for (Observation observation : observations) {
            if (observation.getAssessment() == null) {
                continue;
            }
            LocalDate day = observation.getObservedAt().atZone(ZoneOffset.UTC).toLocalDate();
            ArtifactKey artifact = new ArtifactKey(observation.getArtifactType(), observation.getArtifactId());
            byDayAndArtifact
                .computeIfAbsent(day, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(artifact, ignored -> new ArrayList<>())
                .add(observation.getAssessment());
        }

        return byDayAndArtifact
            .entrySet()
            .stream()
            .map(entry -> toDailyScore(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(DailyScore::date).reversed())
            .toList();
    }

    private static DailyScore toDailyScore(LocalDate date, Map<ArtifactKey, List<Assessment>> byArtifact) {
        double score = byArtifact
            .values()
            .stream()
            .mapToDouble(PracticeTrajectoryCalculator::artifactScore)
            .average()
            .orElse(0.0);
        return new DailyScore(date, score, byArtifact.size());
    }

    private static double artifactScore(List<Assessment> assessments) {
        return assessments
            .stream()
            .mapToInt(a -> a == Assessment.GOOD ? 1 : -1)
            .average()
            .orElse(0.0);
    }

    private record ArtifactKey(WorkArtifact type, Long id) {}

    private record DailyScore(LocalDate date, double score, int evidenceCount) {}
}
