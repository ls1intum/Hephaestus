package de.tum.cit.aet.hephaestus.integration.outline.documentation;

import de.tum.cit.aet.hephaestus.agent.documentation.DocumentProjection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outline-owned implementation of the agent {@link DocumentProjection} SPI: projects the mirrored
 * {@code outline_document} rows into the flat, agent-facing view the documentation content source materialises.
 * This is the sole agent-facing reader of the mirror — the agent consumes the projected payload through the SPI,
 * never the schema, so a column rename in {@code outline_document} is a compile-and-SQL change <em>inside
 * Outline</em>, not a silent runtime break in the agent.
 *
 * <p><strong>Tombstones and evictions.</strong> A document removed upstream ({@code deleted_at} set) or whose body
 * was evicted under the size cap comes back as a marker ({@code deleted = true} / {@code bodyMarkdown = null})
 * rather than being dropped, so a link to a vanished document still resolves to a placeholder. Reads are pure: this
 * projector never mutates {@code last_materialized_at} (that write belongs to the content source that actually
 * materialises a body into a sandbox run), which the {@code readOnly} transaction enforces.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
@Transactional(readOnly = true)
public class OutlineDocumentProjector implements DocumentProjection {

    /** Cap on documents surfaced to the mentor per turn — the corpus-breadth envelope. */
    static final int MAX_DOCUMENTS = 200;

    private final OutlineDocumentRepository documentRepository;

    public OutlineDocumentProjector(OutlineDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public List<ProjectedDocument> documentsForWorkspace(long workspaceId) {
        return documentRepository
            .findForProjection(workspaceId, PageRequest.of(0, MAX_DOCUMENTS))
            .stream()
            .map(OutlineDocumentProjector::project)
            .toList();
    }

    @Override
    public List<ProjectedDocument> documentsByReference(long workspaceId, Collection<String> outlineDocIdsOrUrls) {
        if (outlineDocIdsOrUrls == null || outlineDocIdsOrUrls.isEmpty()) {
            return List.of();
        }
        // A reference is either a raw Outline document id or a document URL; match on the id and slug columns,
        // adding the last URL path segment as a candidate token so a link resolves to the mirrored row.
        Set<String> tokens = new LinkedHashSet<>();
        for (String ref : outlineDocIdsOrUrls) {
            if (ref == null || ref.isBlank()) {
                continue;
            }
            String trimmed = ref.trim();
            tokens.add(trimmed);
            int lastSlash = trimmed.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < trimmed.length() - 1) {
                tokens.add(trimmed.substring(lastSlash + 1));
            }
        }
        if (tokens.isEmpty()) {
            return List.of();
        }
        return documentRepository
            .findByWorkspaceIdAndReferenceIn(workspaceId, tokens)
            .stream()
            .map(OutlineDocumentProjector::project)
            .toList();
    }

    /** Maps a mirrored row to the agent view; a tombstoned/evicted document serves a null body. */
    private static ProjectedDocument project(OutlineDocument doc) {
        boolean deleted = doc.isDeleted();
        String body = deleted ? null : doc.getBodyMarkdown();
        return new ProjectedDocument(
            orEmpty(doc.getCollectionSlug()),
            orEmpty(doc.getSlug()),
            orEmpty(doc.getTitle()),
            body,
            deleted
        );
    }

    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
