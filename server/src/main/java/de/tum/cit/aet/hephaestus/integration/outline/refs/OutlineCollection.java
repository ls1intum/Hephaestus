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
 * Persisted Outline collection — the document grouping each document belongs to.
 *
 * <p>Workspace-scoped through {@link Connection#getWorkspace()}. Identical
 * ToS/deletion-event contract to {@link OutlineDocument}: rows cascade on
 * {@link Connection} delete; an explicit {@code collections.delete} webhook
 * triggers physical removal via {@code OutlineLifecycleListener.onScopeChanged}.
 */
@Entity
@Table(
    name = "outline_collection",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_outline_collection",
        columnNames = {"connection_id", "collection_id"}
    ),
    indexes = {
        @Index(name = "ix_outline_collection_connection", columnList = "connection_id")
    }
)
public class OutlineCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private Connection connection;

    @Column(name = "collection_id", nullable = false, length = 64)
    private String collectionId;

    @Column(name = "name", length = 256)
    @Nullable
    private String name;

    @Column(name = "description", columnDefinition = "text")
    @Nullable
    private String description;

    @Column(name = "url", length = 1024)
    @Nullable
    private String url;

    @CreationTimestamp
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;

    @Column(name = "deleted_at")
    @Nullable
    private Instant deletedAt;

    protected OutlineCollection() {
    }

    public OutlineCollection(Connection connection, String collectionId, @Nullable String name) {
        this.connection = connection;
        this.collectionId = collectionId;
        this.name = name;
    }

    public Long getId() { return id; }
    public Connection getConnection() { return connection; }
    public String getCollectionId() { return collectionId; }
    @Nullable public String getName() { return name; }
    @Nullable public String getDescription() { return description; }
    @Nullable public String getUrl() { return url; }
    public Instant getIngestedAt() { return ingestedAt; }
    @Nullable public Instant getDeletedAt() { return deletedAt; }

    public void setName(@Nullable String name) { this.name = name; }
    public void setDescription(@Nullable String description) { this.description = description; }
    public void setUrl(@Nullable String url) { this.url = url; }

    public void softDelete(Instant at) {
        if (this.deletedAt != null) {
            return;
        }
        this.deletedAt = at;
    }
}
