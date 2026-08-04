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
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObservationVisibilityPolicyTest extends BaseUnitTest {

    @Test
    void permitsCurrentAuthorizedObservation() {
        EvidenceAuthorization authorization = mock(EvidenceAuthorization.class);
        Observation observation = observation("fingerprint", "fingerprint");
        when(authorization.permits(7L, observation, SourceUsePurpose.CONVERSATIONAL_MENTORING)).thenReturn(true);

        assertThat(
            new ObservationVisibilityPolicy(authorization).permits(
                7L,
                observation,
                SourceUsePurpose.CONVERSATIONAL_MENTORING
            )
        ).isTrue();
    }

    @Test
    void rejectsCurrentObservationWhenAudienceAuthorizationIsWithdrawn() {
        EvidenceAuthorization authorization = mock(EvidenceAuthorization.class);
        Observation observation = observation("fingerprint", "fingerprint");
        when(authorization.permits(7L, observation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY)).thenReturn(false);

        assertThat(
            new ObservationVisibilityPolicy(authorization).permits(
                7L,
                observation,
                SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY
            )
        ).isFalse();
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

    private static Observation observation(String evaluatedFingerprint, String currentFingerprint) {
        PracticeRevision evaluated = mock(PracticeRevision.class);
        PracticeRevision current = mock(PracticeRevision.class);
        Practice practice = mock(Practice.class);
        when(evaluated.getReviewRuleFingerprint()).thenReturn(evaluatedFingerprint);
        when(current.getReviewRuleFingerprint()).thenReturn(currentFingerprint);
        when(practice.getCurrentRevision()).thenReturn(current);
        return Observation.builder()
            .agentJobId(UUID.randomUUID())
            .practice(practice)
            .practiceRevision(evaluated)
            .build();
    }
}
