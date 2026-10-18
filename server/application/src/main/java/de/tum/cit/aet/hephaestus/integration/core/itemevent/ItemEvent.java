package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import de.tum.cit.aet.hephaestus.integration.core.spi.FoundBy;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState;
import de.tum.cit.aet.hephaestus.integration.core.spi.ReviewRequestReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.Nullable;

@Entity
@Table(
        name = "item_event",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_item_event_workspace",
                    columnNames = {"id", "workspace_id"}),
            @UniqueConstraint(
                    name = "uk_item_event_key",
                    columnNames = {"workspace_id", "item_id", "name", "item_version"})
        })
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ItemEvent {
    @Id
    @EqualsAndHashCode.Include
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private long workspaceId;

    @Column(name = "item_id", nullable = false)
    private long itemId;

    @Column(name = "name", nullable = false, length = 128, updatable = false)
    private String name;

    /** The exact evidence version, or null while the source cannot pin one for this occasion yet. */
    @Nullable
    @Column(name = "item_version", columnDefinition = "text")
    private String itemVersion;

    /** The source's opaque fence for an occasion it could not pin; the version is pinned only while it holds. */
    @Nullable
    @Column(name = "pending_fence", length = 1024)
    private String pendingFence;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "found_by", nullable = false, length = 16)
    private FoundBy foundBy;

    @Nullable
    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Nullable
    @Column(name = "requested_by_id")
    private Long requestedById;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'RECORDED'")
    @Column(name = "state", nullable = false, length = 16)
    private ItemEventState state;

    @Nullable
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 48)
    private ReviewRequestReason reason;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Nullable
    @Column(name = "last_attempted_at")
    private Instant lastAttemptedAt;

    @Nullable
    @Column(name = "retry_at")
    private Instant retryAt;
}
