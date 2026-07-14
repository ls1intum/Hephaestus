package de.tum.cit.aet.hephaestus.agent.documentation;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Agent-owned SPI projecting a workspace's mirrored documentation into the agent-facing view. Implemented by the
 * integration module that owns the schema, so the agent never touches it. A tombstoned or size-cap-evicted document
 * is returned as a {@code deleted} / null-body marker rather than dropped, so a link to a vanished document still
 * resolves. Pure reads: no practice, verdict, or threshold logic lives on this seam.
 */
public interface DocumentProjection {
    /** A bounded breadth of the workspace's mirrored documents — live ahead of tombstoned, capped so a large workspace never floods the context. */
    List<ProjectedDocument> documentsForWorkspace(long workspaceId);

    /**
     * The linked-document lookup. Each reference may be a document id or URL; a tombstoned/evicted match is still
     * returned as a marker so a stale link resolves rather than silently vanishing.
     */
    List<ProjectedDocument> documentsByReference(long workspaceId, Collection<String> documentRefs);

    /**
     * The workspace's live mirrored documents ranked by full-text relevance to {@code queryText} — the
     * retrieval path that surfaces relevant-but-unlinked documentation. Tombstoned/evicted documents are
     * excluded (there is no body to rank against); a blank query or no match yields an empty list, the
     * caller's cue to fall back to {@link #documentsForWorkspace}.
     *
     * @param workspaceId the workspace to scope the read to
     * @param queryText   free text describing what is relevant (websearch syntax; {@code OR}-joined terms)
     * @param limit       maximum number of documents to return
     */
    List<ProjectedDocument> searchDocuments(long workspaceId, String queryText, int limit);

    /**
     * Pulls documentation references — ids, slugs, links — out of free text (an artifact body). What counts
     * as a reference (link grammar, token derivation) is the implementation's vendor knowledge; the consumer
     * stays vendor-blind and feeds the result straight into {@link #documentsByReference}. Bounded and
     * insertion-ordered; a {@code null}/blank input yields an empty set.
     *
     * @param text the free text to scan; may be {@code null}
     */
    Set<String> extractReferences(@Nullable String text);

    /**
     * The agent-facing view of one mirrored document. {@code bodyMarkdown} is {@code null} when the
     * document was removed upstream ({@code deleted}) or its body was evicted under the size cap.
     *
     * <p><strong>Timestamps.</strong> {@code createdAt}/{@code updatedAt} are the upstream document clocks
     * — the up-to-dateness signal a reader needs to weigh a doc's authority; {@code null} when the mirror
     * has not captured them.
     *
     * <p><strong>Authorship.</strong> {@code createdBy*}/{@code updatedBy*} carry the upstream author
     * substrate: the display name (untrusted third-party text — it must ride inside quarantined content,
     * never as trusted metadata) and the provider-native subject. The {@code *MemberId} fields are the
     * workspace member the subject resolves to through the linked-account chain — resolved lazily at
     * projection time, {@code null} when the author has not linked an identity (graceful floor: the display
     * name still renders). {@code collaborators} lists everyone who edited the document (the middle editors
     * the creator/last-editor pair misses); a collaborator's name is only known when they are also the
     * creator or last editor, so entries may carry subject + member id only.
     *
     * <p>{@code archived} is Outline's soft, recoverable "archived in the wiki" state — distinct from
     * {@code deleted}: an archived document keeps its {@code bodyMarkdown} (unlike a tombstone, which nulls
     * it), so a renderer should surface both facts distinctly rather than treating archived as another
     * flavor of gone. {@code collectionName} is the collection's human-facing display name — as opposed to
     * {@link #collectionSlug}, which stays the stable path/directory identity — {@code null} when the
     * mirror has not captured a name for the collection.
     */
    record ProjectedDocument(
        String collectionSlug,
        String slug,
        String title,
        @Nullable String bodyMarkdown,
        boolean deleted,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt,
        @Nullable String createdByName,
        @Nullable String createdBySubject,
        @Nullable Long createdByMemberId,
        @Nullable String updatedByName,
        @Nullable String updatedBySubject,
        @Nullable Long updatedByMemberId,
        List<Collaborator> collaborators,
        boolean archived,
        @Nullable String collectionName
    ) {
        /** One document editor: provider-native subject, display name if known, resolved member id if linked. */
        public record Collaborator(String subject, @Nullable String name, @Nullable Long memberId) {}

        /**
         * An author-less projection (no substrate captured / tombstoned) — every author field {@code null},
         * no collaborators, no timestamps, not archived, no collection name.
         */
        public static ProjectedDocument withoutAuthors(
            String collectionSlug,
            String slug,
            String title,
            @Nullable String bodyMarkdown,
            boolean deleted
        ) {
            return new ProjectedDocument(
                collectionSlug,
                slug,
                title,
                bodyMarkdown,
                deleted,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                false,
                null
            );
        }
    }
}
