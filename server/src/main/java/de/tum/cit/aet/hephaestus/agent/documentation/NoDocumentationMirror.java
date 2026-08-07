package de.tum.cit.aet.hephaestus.agent.documentation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The documentation projection for a deployment that mirrors no documentation.
 *
 * <p>Every method answers "nothing", which is the truth rather than a placeholder: with no
 * documentation integration switched on there are no mirrored documents, so a linked-document lookup
 * finds none and a document review finds no subject. The consumers already handle that — the review
 * context reports its subject unavailable with a reason, and the linked-document evidence is optional.
 *
 * <p><b>Why this exists.</b> {@link DocumentProjection} is agent-owned but implemented only by a vendor
 * module, and the one vendor that implements it is behind a master switch that is <em>off by default</em>.
 * Without this bean, the review context source that reads it could not be constructed, and the whole
 * application failed to start on any deployment that had not turned Outline on. The alternative — gating
 * the context source on the vendor flag — would take the {@code docs.document} descriptor's context
 * builder away with it, and the review contract refuses to start a build whose reviewable kind has none.
 * So the seam gets a floor instead: the kind stays defined and serviceable, and this deployment simply
 * has nothing to review.
 */
final class NoDocumentationMirror implements DocumentProjection {

    @Override
    public List<ProjectedDocument> documentsForWorkspace(long workspaceId) {
        return List.of();
    }

    @Override
    public List<ProjectedDocument> documentsByReference(long workspaceId, Collection<String> documentRefs) {
        return List.of();
    }

    @Override
    public Optional<ProjectedDocument> documentById(long workspaceId, long documentId) {
        return Optional.empty();
    }

    @Override
    public List<ProjectedDocument> searchDocuments(long workspaceId, String queryText, int limit) {
        return List.of();
    }

    /**
     * No references, rather than the link grammar of a vendor that is not installed. What counts as a
     * documentation reference is vendor knowledge; a deployment with no documentation vendor has none,
     * and returning matches nothing could resolve would only produce lookups that find nothing.
     */
    @Override
    public Set<String> extractReferences(@Nullable String text) {
        return Set.of();
    }
}
