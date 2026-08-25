package de.tum.cit.aet.hephaestus.integration.core.conformance;

import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import java.util.List;

/**
 * The synthetic integration through the same suite as the shipped ones.
 *
 * <p>If this subclass ever needs an override the others do not, the suite has stopped being a contract
 * and started being a description of GitHub.
 */
class FixtureManifestContractTest extends IntegrationManifestContractTest {

    @Override
    protected IntegrationManifest manifest() {
        return FixtureIntegration.manifest();
    }

    @Override
    protected List<ArtifactDescriptor> descriptors() {
        return List.of(FixtureIntegration.descriptor());
    }
}
