package de.tum.cit.aet.hephaestus.integration.core.conformance;

import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.outline.domain.signal.DocumentArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.outline.manifest.OutlineManifest;
import java.util.List;

/**
 * Outline through the shared acceptance suite.
 *
 * <p>Run against a manifest constructed as enabled. The bean is registered either way — what an
 * integration could raise is a fact about the build — but the rules the suite runs are about the wiring a
 * disabled deployment does not have.
 */
class OutlineManifestContractTest extends IntegrationManifestContractTest {

    @Override
    protected IntegrationManifest manifest() {
        return new OutlineManifest(true);
    }

    @Override
    protected List<ArtifactDescriptor> descriptors() {
        return List.of(new DocumentArtifactDescriptor());
    }
}
