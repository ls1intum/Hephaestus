package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactCatalog;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Holds {@link PracticeSignalOptionsFixture} to the descriptors a running server actually registers.
 *
 * <p>The fixture lists the shipped descriptors by hand, and the tests built on it — what an author may
 * bind to, which kinds carry a manual-request signal, which lanes exist — are only as wide as that list.
 * Registering a fifth descriptor without adding it here would leave every one of them asserting over a
 * four-kind world and passing, which is the shape of green that means nothing.
 */
class PracticeSignalOptionsFixtureIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ArtifactCatalog registeredCatalog;

    @Test
    void theFixtureOffersExactlyTheKindsTheContainerRegisters() {
        List<ArtifactKind> registered = registeredCatalog.all().stream().map(ArtifactDescriptor::kind).toList();

        assertThat(PracticeSignalOptionsFixture.catalog().all())
            .extracting(ArtifactDescriptor::kind)
            .containsExactlyInAnyOrderElementsOf(registered);
    }
}
