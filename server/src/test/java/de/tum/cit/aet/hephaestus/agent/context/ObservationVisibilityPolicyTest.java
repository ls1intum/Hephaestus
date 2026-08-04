package de.tum.cit.aet.hephaestus.agent.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.SourceUseAudience;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObservationVisibilityPolicyTest extends BaseUnitTest {

    @Test
    void permitsCurrentAuthorizedObservation() {
        EvidenceDeliveryAuthorization authorization = mock(EvidenceDeliveryAuthorization.class);
        Observation observation = observation("fingerprint", "fingerprint");
        when(authorization.permits(7L, observation, SourceUseAudience.PRACTICE_MENTORING)).thenReturn(true);

        assertThat(
            new ObservationVisibilityPolicy(authorization).permits(
                7L,
                observation,
                SourceUseAudience.PRACTICE_MENTORING
            )
        ).isTrue();
    }

    @Test
    void rejectsCurrentObservationWhenAudienceAuthorizationIsWithdrawn() {
        EvidenceDeliveryAuthorization authorization = mock(EvidenceDeliveryAuthorization.class);
        Observation observation = observation("fingerprint", "fingerprint");
        when(authorization.permits(7L, observation, SourceUseAudience.PRACTICE_FEEDBACK_RECIPIENTS)).thenReturn(false);

        assertThat(
            new ObservationVisibilityPolicy(authorization).permits(
                7L,
                observation,
                SourceUseAudience.PRACTICE_FEEDBACK_RECIPIENTS
            )
        ).isFalse();
        verify(authorization).permits(7L, observation, SourceUseAudience.PRACTICE_FEEDBACK_RECIPIENTS);
    }

    @Test
    void rejectsStaleObservationWithoutCheckingSourceAuthorization() {
        EvidenceDeliveryAuthorization authorization = mock(EvidenceDeliveryAuthorization.class);

        assertThat(
            new ObservationVisibilityPolicy(authorization).permits(
                7L,
                observation("old", "current"),
                SourceUseAudience.PRACTICE_FEEDBACK_RECIPIENTS
            )
        ).isFalse();
        verifyNoInteractions(authorization);
    }

    private static Observation observation(String evaluatedFingerprint, String currentFingerprint) {
        PracticeRevision evaluated = mock(PracticeRevision.class);
        PracticeRevision current = mock(PracticeRevision.class);
        Practice practice = mock(Practice.class);
        when(evaluated.getDetectionFingerprint()).thenReturn(evaluatedFingerprint);
        when(current.getDetectionFingerprint()).thenReturn(currentFingerprint);
        when(practice.getCurrentRevision()).thenReturn(current);
        return Observation.builder()
            .agentJobId(UUID.randomUUID())
            .practice(practice)
            .practiceRevision(evaluated)
            .build();
    }
}
