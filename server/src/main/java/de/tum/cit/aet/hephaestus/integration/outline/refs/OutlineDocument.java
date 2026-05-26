package de.tum.cit.aet.hephaestus.integration.outline.refs;

import de.tum.cit.aet.hephaestus.integration.connection.Connection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.lang.Nullable;

/**
 * Persisted Outline document metadata + most-recent-revision pointer.
 *
 * <p>Workspace-scoped through {@link Connection#getWorkspace()}. The
 * {@code lastRevisionId} powers ETag-style staleness checks during sync —
 * a document whose remote revision matches the stored one can be skipped.
 *
 * <p>Deletion-event contract:
 * <ul>
 *   <li>{@code documents.delete} → soft delete (preserves edit-in-place audit trail
 *       in {@code FeedbackPost.subject_external_id})</li>
 *   <li>{@code collections.delete} → soft delete all documents within the collection
 *       (separate handler — there is no DB-level cascade because {@code collection_id}
 *       is a string id, not an FK; see {@code OutlineLifecycleListener})</li>
 *   <li>{@code app_uninstalled}/{@code tokens_revoked} → physical cascade via
 *       {@link Connection} FK</li>
 * </ul>
 */
@Entity
@Table(
    name = "outline_document",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_outline_document",
        columnNames = { "connection_id", "document_id" }
    ),
    indexes = {
        @Index(name = "ix_outline_document_connection", columnList = "connection_id"),
        @Index(name = "ix_outline_document_collection", columnList = "connection_id, collection_id"),
    }
)
public class OutlineDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private Connection connection;

    @Column(name = "document_id", nullable = false, length = 64)
    private String documentId;

    @Column(name = "collection_id", length = 64)
    @Nullable
    private String collectionId;

    @Column(name = "title", length = 512)
    @Nullable
    private String title;

    @Column(name = "url", length = 1024)
    @Nullable
    private String url;

    @Column(name = "last_revision_id", length = 64)
    @Nullable
    private String lastRevisionId;

    @Column(name = "last_modified_at")
    @Nullable
    private Instant lastModifiedAt;

    @CreationTimestamp
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;

    @Column(name = "deleted_at")
    @Nullable
    private Instant deletedAt;

    protected OutlineDocument() {}

    public OutlineDocument(Connection connection, String documentId) {
        this.connection = connection;
        this.documentId = documentId;
    }

    public Long getId() {
        return id;
    }

    public Connection getConnection() {
        return connection;
    }

    public String getDocumentId() {
        return documentId;
    }

    @Nullable
    public String getCollectionId() {
        return collectionId;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getUrl() {
        return url;
    }

    @Nullable
    public String getLastRevisionId() {
        return lastRevisionId;
    }

    @Nullable
    public Instant getLastModifiedAt() {
        return lastModifiedAt;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }

    @Nullable
    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setCollectionId(@Nullable String collectionId) {
        this.collectionId = collectionId;
    }

    public void setTitle(@Nullable String title) {
        this.title = title;
    }

    public void setUrl(@Nullable String url) {
        this.url = url;
    }

    public void setLastRevisionId(@Nullable String lastRevisionId) {
        this.lastRevisionId = lastRevisionId;
    }

    public void setLastModifiedAt(@Nullable Instant lastModifiedAt) {
        this.lastModifiedAt = lastModifiedAt;
    }

    public void softDelete(Instant at) {
        if (this.deletedAt != null) {
            return;
        }
        this.deletedAt = at;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
