package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyStage;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PracticeFeedbackDeliveryPolicyTest extends BaseUnitTest {

    @Test
    void approvedFeedbackKeepsHumanAuthorizationAtPhysicalEgress() {
        assertThat(
            PracticeFeedbackDeliveryPolicy.requiredAutonomy(DeliveryPolicyStage.EGRESS, UUID.randomUUID())
        ).isEqualTo(PracticeAutonomy.HUMAN_APPROVAL);
    }

    @Test
    void automaticFeedbackRequiresAutomaticAuthorityAtPhysicalEgress() {
        assertThat(PracticeFeedbackDeliveryPolicy.requiredAutonomy(DeliveryPolicyStage.EGRESS, null)).isEqualTo(
            PracticeAutonomy.AUTOMATIC
        );
    }
}
