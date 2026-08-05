package de.tum.cit.aet.hephaestus.practices.observation;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PracticeTrajectoryCalculatorTest {

    @Test
    void shouldKeepDirectionEmptyWhenPracticeHasOnlyOneEvidenceDay() {
        PracticeTrajectory trajectory = calculate(observation(Assessment.BAD, "2026-08-05T09:00:00Z", 1L));

        assertThat(trajectory.direction()).isNull();
        assertThat(trajectory.currentEvidenceCount()).isEqualTo(1);
        assertThat(trajectory.previousAsOf()).isNull();
    }

    @Test
    void shouldClassifyDailyPracticeImprovementIndependentOfWorkVolume() {
        PracticeTrajectory trajectory = calculate(
            observation(Assessment.BAD, "2026-08-04T09:00:00Z", 1L),
            observation(Assessment.GOOD, "2026-08-05T09:00:00Z", 2L),
            observation(Assessment.BAD, "2026-08-05T10:00:00Z", 3L),
            observation(Assessment.GOOD, "2026-08-05T11:00:00Z", 4L)
        );

        assertThat(trajectory.direction()).isEqualTo(AreaTrajectory.IMPROVING);
        assertThat(trajectory.currentEvidenceCount()).isEqualTo(3);
        assertThat(trajectory.previousEvidenceCount()).isEqualTo(1);
        assertThat(trajectory.scoreDelta()).isPositive();
    }

    @Test
    void shouldClassifySamePerArtifactRatioAsSteadyWhenCurrentDayHasMoreWork() {
        PracticeTrajectory trajectory = calculate(
            observation(Assessment.BAD, "2026-08-04T09:00:00Z", 1L),
            observation(Assessment.GOOD, "2026-08-04T10:00:00Z", 2L),
            observation(Assessment.BAD, "2026-08-05T09:00:00Z", 3L),
            observation(Assessment.GOOD, "2026-08-05T10:00:00Z", 4L),
            observation(Assessment.BAD, "2026-08-05T11:00:00Z", 5L),
            observation(Assessment.GOOD, "2026-08-05T12:00:00Z", 6L)
        );

        assertThat(trajectory.direction()).isEqualTo(AreaTrajectory.STEADY);
    }

    @Test
    void shouldLetPracticeWeightsInfluenceAreaDirectionWithoutChangingPracticeCalculation() {
        PracticeTrajectory improving = trajectory("testing", AreaTrajectory.IMPROVING, 1.0);
        PracticeTrajectory regressing = trajectory("naming", AreaTrajectory.REGRESSING, -1.0);

        AreaTrajectoryAggregator.AreaTrajectorySignal result = AreaTrajectoryAggregator.aggregate(
            List.of(improving, regressing),
            Map.of("testing", 2.0, "naming", 1.0)
        );

        assertThat(result).isNotNull();
        assertThat(result.direction()).isEqualTo(AreaTrajectory.IMPROVING);
        assertThat(result.scoreDelta()).isEqualTo(1.0 / 3.0);
        assertThat(result.practiceCount()).isEqualTo(2);
    }

    private static PracticeTrajectory calculate(Observation... observations) {
        return PracticeTrajectoryCalculator.calculate(Map.of("testing", List.of(observations))).get("testing");
    }

    private static Observation observation(Assessment assessment, String observedAt, Long artifactId) {
        return Observation.builder()
            .id(UUID.randomUUID())
            .artifactType(WorkArtifact.PULL_REQUEST)
            .artifactId(artifactId)
            .assessment(assessment)
            .observedAt(Instant.parse(observedAt))
            .build();
    }

    private static PracticeTrajectory trajectory(String slug, AreaTrajectory direction, double delta) {
        return new PracticeTrajectory(
            slug,
            direction,
            delta,
            2,
            2,
            java.time.LocalDate.parse("2026-08-05"),
            java.time.LocalDate.parse("2026-08-04")
        );
    }
}
