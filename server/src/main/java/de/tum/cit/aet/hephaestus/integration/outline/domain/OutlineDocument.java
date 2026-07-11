package de.tum.cit.aet.hephaestus.integration.outline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
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
 * removed upstream) and a size-cap eviction ({@code bodyEvictedAt} set; the row survives as a
 * directory marker pointing at the document URL). {@code contentHash} + {@code outlineUpdatedAt}
 * drive incremental sync so an unchanged document is never re-exported — an evicted row KEEPS its
 * hash, so the body comes back only when upstream actually changes or a targeted refresh asks for it,
 * never in a cap-evict-re-export thrash loop.
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

    /**
     * Optimistic-lock guard. A webhook-triggered {@code refreshDocument} and a mid-flight full reconcile's
     * per-document upsert both run in their own {@code REQUIRES_NEW} transaction with nothing serializing
     * them per-workspace, so they CAN race the SAME row (e.g. a document edited right as the 6h reconcile
     * reaches its collection). Without a version column, both writers do a full-column save and the loser's
     * commit silently clobbers the winner's — a lost update. {@code OutlineDocumentSyncService} retries a
     * losing write once against the row's current version before giving up to the next reconcile.
     */
    @Version
    @ColumnDefault("0")
    @Column(name = "version", nullable = false)
    private Long version;

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

    /** Upstream {@code createdAt} — the document-age half of the up-to-dateness signal. */
    @Column(name = "outline_created_at")
    private @Nullable Instant outlineCreatedAt;

    /**
     * Outline user ids (UUIDs) of everyone who edited the document ({@code Document.collaboratorIds})
     * — the middle editors the creator/last-editor pair misses. Same lazy-join, PII posture as
     * {@link #createdBySubject}; {@code null} (never an empty array) when upstream reports none.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "collaborator_subjects", columnDefinition = "jsonb")
    private @Nullable List<String> collaboratorSubjects;

    /**
     * Outline user id (UUID) of the document's creator — authorship substrate. Subjects join
     * {@code identity_link} lazily at projection time; no member id is stamped here.
     *
     * <p><strong>Account-erasure posture.</strong> The author subject/name columns are integration-mirror
     * data, NOT Hephaestus account PII: they are captured from Outline's own {@code Document.createdBy}
     * and are on the same GDPR Art. 17(3) footing as the SCM activity mirror ({@code
     * integration.scm.domain.user.User}) that {@code AccountHardDeleteSweeper} documents. An account
     * hard-delete severs the personal ↔ mirror association by deleting {@code identity_link} (so the
     * Outline subject can no longer be joined back to the erased account) rather than scrubbing every
     * workspace's mirror — the same posture Slack's ingested content follows (its person-erasure fires on
     * Slack-side consent revocation / opt-out, not on account hard-delete). Full erasure of these columns
     * happens with the workspace/connection purge and on tombstone. Adding an Outline-only account-erasure
     * hook would diverge from that established repo-wide posture, so it is deliberately not done here.
     */
    @Column(name = "created_by_subject", length = 64)
    private @Nullable String createdBySubject;

    /** Creator display name (graceful floor when the subject has no linked account). */
    @Column(name = "created_by_name", length = 255)
    private @Nullable String createdByName;

    /** Outline user id (UUID) of the last editor — authorship substrate, same lazy-join contract. */
    @Column(name = "updated_by_subject", length = 64)
    private @Nullable String updatedBySubject;

    /** Last editor display name. */
    @Column(name = "updated_by_name", length = 255)
    private @Nullable String updatedByName;

    /** Set when the document no longer exists upstream; the body is dropped and a placeholder is served. */
    @Column(name = "deleted_at")
    private @Nullable Instant deletedAt;

    /**
     * Set when the document is archived in Outline — soft and recoverable, unlike {@link #deletedAt}: the
     * body, hash, and author/collaborator fields are all KEPT. Cleared the moment the document is seen live
     * again (a normal {@code documents.list}/{@code collections.documents} enumeration, both of which exclude
     * archived documents by default, or an {@code documents.unarchive} refresh).
     */
    @Column(name = "archived_at")
    private @Nullable Instant archivedAt;

    /** When the document body was last exported from Outline into the mirror; drives least-recently-exported eviction. */
    @Column(name = "last_materialized_at")
    private @Nullable Instant lastMaterializedAt;

    /**
     * Set when the size cap evicted the body ({@code bodyMarkdown} nulled, {@code contentHash} kept),
     * cleared when a sync re-exports the body. Distinguishes "evicted" from "tombstoned" explicitly.
     */
    @Column(name = "body_evicted_at")
    private @Nullable Instant bodyEvictedAt;

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

    /** Whether the document is archived in Outline (soft, recoverable — content is still present). */
    public boolean isArchived() {
        return archivedAt != null;
    }
}
