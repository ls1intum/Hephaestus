package de.tum.cit.aet.hephaestus.integration.core.item;

import de.tum.cit.aet.hephaestus.integration.core.itemevent.ItemKindConverter;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(
        name = "item",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_item_workspace",
                        columnNames = {"id", "workspace_id"}))
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private long workspaceId;

    @Convert(converter = ItemKindConverter.class)
    @Column(name = "kind", nullable = false, length = 64, updatable = false)
    private ItemKind kind;

    @Nullable
    @Column(name = "parent_id")
    private Long parentId;

    @Nullable
    @Column(name = "source_id")
    private Long sourceId;

    @Nullable
    @Column(name = "path", columnDefinition = "text")
    private String path;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Nullable
    @Column(name = "removed_at")
    private Instant removedAt;
}
