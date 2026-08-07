package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.agent.conversation.ConversationThreadArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactCatalog;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.outline.domain.signal.DocumentArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.IssueArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.PullRequestArtifactDescriptor;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The signal options a running server offers, assembled from the real shipped descriptors.
 *
 * <p>Names four vendor-side descriptor classes, which the practices module itself may not do. That is
 * the point of it being test code: the boundary rule is checked over production classes, and this
 * fixture exists precisely to assert that the production module reaches the same answer without ever
 * naming them.
 *
 * <p>Deliberately the real ones rather than stubs: what an author may bind to is derived from the
 * shipped declarations, so a unit test built on invented options would stop testing the thing that can
 * actually break.
 */
public final class PracticeSignalOptionsFixture {

    private static final List<ArtifactDescriptor> DESCRIPTORS = List.of(
        new PullRequestArtifactDescriptor(),
        new IssueArtifactDescriptor(),
        new ConversationThreadArtifactDescriptor(),
        new DocumentArtifactDescriptor()
    );

    private PracticeSignalOptionsFixture() {}

    public static PracticeSignalOptions real() {
        return new PracticeSignalOptions(catalog());
    }

    public static PracticeSignalOptions with(ArtifactDescriptor... descriptors) {
        return new PracticeSignalOptions(catalog(List.of(descriptors)));
    }

    public static ArtifactCatalog catalog() {
        return catalog(DESCRIPTORS);
    }

    public static ArtifactCatalog catalog(List<ArtifactDescriptor> descriptors) {
        return new ArtifactCatalog() {
            @Override
            public Collection<ArtifactDescriptor> all() {
                return descriptors;
            }

            @Override
            public Optional<ArtifactDescriptor> descriptorFor(ArtifactKind kind) {
                return descriptors
                    .stream()
                    .filter(descriptor -> descriptor.kind().equals(kind))
                    .findFirst();
            }
        };
    }
}
