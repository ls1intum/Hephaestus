package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayName("Delivery policy facts snapshot")
class DeliveryPolicyFactsSnapshotTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void storesExactlyTheComponentsHistoricalRowsWereWrittenWith() {
        assertThat(objectMapper.valueToTree(empty()).propertyNames()).containsExactlyInAnyOrder(
            "artifactKind",
            "repository",
            "baseBranch",
            "subject",
            "repositoryMode",
            "personMode",
            "repositoryMatched",
            "branchMatched",
            "personMatched",
            "recipientConsent",
            "deliveryStatus",
            "triggerMode",
            "contributingPractices"
        );
    }

    private static DeliveryPolicyFactsSnapshot empty() {
        return new DeliveryPolicyFactsSnapshot(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of()
        );
    }
}
