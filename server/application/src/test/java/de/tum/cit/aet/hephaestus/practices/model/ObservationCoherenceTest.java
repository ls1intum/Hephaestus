package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The presence/assessment coupling, held over the whole enum rather than over the values that existed
 * when it was written — a hand-enumerated list would silently stop covering a new value, letting an
 * {@code INCONCLUSIVE} row carrying a GOOD assessment slip through with the suite green.
 */
class ObservationCoherenceTest extends BaseUnitTest {

    private static Observation.ObservationBuilder observation(Presence presence, @Nullable Assessment assessment) {
        return Observation.builder()
                .occurrenceKey("occurrence")
                .agentJobId(UUID.randomUUID())
                .artifactKind(ArtifactKinds.PULL_REQUEST)
                .artifactId(1L)
                .aboutUserId(2L)
                .summary("title")
                .presence(presence)
                .assessment(assessment);
    }

    private static void persist(Observation observation) {
        observation.onCreate();
    }

    @Test
    void onlyPresentAndAbsentCarryADirection() {
        assertThat(EnumSet.allOf(Presence.class).stream().filter(Presence::carriesValence))
                .containsExactlyInAnyOrder(Presence.PRESENT, Presence.ABSENT);
    }

    @ParameterizedTest
    @EnumSource(Presence.class)
    void assessmentIsRequiredExactlyWhenThePresenceCarriesADirection(Presence presence) {
        if (presence.carriesValence()) {
            assertThatCode(() -> persist(observation(presence, Assessment.GOOD).build()))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> persist(observation(presence, null).build()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("coherence violation");
        } else {
            assertThatCode(() -> persist(observation(presence, null).build())).doesNotThrowAnyException();
            assertThatThrownBy(
                            () -> persist(observation(presence, Assessment.GOOD).build()))
                    .as("a presence with no direction must not be able to smuggle one out")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("coherence violation");
        }
    }

    /**
     * INCONCLUSIVE is a measurement about the world; a source that could not be read is not, and is
     * refused before any observation is written.
     */
    @Test
    void noPresenceValueDescribesTheInstrumentRatherThanTheWork() {
        Set<String> instrumentShaped = Set.of("NOT_ASSESSABLE", "UNAVAILABLE", "ERROR", "SKIPPED", "NOT_COLLECTED");
        assertThat(EnumSet.allOf(Presence.class))
                .as("coverage failures belong in the readiness record, never in the behaviour series")
                .noneMatch(presence -> instrumentShaped.contains(presence.name()));
    }

    @Test
    void anObservationDefaultsToTheLivePopulation() {
        Observation built = observation(Presence.PRESENT, Assessment.GOOD).build();
        persist(built);
        assertThat(built.getOrigin()).isEqualTo(ObservationOrigin.LIVE);
    }
}
