package de.tum.cit.aet.hephaestus.integration.core.conformance;

import de.tum.cit.aet.hephaestus.integration.chat.item.ThreadItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.slack.manifest.SlackManifest;
import java.util.List;

/** Slack through the shared acceptance suite; it declares its observed thread kind. */
class SlackManifestContractTest extends IntegrationManifestContractTest {

    @Override
    protected IntegrationManifest manifest() {
        return new SlackManifest(true);
    }

    @Override
    protected List<ItemDescriptor> descriptors() {
        return List.of(new ThreadItemDescriptor());
    }
}
