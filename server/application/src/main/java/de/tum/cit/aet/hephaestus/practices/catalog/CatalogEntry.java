package de.tum.cit.aet.hephaestus.practices.catalog;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "catalog_entry")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CatalogEntry {
    @EmbeddedId
    @EqualsAndHashCode.Include
    private Id id;

    @Column(length = 64)
    private @Nullable ItemKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private @Nullable JsonNode definition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private @Nullable JsonNode base;

    @Column
    private @Nullable Integer position;

    @Column(name = "excluded_at")
    private @Nullable Instant excludedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    @ColumnDefault("0")
    private long version;

    public CatalogEntry(CatalogEntryType type, String slug) {
        this.id = new Id(type, slug);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    @Embeddable
    public record Id(
            @Enumerated(EnumType.STRING) @Column(name = "entry_type", length = 16)
            CatalogEntryType type,

            @Column(length = 64) String slug)
            implements Serializable {
        public Id {
            Objects.requireNonNull(type);
            Objects.requireNonNull(slug);
        }
    }
}
