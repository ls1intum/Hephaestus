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
 * <p>Lives in test code because it names descriptor classes from outside the practices module, which that
 * module itself may not — the fixture exists to assert the production module reaches the same answer
 * without naming them.
 *
 * <p>Deliberately the real descriptors, not stubs: what an author may bind to is derived from the shipped
 * declarations, so invented options would stop testing what can actually break.
 */
public final class PracticeSignalOptionsFixture {

    private static final List<ArtifactDescriptor> DESCRIPTORS = List.of(
            new PullRequestArtifactDescriptor(),
            new IssueArtifactDescriptor(),
            new ConversationThreadArtifactDescriptor(),
            new DocumentArtifactDescriptor());

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
                return descriptors.stream()
                        .filter(descriptor -> descriptor.kind().equals(kind))
                        .findFirst();
            }
        };
    }
}
