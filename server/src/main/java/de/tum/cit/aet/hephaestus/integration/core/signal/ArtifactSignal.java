package de.tum.cit.aet.hephaestus.integration.core.signal;

import de.tum.cit.aet.hephaestus.workspace.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * One occurrence of one signal on one artifact, and what we did about it.
 *
 * <p>Writes go through {@link ArtifactSignalRepository}'s native statements rather than this mapping:
 * recording must be an insert-or-nothing race the database settles, and every state change is a
 * conditional update, neither of which Hibernate's dirty checking can express. The entity exists so
 * the reaper and the read views can work with rows in Java.
 */
@Entity
@Table(
    name = "artifact_signal",
    indexes = { @Index(name = "idx_artifact_signal_state_changed", columnList = "state, state_changed_at") },
    uniqueConstraints = @UniqueConstraint(
        name = "uq_artifact_signal",
        columnNames = { "workspace_id", "artifact_kind", "artifact_id", "signal_name", "revision" }
    )
)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ArtifactSignal {

    @Id
    @EqualsAndHashCode.Include
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "workspace_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_artifact_signal_workspace")
    )
    @ToString.Exclude
    private Workspace workspace;

    @NonNull
    @Column(name = "artifact_kind", nullable = false, updatable = false, length = ArtifactKind.MAX_LENGTH)
    private String artifactKind;

    @NonNull
    @Column(name = "artifact_id", nullable = false, updatable = false)
    private Long artifactId;

    @NonNull
    @Column(name = "signal_name", nullable = false, updatable = false, length = SignalName.MAX_LENGTH)
    private String signalName;

    @NonNull
    @Column(name = "revision", nullable = false, updatable = false, length = SignalRevision.MAX_LENGTH)
    private String revision;

    /** When the observed thing happened upstream — for a sync discovery, only as precise as the sync. */
    @NonNull
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "discovered_via", nullable = false, length = 16)
    private DiscoveredVia discoveredVia;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private SignalState state;

    @Nullable
    @Enumerated(EnumType.STRING)
    @Column(name = "state_reason", length = 48)
    private SignalStateReason stateReason;

    /** The review this signal produced, when it produced one. */
    @Nullable
    @Column(name = "job_id")
    private UUID jobId;

    /** Doubles as the reaper's clock: how long this signal has been waiting in its current state. */
    @NonNull
    @Column(name = "state_changed_at", nullable = false)
    private Instant stateChangedAt;

    /** The ledger identity of this row, for handing back to a {@link SignalRecorder}. */
    public SignalKey key() {
        return new SignalKey(workspace.getId(), artifactId, SignalName.of(signalName), new SignalRevision(revision));
    }
}
