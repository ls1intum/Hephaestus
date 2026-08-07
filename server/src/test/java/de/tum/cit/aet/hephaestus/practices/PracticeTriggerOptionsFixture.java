package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactCatalog;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.SignalVocabulary;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.IssueArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.PullRequestArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignalVocabulary;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The trigger options a running server offers, assembled from the real SCM descriptors and vocabulary.
 *
 * <p>Deliberately the real ones rather than stubs: what an author may pick is now derived from the
 * shipped declarations, so a unit test built on invented options would stop testing the thing that can
 * actually break.
 */
public final class PracticeTriggerOptionsFixture {

    private static final List<ArtifactDescriptor> DESCRIPTORS = List.of(
        new PullRequestArtifactDescriptor(),
        new IssueArtifactDescriptor()
    );

    private PracticeTriggerOptionsFixture() {}

    public static PracticeTriggerOptions real() {
        return with(new ScmSignalVocabulary());
    }

    public static PracticeTriggerOptions with(SignalVocabulary... vocabularies) {
        return new PracticeTriggerOptions(catalog(), List.of(vocabularies));
    }

    public static ArtifactCatalog catalog() {
        return new ArtifactCatalog() {
            @Override
            public Collection<ArtifactDescriptor> all() {
                return DESCRIPTORS;
            }

            @Override
            public Optional<ArtifactDescriptor> descriptorFor(ArtifactKind kind) {
                return DESCRIPTORS.stream()
                    .filter(descriptor -> descriptor.kind().equals(kind))
                    .findFirst();
            }
        };
    }
}
