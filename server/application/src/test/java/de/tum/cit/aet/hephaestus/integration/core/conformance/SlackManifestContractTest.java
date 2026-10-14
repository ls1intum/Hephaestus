package de.tum.cit.aet.hephaestus.integration.core.conformance;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.slack.manifest.SlackManifest;
import java.util.List;

/** Slack through the shared acceptance suite; it contributes nothing and says so. */
class SlackManifestContractTest extends IntegrationManifestContractTest {

    @Override
    protected IntegrationManifest manifest() {
        return new SlackManifest(true);
    }

    @Override
    protected List<ItemDescriptor> descriptors() {
        return List.of();
    }
}
