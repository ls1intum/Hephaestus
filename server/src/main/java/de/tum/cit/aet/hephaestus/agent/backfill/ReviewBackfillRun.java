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
 * <p>A workspace adopting Hephaestus wants a baseline — "review the pull requests of the last 30 days" —
 * and the ingestion path deliberately refuses to give it one: a first sync records thousands of signals
 * and triggers none of them, precisely so adoption does not fire thousands of reviews. This row is how
 * that refusal is lifted, once, on purpose, for a named range.
 *
 * <p>Not to be confused with {@code RepositoryToMonitor}'s backfill checkpoints, which are about
 * <em>fetching</em> history from a provider. This is about <em>reviewing</em> history already mirrored.
 */
@Entity
@Table(
    name = "review_backfill_run",
    indexes = {
        @Index(name = "idx_review_backfill_run_workspace", columnList = "workspace_id, created_at"),
        @Index(name = "idx_review_backfill_run_status", columnList = "status"),
    }
)
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
     * The driver writes a run back detached, which is a {@code merge} — a full-column copy-back of the
     * status and counters as they were when the batch started. Without this lock an admin's mid-batch
     * cancel would be overwritten with {@code RUNNING} and the campaign would keep spending; with it the
     * losing write throws, the batch's progress is discarded, and the next tick re-reads the cancel.
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
        foreignKey = @ForeignKey(name = "fk_review_backfill_run_workspace")
    )
    @ToString.Exclude
    private Workspace workspace;

    /** The kind of work this campaign reviews. One kind per run, so the estimate means one thing. */
    @NotNull
    @Column(name = "artifact_kind", nullable = false, updatable = false, length = ArtifactKind.MAX_LENGTH)
    private String artifactKind;

    /**
     * How the signals this run records came to be known — {@link DiscoveredVia#BACKFILL} for a campaign
     * an admin scoped and confirmed by hand, {@link DiscoveredVia#SWEEP} for one a
     * {@link ReviewSweepSchedule} opened over recent work.
     *
     * <p>Carried on the run rather than decided at the submitter, because by the time an artifact is
     * offered nothing else remembers which of the two this was, and the answer decides both the ledger's
     * discovery mode and — through {@code SignalOrigins} — the population every resulting measurement is
     * filed in. A submitter that hard-coded BACKFILL would file a nightly sweep as a hindsight-selected
     * corpus, and the reflection read model would hide every finding it produced.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'BACKFILL'")
    @Column(name = "discovered_via", nullable = false, updatable = false, length = 16)
    private DiscoveredVia discoveredVia = DiscoveredVia.BACKFILL;

    /**
     * The schedule that opened this run, or null for a campaign an admin scoped by hand.
     *
     * <p>A plain column rather than an association, and deliberately without a foreign key. The driver
     * never navigates to the schedule, so a lazy association here would only be a proxy waiting to be
     * touched outside the transaction that loaded it. And a constraint would force a choice between
     * refusing to delete a schedule and nulling this column when one is deleted — the second of which
     * erases, from a row describing money that was spent, the record of what authorised spending it.
     * A soft reference that may name something gone is the honest shape for history.
     */
    @Nullable
    @Column(name = "sweep_schedule_id", updatable = false, columnDefinition = "UUID")
    private UUID sweepScheduleId;

    /**
     * Window start, inclusive, over the artifact's creation time.
     *
     * <p>Creation rather than last-update on purpose: an update timestamp moves while the campaign runs,
     * so a row could enter or leave the scope mid-walk and the run would no longer be reviewing the set
     * it was costed against.
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

    /** How many artifacts the preflight found in scope. What the admin was shown before confirming. */
    @NotNull
    @Column(name = "estimated_artifacts", nullable = false, updatable = false)
    private Integer estimatedArtifacts = 0;

    /**
     * What the preflight thought the campaign would cost, in USD. Null when the workspace has no priced
     * review history to derive a per-review cost from, and must be rendered as unknown rather than as
     * zero — an unknown cost shown as free invites a confirmation nobody meant to give.
     */
    @Nullable
    @Column(name = "estimated_cost_usd", precision = 12, scale = 4, updatable = false)
    private BigDecimal estimatedCostUsd;

    /**
     * The highest artifact id already walked. Null before the first batch. The walk is ordered by id
     * ascending, which is a stable total order independent of any timestamp the mirror may rewrite.
     *
     * <p>A run that pauses must NOT advance this past artifacts it did not submit. A gap-toothed
     * baseline is worse than a truncated one: nothing downstream can tell "not reviewed" from
     * "reviewed, nothing found".
     */
    @Nullable
    @Column(name = "cursor_artifact_id")
    private Long cursorArtifactId;

    /** Artifacts for which a review job was created. */
    @NotNull
    @ColumnDefault("0")
    @Column(name = "submitted_count", nullable = false)
    private Integer submittedCount = 0;

    /**
     * Artifacts the campaign walked past without creating a job: already recorded at this state, refused
     * by the gate, or outside the workspace review scope. Counted rather than dropped so the run's own
     * arithmetic adds up on screen.
     */
    @NotNull
    @ColumnDefault("0")
    @Column(name = "passed_count", nullable = false)
    private Integer passedCount = 0;

    /**
     * Artifacts whose submission threw, and which therefore have no ledger row, no observation and no
     * decision recorded anywhere.
     *
     * <p>Its own counter rather than folded into {@link #passedCount}: a pass means the campaign looked
     * and decided, a failure means it never got an answer. Counting failures as passes would let
     * {@code submitted + passed} reach the estimate and report COMPLETED over a baseline with holes.
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
