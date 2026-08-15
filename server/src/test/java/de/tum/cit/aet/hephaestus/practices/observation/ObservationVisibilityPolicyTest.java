package de.tum.cit.aet.hephaestus.practices.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObservationVisibilityPolicyTest extends BaseUnitTest {

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void answersWhatEvidenceAuthorizationAnswersForACurrentObservation(boolean authorized) {
        EvidenceAuthorization authorization = mock(EvidenceAuthorization.class);
        Observation observation = observation("fingerprint", "fingerprint");
        when(authorization.permits(7L, observation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY)).thenReturn(
            authorized
        );

        assertThat(
            new ObservationVisibilityPolicy(authorization).permits(
                7L,
                observation,
                SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY
            )
        ).isEqualTo(authorized);
        verify(authorization).permits(7L, observation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
    }

    @Test
    void rejectsStaleObservationWithoutCheckingSourceAuthorization() {
        EvidenceAuthorization authorization = mock(EvidenceAuthorization.class);

        assertThat(
            new ObservationVisibilityPolicy(authorization).permits(
                7L,
                observation("old", "current"),
                SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY
            )
        ).isFalse();
        verifyNoInteractions(authorization);
    }

    /**
     * The batched form must keep the currentness conjunct ahead of the authorization one, exactly as the
     * per-row form does: a stale claim is refused without an evidence read, so it can never be admitted by
     * an authorization answer given about the batch it was in.
     */
    @Test
    void authorizesOnlyTheCurrentObservationsOfABatch() {
        EvidenceAuthorization authorization = mock(EvidenceAuthorization.class);
        Observation current = observation("fingerprint", "fingerprint");
        Observation stale = observation("old", "current");
        when(authorization.permitsAll(7L, List.of(current), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY)).thenReturn(
            Set.of(current.getId())
        );

        assertThat(
            new ObservationVisibilityPolicy(authorization).permitsAll(
                7L,
                List.of(current, stale),
                SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY
            )
        ).containsExactly(current.getId());
    }

    @Test
    void asksNothingOfEvidenceAuthorizationWhenEveryObservationIsStale() {
        EvidenceAuthorization authorization = mock(EvidenceAuthorization.class);

        assertThat(
            new ObservationVisibilityPolicy(authorization).permitsAll(
                7L,
                List.of(observation("old", "current")),
                SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY
            )
        ).isEmpty();
        verifyNoInteractions(authorization);
    }

    private static Observation observation(String evaluatedFingerprint, String currentFingerprint) {
        PracticeRevision evaluated = mock(PracticeRevision.class);
        PracticeRevision current = mock(PracticeRevision.class);
        Practice practice = mock(Practice.class);
        when(evaluated.getReviewRuleFingerprint()).thenReturn(evaluatedFingerprint);
        when(current.getReviewRuleFingerprint()).thenReturn(currentFingerprint);
        when(practice.getCurrentRevision()).thenReturn(current);
        return Observation.builder()
            .id(UUID.randomUUID())
            .agentJobId(UUID.randomUUID())
            .practice(practice)
            .practiceRevision(evaluated)
            .build();
    }
}
