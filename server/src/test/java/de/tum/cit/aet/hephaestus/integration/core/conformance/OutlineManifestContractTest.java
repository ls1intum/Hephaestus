package de.tum.cit.aet.hephaestus.integration.core.conformance;

import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.outline.domain.signal.DocumentArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.outline.manifest.OutlineManifest;
import java.util.List;

/**
 * Constructed as enabled: the suite's rules are about wiring a disabled deployment lacks, not about
 * whether the bean is registered — it is, either way.
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
