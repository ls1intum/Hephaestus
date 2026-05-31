package de.tum.cit.aet.hephaestus.integration.core.framework;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationLifecycleListener;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IntegrationFrameworkBootstrapTest extends BaseUnitTest {

    @Test
    void manifestWithNoCapabilitiesValidatesCleanly() {
        // Regression: an outbound-only kind (e.g. Slack) declares zero capabilities. The universal
        // credential-provider + lifecycle-listener beans are still required, but the per-capability
        // checks must be a no-op — and must not trip EnumSet.copyOf on the empty declared set.
        IntegrationManifestRegistry registry = new IntegrationManifestRegistry(
            List.of(manifest(IntegrationKind.SLACK, Set.of()))
        );
        IntegrationFrameworkBootstrap bootstrap = bootstrap(
            registry,
            List.of(credentialProvider(IntegrationKind.SLACK)),
            List.of(lifecycleListener(IntegrationKind.SLACK))
        );

        assertThatCode(bootstrap::validate).doesNotThrowAnyException();
    }

    @Test
    void declaredCapabilityWithoutItsBeanFailsLoud() {
        IntegrationManifestRegistry registry = new IntegrationManifestRegistry(
            List.of(manifest(IntegrationKind.GITHUB, Set.of(Capability.WEBHOOK_INGEST)))
        );
        // Universal beans present, but no WebhookSignatureVerifier backing the WEBHOOK_INGEST claim.
        IntegrationFrameworkBootstrap bootstrap = bootstrap(
            registry,
            List.of(credentialProvider(IntegrationKind.GITHUB)),
            List.of(lifecycleListener(IntegrationKind.GITHUB))
        );

        assertThatThrownBy(bootstrap::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("WebhookSignatureVerifier");
    }

    private static IntegrationFrameworkBootstrap bootstrap(
        IntegrationManifestRegistry registry,
        List<ApiCredentialProvider> credentialProviders,
        List<IntegrationLifecycleListener> lifecycleListeners
    ) {
        return new IntegrationFrameworkBootstrap(
            registry,
            List.of(), // signatureVerifiers
            List.of(), // secretSources
            List.of(), // subjectKeyDerivers
            List.of(), // subjectParsers
            credentialProviders,
            List.of(), // tokenRefreshers
            List.of(), // feedbackChannels
            List.of(), // inlineFindingChannels
            List.of(), // approvalChannels
            lifecycleListeners
        );
    }

    private static IntegrationManifest manifest(IntegrationKind kind, Set<Capability> capabilities) {
        return new IntegrationManifest() {
            @Override
            public IntegrationKind kind() {
                return kind;
            }

            @Override
            public String displayName() {
                return kind.name();
            }

            @Override
            public Set<Capability> declaredCapabilities() {
                return capabilities;
            }
        };
    }

    private static ApiCredentialProvider credentialProvider(IntegrationKind kind) {
        return new ApiCredentialProvider() {
            @Override
            public IntegrationKind kind() {
                return kind;
            }

            @Override
            public Optional<CredentialBundle> resolve(IntegrationRef ref) {
                return Optional.empty();
            }
        };
    }

    private static IntegrationLifecycleListener lifecycleListener(IntegrationKind kind) {
        return () -> kind;
    }
}
