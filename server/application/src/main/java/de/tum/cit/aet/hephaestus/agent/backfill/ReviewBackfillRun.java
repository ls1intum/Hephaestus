package de.tum.cit.aet.hephaestus.agent.backfill;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.Nullable;

/**
 * One bounded, attributable campaign to review work that already existed.
 *
 * <p>A first sync deliberately triggers no reviews, so adoption does not fire thousands of them at once;
 * this row is how that refusal is lifted, once, on purpose, for a named range.
 *
 * <p>Not to be confused with {@code RepositoryToMonitor}'s backfill checkpoints, which fetch history from
 * a provider rather than review history already mirrored.
 */
@Entity
@Table(
        name = "review_backfill_run",
        indexes = {
            @Index(name = "idx_review_backfill_run_workspace", columnList = "workspace_id, created_at"),
            @Index(name = "idx_review_backfill_run_status", columnList = "status"),
        })
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ReviewBackfillRun {

    @Id
    @EqualsAndHashCode.Include
    @Column(columnDefinition = "UUID")
    private UUID id;

    /**
     * The driver writes a run back detached (a full-column {@code merge}), so without this lock an
     * admin's mid-batch cancel would be overwritten with {@code RUNNING} and the campaign would keep
     * spending.
     */
    @Version
    @ColumnDefault("0")
    @Column(name = "version", nullable = false)
    private Long version;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "workspace_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_review_backfill_run_workspace"))
    @ToString.Exclude
    private Workspace workspace;

    /** The kind of work this campaign reviews. One kind per run, so the estimate means one thing. */
    @NotNull
    @Column(name = "artifact_kind", nullable = false, updatable = false, length = ArtifactKind.MAX_LENGTH)
    private String artifactKind;

    /**
     * How the signals this run records came to be known — {@link DiscoveredVia#BACKFILL} for a campaign an
     * admin scoped and confirmed by hand, {@link DiscoveredVia#SWEEP} for one a {@link ReviewSweepSchedule}
     * opened over recent work. Carried on the run rather than decided at the submitter, since by the time
     * an artifact is offered nothing else remembers which of the two this was.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'BACKFILL'")
    @Column(name = "discovered_via", nullable = false, updatable = false, length = 16)
    private DiscoveredVia discoveredVia = DiscoveredVia.BACKFILL;

    /**
     * The schedule that opened this run, or null for a campaign an admin scoped by hand. Deliberately no
     * foreign key: a constraint would null this column when its schedule is deleted, erasing the record of
     * what authorised the spend — a soft reference that may name something gone is the honest shape for
     * history.
     */
    @Nullable
    @Column(name = "sweep_schedule_id", updatable = false, columnDefinition = "UUID")
    private UUID sweepScheduleId;

    /**
     * Window start, inclusive, over the artifact's creation time — not last-update, which moves while the
     * campaign runs and would let a row enter or leave the scope mid-walk.
     */
    @NotNull
    @Column(name = "from_at", nullable = false, updatable = false)
    private Instant fromAt;

    /** Window end, exclusive. */
    @NotNull
    @Column(name = "to_at", nullable = false, updatable = false)
    private Instant toAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ReviewBackfillStatus status = ReviewBackfillStatus.AWAITING_CONFIRMATION;

    /** Set iff {@link #status} is {@code PAUSED}; cleared when the run resumes. */
    @Nullable
    @Enumerated(EnumType.STRING)
    @Column(name = "pause_reason", length = 32)
    private ReviewBackfillPauseReason pauseReason;

    /** How many artifacts the preflight found in scope — what the admin was shown before confirming. */
    @NotNull
    @Column(name = "estimated_artifacts", nullable = false, updatable = false)
    private Integer estimatedArtifacts = 0;

    /**
     * What the preflight thought the campaign would cost, in USD. Null when the workspace has no priced
     * review history to derive a cost from; render that as unknown, not zero — an unknown cost shown as
     * free invites a confirmation nobody meant to give.
     */
    @Nullable
    @Column(name = "estimated_cost_usd", precision = 12, scale = 4, updatable = false)
    private BigDecimal estimatedCostUsd;

    /**
     * The highest artifact id already walked, ordered ascending so the walk is stable independent of any
     * timestamp the mirror may rewrite. Null before the first batch.
     *
     * <p>A run that pauses must NOT advance this past artifacts it did not submit — a gap-toothed baseline
     * is worse than a truncated one, since nothing downstream can tell "not reviewed" from "reviewed,
     * nothing found".
     */
    @Nullable
    @Column(name = "cursor_artifact_id")
    private Long cursorArtifactId;

    /** Artifacts for which a review job was created. */
    @NotNull
    @ColumnDefault("0")
    @Column(name = "submitted_count", nullable = false)
    private Integer submittedCount = 0;

    /** Artifacts the campaign walked past without creating a job: already recorded, gated, or out of scope. */
    @NotNull
    @ColumnDefault("0")
    @Column(name = "passed_count", nullable = false)
    private Integer passedCount = 0;

    /**
     * Artifacts whose submission threw, with no ledger row, observation, or decision recorded anywhere.
     * Kept separate from {@link #passedCount}: folding it in would let {@code submitted + passed} reach
     * the estimate and report COMPLETED over a baseline with holes.
     */
    @NotNull
    @ColumnDefault("0")
    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    @NotNull
    @Column(name = "requested_by_account_id", nullable = false, updatable = false)
    private Long requestedByAccountId;

    /** Who authorised the spend. Null until the run is confirmed. */
    @Nullable
    @Column(name = "confirmed_by_account_id")
    private Long confirmedByAccountId;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Nullable
    @Column(name = "started_at")
    private Instant startedAt;

    @Nullable
    @Column(name = "finished_at")
    private Instant finishedAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    public ArtifactKind kind() {
        return ArtifactKind.of(artifactKind);
    }

    /** Move to a state, keeping the pause reason and the terminal timestamp coherent with it. */
    public void transitionTo(ReviewBackfillStatus next, @Nullable ReviewBackfillPauseReason reason) {
        this.status = next;
        this.pauseReason = next == ReviewBackfillStatus.PAUSED ? reason : null;
        if (next == ReviewBackfillStatus.COMPLETED || next == ReviewBackfillStatus.CANCELLED) {
            this.finishedAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }
}
