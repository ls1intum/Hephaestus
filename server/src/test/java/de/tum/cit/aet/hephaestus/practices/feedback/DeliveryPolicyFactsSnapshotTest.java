package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * The snapshot is written into a JSON column and read back through the same record, so a renamed
 * component silently reinterprets every row already stored. This pins the stored names.
 */
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

    @Test
    void survivesARoundTripThroughTheColumn() {
        DeliveryPolicyFactsSnapshot written = empty();

        assertThat(
            objectMapper.treeToValue(objectMapper.valueToTree(written), DeliveryPolicyFactsSnapshot.class)
        ).isEqualTo(written);
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
