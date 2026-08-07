package de.tum.cit.aet.hephaestus.agent.backfill;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
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
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

/**
 * One bounded, attributable campaign to review work that already existed.
 *
 * <p>A workspace adopting Hephaestus wants a baseline — "review the pull requests of the last 30 days" —
 * and the ingestion path deliberately refuses to give it one: a first sync records thousands of signals
 * and triggers none of them, precisely so adoption does not fire thousands of reviews. This row is how
 * that refusal is lifted, once, on purpose, for a named range.
 *
 * <p>Three properties are load-bearing and each is a column here:
 *
 * <ul>
 *   <li><strong>Confirmed before it costs anything.</strong> The run is created with its scope enumerated
 *       and costed and its status {@code AWAITING_CONFIRMATION}; nothing is submitted until an admin
 *       moves it to {@code RUNNING}. {@link #requestedByAccountId} and {@link #confirmedByAccountId} are kept
 *       apart so the estimate and the decision to spend are separately attributable.
 *   <li><strong>Resumable, and paused rather than thinned.</strong> {@link #cursorArtifactId} is the
 *       high-water mark of an ascending walk. When the budget runs out the run pauses <em>without
 *       advancing it</em>, so no artifact is passed over. A gap-toothed baseline is worse than a
 *       truncated one, because nothing downstream can tell "not reviewed" from "reviewed, nothing found".
 *   <li><strong>Bounded by construction.</strong> The window is closed at both ends and fixed at
 *       creation, so the scope cannot grow under a running campaign.
 * </ul>
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
     * What the preflight thought the campaign would cost, in USD.
     *
     * <p>Null when the workspace has no priced review history to derive a per-review cost from. Null is
     * shown as "unknown" rather than as zero: an unknown cost that renders as free is the single worst
     * thing this screen could do.
     */
    @Nullable
    @Column(name = "estimated_cost_usd", precision = 12, scale = 4, updatable = false)
    private BigDecimal estimatedCostUsd;

    /**
     * The highest artifact id already walked. Null before the first batch. The walk is ordered by id
     * ascending, which is a stable total order independent of any timestamp the mirror may rewrite.
     */
    @Nullable
    @Column(name = "cursor_artifact_id")
    private Long cursorArtifactId;

    /** Artifacts for which a review job was created. */
    @NotNull
    @Column(name = "submitted_count", nullable = false)
    private Integer submittedCount = 0;

    /**
     * Artifacts the campaign walked past without creating a job: already recorded at this state, refused
     * by the gate, or outside the workspace review scope. Counted rather than dropped so the run's own
     * arithmetic adds up on screen.
     */
    @NotNull
    @Column(name = "passed_count", nullable = false)
    private Integer passedCount = 0;

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

    /** The artifact kind as the signal vocabulary spells it. */
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
