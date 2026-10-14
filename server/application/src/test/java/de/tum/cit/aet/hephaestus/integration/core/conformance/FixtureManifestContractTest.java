package de.tum.cit.aet.hephaestus.integration.core.conformance;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import java.util.List;

// This fixture must pass the shared suite without special overrides.
class FixtureManifestContractTest extends IntegrationManifestContractTest {

    @Override
    protected IntegrationManifest manifest() {
        return FixtureIntegration.manifest();
    }

    @Override
    protected List<ItemDescriptor> descriptors() {
        return List.of(FixtureIntegration.descriptor());
    }
}
