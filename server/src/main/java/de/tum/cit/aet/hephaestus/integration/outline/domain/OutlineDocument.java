package de.tum.cit.aet.hephaestus.integration.outline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

/**
 * A local copy of one Outline document, mirrored from an allow-listed collection.
 *
 * <p>Workspace-scoped (scalar {@code workspaceId}) and tied to the {@code connectionId} of the
 * Outline install it came from, so a workspace can mirror more than one Outline instance without
 * document-id collisions. The mirror is the source of truth the agent reads from — a sandbox run
 * never calls Outline directly.
 *
 * <p>{@code bodyMarkdown} carries the rendered Markdown body. It is {@code null} in two states that
 * both render as a placeholder rather than a missing file: {@code deletedAt} set (the document was
 * removed upstream) and a size-cap eviction (the row survives as a directory marker pointing at the
 * document URL). {@code contentHash} + {@code outlineUpdatedAt} drive incremental sync so an
 * unchanged document is never re-exported.
 */
@Entity
@Table(
    name = "outline_document",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_outline_document",
        columnNames = { "workspace_id", "connection_id", "document_id" }
    )
)
@Getter
@Setter
@NoArgsConstructor
public class OutlineDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    /** Outline document id (UUID). */
    @Column(name = "document_id", nullable = false, length = 64)
    private String documentId;

    /** Outline collection id (UUID) the document belongs to. */
    @Column(name = "collection_id", nullable = false, length = 64)
    private String collectionId;

    /** Collection slug, used to lay the document out under a stable directory. */
    @Column(name = "collection_slug", length = 512)
    private @Nullable String collectionSlug;

    /** Parent document id (UUID) for a nested document, or {@code null} at the collection root. */
    @Column(name = "parent_document_id", length = 64)
    private @Nullable String parentDocumentId;

    @Column(name = "title", length = 1024)
    private @Nullable String title;

    /** Document slug, used as the materialized file name. */
    @Column(name = "slug", length = 512)
    private @Nullable String slug;

    @Column(name = "body_markdown", columnDefinition = "text")
    private @Nullable String bodyMarkdown;

    /** Hash of the exported body; lets a sync skip re-exporting an unchanged document. */
    @Column(name = "content_hash", length = 64)
    private @Nullable String contentHash;

    /** Upstream {@code updatedAt}; the incremental cursor a sync diffs against. */
    @Column(name = "outline_updated_at")
    private @Nullable Instant outlineUpdatedAt;

    /** Set when the document no longer exists upstream; the body is dropped and a placeholder is served. */
    @Column(name = "deleted_at")
    private @Nullable Instant deletedAt;

    /** When the document body was last exported from Outline into the mirror; drives least-recently-exported eviction. */
    @Column(name = "last_materialized_at")
    private @Nullable Instant lastMaterializedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Whether the document has been tombstoned (removed upstream). */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
