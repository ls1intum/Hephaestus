package de.tum.cit.aet.hephaestus.integration.core.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.framework.ItemDescriptorRegistry;
import de.tum.cit.aet.hephaestus.integration.core.framework.ReviewContractValidator;
import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandlerRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyCapability;
import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventDefinition;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Subclasses supply manifests and descriptors; inherited assertions exercise {@link ReviewContractValidator} rather than duplicating its rules. */
abstract class IntegrationManifestContractTest extends BaseUnitTest {

    /** The manifest under test, constructed as production constructs it. */
    protected abstract IntegrationManifest manifest();

    /** Descriptors for every kind the manifest observes. Empty when it contributes nothing. */
    protected abstract List<ItemDescriptor> descriptors();

    @Test
    void identifiesItself() {
        IntegrationManifest manifest = manifest();
        assertThat(manifest.kind())
                .as("every manifest names the integration it describes")
                .isNotNull();
        assertThat(manifest.displayName())
                .as("display name is shown to operators")
                .isNotBlank();
        assertThat(manifest.declaredCapabilities())
                .as("capabilities may be empty but never null")
                .isNotNull();
        assertThat(manifest.reviewContribution())
                .as("a contribution of none() is a declaration; null is an omission")
                .isNotNull();
    }

    @Test
    void satisfiesTheReviewContractTheBootstrapEnforces() {
        List<String> violations = validator().validateContribution(manifest());

        assertThat(violations)
                .as("this manifest would fail boot validation on an instance that enables it")
                .isEmpty();
    }

    @Test
    void declaresDescriptorsThatSatisfyTheirOwnContract() {
        List<String> violations = validator().validateDescriptors();

        assertThat(violations)
                .as("the descriptors this manifest observes are themselves well-formed")
                .isEmpty();
    }

    @Test
    void spendsEveryDeliveryCapabilityItDeclares() {
        IntegrationManifest manifest = manifest();
        Set<CopyCapability> lanes = manifest.reviewContribution().delivers().values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toUnmodifiableSet());

        if (manifest.declaredCapabilities().contains(Capability.FEEDBACK_DELIVERY)) {
            assertThat(lanes)
                    .as("%s declares FEEDBACK_DELIVERY but names no item it would deliver" + " to", manifest.kind())
                    .containsAnyOf(CopyCapability.SUMMARY, CopyCapability.CONVERSATION);
        }
        if (manifest.declaredCapabilities().contains(Capability.INLINE_FEEDBACK)) {
            assertThat(lanes)
                    .as("%s declares INLINE_FEEDBACK but names no item it would anchor them" + " to", manifest.kind())
                    .contains(CopyCapability.LINE_NOTE);
        }
    }

    private ReviewContractValidator validator() {
        List<ItemDescriptor> descriptors = descriptors();
        Set<EventTypeKey> producers = new LinkedHashSet<>();
        for (ItemDescriptor descriptor : descriptors) {
            for (ItemEventDefinition event : descriptor.events()) {
                producers.addAll(event.producedBy());
            }
        }
        List<IntegrationMessageHandler> handlers =
                producers.stream().map(FixtureIntegration::handler).toList();
        return new ReviewContractValidator(
                new ItemDescriptorRegistry(descriptors), new IntegrationMessageHandlerRegistry(handlers));
    }
}
