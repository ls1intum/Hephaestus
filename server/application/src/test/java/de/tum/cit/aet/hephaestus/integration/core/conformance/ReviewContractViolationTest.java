package de.tum.cit.aet.hephaestus.integration.core.conformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.framework.ItemDescriptorRegistry;
import de.tum.cit.aet.hephaestus.integration.core.framework.ReviewContractValidator;
import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandlerRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyCapability;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReviewContractViolationTest extends BaseUnitTest {
    @Test
    void shouldRejectDuplicateKindsAtStartup() {
        assertThatThrownBy(() -> new ItemDescriptorRegistry(
                        List.of(FixtureIntegration.descriptor(), FixtureIntegration.descriptor())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRefuseUndeclaredEvents() {
        var contribution = new IntegrationManifest.ReviewContribution(
                Set.of(FixtureIntegration.WIDGET),
                Map.of(FixtureIntegration.WIDGET, Set.of(new ItemEventName("fixture.widget.recalled"))),
                Map.of());
        assertThat(validator().validateContribution(FixtureIntegration.manifest(Set.of(), contribution)))
                .anyMatch(v -> v.contains("Undeclared item event"));
    }

    @Test
    void shouldRefuseMissingProvenanceHandler() {
        assertThat(validator().validateContribution(FixtureIntegration.manifest()))
                .anyMatch(v -> v.contains("Missing event handler"));
    }

    @Test
    void shouldRefuseProviderClaimOfLocalDelivery() {
        var contribution = new IntegrationManifest.ReviewContribution(
                Set.of(FixtureIntegration.WIDGET),
                Map.of(),
                Map.of(FixtureIntegration.WIDGET, Set.of(CopyCapability.PRACTICE_PAGE)));
        assertThat(validator().validateContribution(FixtureIntegration.manifest(Set.of(), contribution)))
                .anyMatch(v -> v.contains("local delivery"));
    }

    private ReviewContractValidator validator() {
        return new ReviewContractValidator(
                new ItemDescriptorRegistry(List.of(FixtureIntegration.descriptor())),
                new IntegrationMessageHandlerRegistry(List.of()));
    }
}
