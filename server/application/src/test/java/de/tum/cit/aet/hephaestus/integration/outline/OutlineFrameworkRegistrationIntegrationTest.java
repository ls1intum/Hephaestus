package de.tum.cit.aet.hephaestus.integration.outline;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationLifecycleListener;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * With the integration enabled, Outline becomes a registered kind — which means
 * {@code IntegrationFrameworkBootstrap} demands an {@link ApiCredentialProvider} and an
 * {@link IntegrationLifecycleListener} for it or the context fails to start. This test booting at all
 * is the proof those beans land together; the assertions pin the registration.
 */
@TestPropertySource(properties = "hephaestus.integration.outline.enabled=true")
class OutlineFrameworkRegistrationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private List<IntegrationManifest> manifests;

    @Autowired
    private List<ApiCredentialProvider> credentialProviders;

    @Autowired
    private List<IntegrationLifecycleListener> lifecycleListeners;

    @Test
    void outlineIsRegisteredWithTheRequiredFrameworkBeans() {
        assertThat(manifests).anyMatch(m -> m.kind() == IntegrationKind.OUTLINE);
        assertThat(credentialProviders).anyMatch(p -> p.kind() == IntegrationKind.OUTLINE);
        assertThat(lifecycleListeners).anyMatch(l -> l.kind() == IntegrationKind.OUTLINE);
    }
}
