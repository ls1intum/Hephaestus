package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The presence/assessment coupling, held over the whole enum rather than over the values that existed
 * when it was written.
 *
 * <p>Adding {@code INDETERMINATE} is exactly the change that breaks a test enumerating three values by
 * hand: the new value would simply not be covered, and an INDETERMINATE row carrying a GOOD assessment —
 * the precise failure the value exists to prevent — would slip through with every test still green.
 */
class ObservationCoherenceTest extends BaseUnitTest {

    private static Observation.ObservationBuilder observation(Presence presence, Assessment assessment) {
        return Observation.builder()
            .occurrenceKey("occurrence")
            .agentJobId(UUID.randomUUID())
            .artifactKind(ArtifactKinds.PULL_REQUEST)
            .artifactId(1L)
            .aboutUserId(2L)
            .title("title")
            .presence(presence)
            .assessment(assessment)
            .confidence(0.9f);
    }

    private static void persist(Observation observation) {
        observation.onCreate();
    }

    @Test
    void onlyPresentAndAbsentCarryADirection() {
        assertThat(EnumSet.allOf(Presence.class).stream().filter(Presence::carriesValence)).containsExactlyInAnyOrder(
            Presence.PRESENT,
            Presence.ABSENT
        );
    }

    @ParameterizedTest
    @EnumSource(Presence.class)
    void assessmentIsRequiredExactlyWhenThePresenceCarriesADirection(Presence presence) {
        if (presence.carriesValence()) {
            assertThatCode(() -> persist(observation(presence, Assessment.GOOD).build())).doesNotThrowAnyException();
            assertThatThrownBy(() -> persist(observation(presence, null).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("coherence violation");
        } else {
            assertThatCode(() -> persist(observation(presence, null).build())).doesNotThrowAnyException();
            assertThatThrownBy(() -> persist(observation(presence, Assessment.GOOD).build()))
                .as("a presence with no direction must not be able to smuggle one out")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("coherence violation");
        }
    }

    /**
     * INDETERMINATE is a measurement about the world; a source that could not be read is not, and is
     * refused before any observation is written. If a fifth {@link Presence} ever appears that means "we
     * could not look", this test is the one that should stop it.
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
