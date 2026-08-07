package de.tum.cit.aet.hephaestus.integration.core.conformance;

import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.outline.domain.signal.DocumentArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.outline.manifest.OutlineManifest;
import java.util.List;

/**
 * Outline through the shared acceptance suite.
 *
 * <p>It used to pass while contributing nothing, and the emptiness was the point: a complete webhook
 * stack, eleven ingested document events, no descriptor, no signal, nothing that could trigger a review.
 * The document descriptor has landed, so the suite now holds Outline to it — that every signal it claims
 * to raise is one the descriptor declares, that each is backed by an ingested event of Outline's own, and
 * that it observes the kind it speaks about. Not one line of the practices module changed to get here,
 * which is what this test is really evidence of.
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
