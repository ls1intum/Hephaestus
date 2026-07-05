package de.tum.cit.aet.hephaestus.agent.documentation;

import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Agent-owned SPI: projects a workspace's mirrored Outline documents into the flat, agent-facing view the
 * documentation content source materialises into the sandbox context. Implemented by
 * {@code integration.outline} (the owner of the {@code outline_document} table); the agent content source
 * consumes it and never touches the Outline schema.
 *
 * <p>The implementation is the sole agent-facing reader of the mirror: it maps rows to {@link ProjectedDocument}
 * and represents a tombstoned (removed upstream) or size-cap-evicted document as a {@code deleted} / null-body
 * marker rather than dropping it, so a link to a vanished document still resolves to a placeholder. Both methods
 * are pure reads (extract/load only); no practice, verdict, or threshold logic lives on this seam.
 */
public interface DocumentProjection {
    /**
     * A bounded breadth of the workspace's mirrored documents — the corpus the mentor sees. Live documents are
     * surfaced ahead of tombstoned ones and the result is capped, so a large workspace never floods the context.
     *
     * @param workspaceId the workspace to scope the read to
     */
    List<ProjectedDocument> documentsForWorkspace(long workspaceId);

    /**
     * The workspace's mirrored documents matching a set of references — the linked-document lookup for the review
     * path. Each reference may be an Outline document id or a document URL; a tombstoned/evicted match is still
     * returned (as a {@code deleted} / null-body marker) so a stale link resolves rather than silently vanishing.
     *
     * @param workspaceId          the workspace to scope the read to
     * @param outlineDocIdsOrUrls   the Outline document ids or URLs referenced from the artifact under review
     */
    List<ProjectedDocument> documentsByReference(long workspaceId, Collection<String> outlineDocIdsOrUrls);

    /**
     * The agent-facing view of one mirrored Outline document. {@code bodyMarkdown} is {@code null} when the
     * document was removed upstream ({@code deleted}) or its body was evicted under the size cap.
     */
    record ProjectedDocument(
        String collectionSlug,
        String slug,
        String title,
        @Nullable String bodyMarkdown,
        boolean deleted
    ) {}
}
