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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
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
 * A standing instruction to look at a workspace's recent work again, on a cadence, whether or not
 * anything announced it.
 *
 * <p>It exists because every other way into the review path needs something to happen first: an event,
 * a sync, a person asking, an admin starting a campaign. Without a schedule, an artifact nothing ever
 * signalled is indistinguishable from one that was offered and declined — both have no ledger row and no
 * observation — so "we never looked at this" is unanswerable for exactly the work most likely to have
 * slipped through a missed webhook.
 *
 * <p><strong>A schedule says when, not whose.</strong> There is deliberately no repository or author
 * list here. Which repositories a workspace reviews is already
 * {@code WorkspaceReviewScope.repositories}, and whose work it reviews is already the
 * {@code run_practice_review} role plus {@code runForAllUsers} — both ANDed onto every path by
 * {@code PracticeReviewDetectionGate}, this one included. A second copy of either axis on this row would
 * be a second answer to a question the workspace has already answered, and the two would disagree the
 * first time somebody changed one of them.
 *
 * <p>Each tick opens a {@link ReviewBackfillRun} rather than driving artifacts itself. A recurring
 * workspace-wide review <em>is</em> a campaign plus a recurrence, and the campaign machinery already
 * paces per tick, pauses on an exhausted budget, keeps a cursor and isolates each artifact — none of
 * which a second driver would get right by being written twice. The run is opened directly in
 * {@code RUNNING}: creating this row is the confirmation, and asking an admin to confirm each night's
 * sweep would make the schedule useless.
 */
@Entity
@Table(
    name = "review_sweep_schedule",
    uniqueConstraints = {
        // One schedule per kind of work. Two would each see the other's ledger rows as already covered,
        // and only one of them could ever hold the workspace's single active campaign slot — so the
        // loser would look enabled forever while sweeping nothing.
        @UniqueConstraint(
            name = "uq_review_sweep_schedule_workspace_kind",
            columnNames = { "workspace_id", "artifact_kind" }
        ),
    },
    indexes = { @Index(name = "idx_review_sweep_schedule_due", columnList = "enabled, next_run_at") }
)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ReviewSweepSchedule {

    @Id
    @EqualsAndHashCode.Include
    @Column(columnDefinition = "UUID")
    private UUID id;

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
        foreignKey = @ForeignKey(name = "fk_review_sweep_schedule_workspace")
    )
    @ToString.Exclude
    private Workspace workspace;

    /** The kind of work each sweep covers. One kind per schedule, like a campaign. */
    @NotNull
    @Column(name = "artifact_kind", nullable = false, updatable = false, length = ArtifactKind.MAX_LENGTH)
    private String artifactKind;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "cadence", nullable = false, length = 16)
    private ReviewSweepCadence cadence = ReviewSweepCadence.DAILY;

    /**
     * How far back each sweep looks, in days. Bounded at write time by
     * {@link ReviewSweepCadence#maxLookback()}; see that constant for why the bound is what keeps a
     * sweep's measurements admissible as LIVE.
     */
    @NotNull
    @Column(name = "lookback_days", nullable = false)
    private Integer lookbackDays = 2;

    /**
     * Whether the scheduler acts on this row. A disabled schedule is kept rather than deleted so the
     * admin's cadence and window survive a pause, and so "we stopped sweeping on the 3rd" stays legible.
     */
    @NotNull
    @ColumnDefault("true")
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    /**
     * When the next sweep is due.
     *
     * <p>Advanced by whole cadences from its own previous value, never from the moment the tick actually
     * ran: a schedule that added the interval to "now" would drift later by however long each tick was
     * delayed, and after a month a nightly sweep would be running at noon. The per-workspace jitter that
     * keeps an instance's schedules from stampeding is therefore applied once, at creation, as a phase
     * offset — not on every advance, which would be the same drift by another name.
     */
    @NotNull
    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    /**
     * When this schedule last actually opened a campaign. Null until the first one.
     *
     * <p>Deliberately not "when it last came due". It does not decide anything — {@link #windowStart}
     * ignores it on purpose — but it is the only thing that distinguishes a schedule that is working
     * from one whose every turn has been skipped for a month because the workspace's practices are off.
     * Moving it on a tick that opened nothing would make that distinction unreadable, which is the one
     * job it has.
     */
    @Nullable
    @Column(name = "last_run_at")
    private Instant lastRunAt;

    /**
     * The account whose authority each sweep spends under. Recorded on every run this schedule opens,
     * as both requester and confirmer, because a nightly spend nobody can be asked about is the thing a
     * confirmation step exists to prevent — and the schedule is where that consent was given.
     */
    @NotNull
    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private Long createdByAccountId;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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

    public Duration lookback() {
        return Duration.ofDays(lookbackDays);
    }

    /**
     * The window the sweep due at {@code now} covers: the last {@link #lookbackDays} days, every time.
     *
     * <p>Deliberately <em>not</em> anchored to {@link #lastRunAt}, which would make consecutive windows
     * abut and never re-walk a day. Abutting windows look tidier and are wrong: a campaign that paused on
     * an exhausted budget, was cancelled, or simply had not reached the end of its cursor leaves
     * artifacts it never offered, and an anchored window would have moved past them for good. Nothing
     * would ever look at them again, and nothing would say so.
     *
     * <p>Overlapping instead costs nothing, which is the whole reason the lookback ceiling is <em>twice</em>
     * the cadence rather than once. An artifact already measured at its current state produces the same
     * {@code SignalKey} the first sweep recorded, so the ledger's unique constraint refuses the second
     * offer and the campaign walks past it: the re-walk is a scope query, not a review, and not a charge.
     * The overlap is therefore the retry, and the ledger is what makes the retry free.
     */
    public Instant windowStart(Instant now) {
        return now.minus(lookback());
    }

    /**
     * When a schedule created now should first come due: immediately, offset by a fixed per-workspace
     * jitter of under an hour.
     *
     * <p>Immediately because an admin who has just described a nightly sweep should be able to see one
     * happen rather than take it on faith for a day; its first window is bounded by the same lookback as
     * every later one, so this is not a hidden backfill.
     *
     * <p>The jitter is derived from the workspace id and applied exactly once, here. Every later
     * occurrence is this instant plus whole cadences, so an instance with two hundred workspaces spreads
     * its nightly sweeps across the hour permanently, instead of every one of them waking the same
     * minute forever because they were all created by the same migration.
     */
    public static Instant firstRunAt(long workspaceId, Instant now) {
        return now.plus(Duration.ofMinutes(Math.floorMod(workspaceId, 60)));
    }

    /**
     * Move {@link #nextRunAt} to the first occurrence strictly after {@code now}, keeping its phase.
     *
     * <p>Whole cadences from the row's own previous value, so the time of day an admin implicitly chose
     * survives every late tick. Computed rather than looped so that an instance which was down for a
     * year does not spend a year's worth of iterations catching up — and, more importantly, does not
     * fire a year's worth of sweeps: the missed occurrences are skipped, not queued, because a sweep is
     * about what happened recently and re-running last March's is meaningless.
     */
    public void advancePast(Instant now) {
        Duration interval = cadence.interval();
        long missed = Math.max(0, Duration.between(nextRunAt, now).getSeconds() / interval.getSeconds());
        this.nextRunAt = nextRunAt.plus(interval.multipliedBy(missed + 1));
        this.updatedAt = now;
    }
}
