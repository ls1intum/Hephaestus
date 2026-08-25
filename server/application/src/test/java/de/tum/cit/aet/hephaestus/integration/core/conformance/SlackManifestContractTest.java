package de.tum.cit.aet.hephaestus.integration.core.conformance;

import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.slack.manifest.SlackManifest;
import java.util.List;

/** Slack through the shared acceptance suite; it contributes nothing and says so. */
class SlackManifestContractTest extends IntegrationManifestContractTest {

    @Override
    protected IntegrationManifest manifest() {
        return new SlackManifest(true);
    }

    @Override
    protected List<ArtifactDescriptor> descriptors() {
        return List.of();
    }
}
