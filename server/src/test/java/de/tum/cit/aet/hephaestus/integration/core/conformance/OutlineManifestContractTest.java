package de.tum.cit.aet.hephaestus.integration.core.conformance;

import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.outline.manifest.OutlineManifest;
import java.util.List;

/**
 * Outline through the shared acceptance suite.
 *
 * <p>It passes while contributing nothing, which is correct and is exactly the state to keep visible:
 * thirteen ingested document events, no descriptor, no signal, nothing that can trigger a review. The
 * day a document descriptor lands, this class gains descriptors and the suite starts holding Outline to
 * them without a line changing in the practices module.
 */
class OutlineManifestContractTest extends IntegrationManifestContractTest {

    @Override
    protected IntegrationManifest manifest() {
        return new OutlineManifest();
    }

    @Override
    protected List<ArtifactDescriptor> descriptors() {
        return List.of();
    }
}
