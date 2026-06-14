package de.tum.cit.aet.hephaestus.practices.feedback;

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
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Immutable record of one concrete placement of a synthesized {@code Feedback} unit on a
 * delivery surface — a summary block, an inline diff anchor, or a conversation turn.
 *
 * <p>A single feedback unit can have multiple placements (1:N): for example a SUMMARY block
 * plus several INLINE anchors. Each placement carries the full diff-anchor coordinates (path,
 * line, side, pinned commit) and tracks its own {@link PlacementPostedState} lifecycle and
 * external comment/note id so re-delivery, snapping, and resolution can be reconciled per anchor.
 *
 * <p>Append-only / {@code @Immutable}: posting outcomes that change over time (snapped,
 * outdated, resolved) are represented by writing a new placement row or by the reconciler's
 * dedicated update path; the row itself is not mutated through the ORM. The parent
 * {@link Feedback} lives in the same {@code practices.feedback} module, so a real
 * {@code @ManyToOne} association is used with DB-level {@code ON DELETE CASCADE}.
 *
 * @see Feedback for the feedback unit being placed
 * @see PlacementSurface for SUMMARY/INLINE/CONVERSATION_TURN
 * @see PlacementPostedState for the posting lifecycle
 */
@Entity
@Immutable
@Table(
    name = "feedback_placement",
    indexes = {
        @Index(name = "idx_feedback_placement_feedback", columnList = "feedback_id"),
        @Index(name = "idx_feedback_placement_external_ref", columnList = "external_ref"),
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackPlacement {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    /**
     * The feedback unit this placement renders. Uses DB-level {@code ON DELETE CASCADE} so
     * deleting a feedback unit cleans up its immutable placement rows.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "feedback_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_feedback_placement_feedback")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Feedback feedback;

    /**
     * Direct access to the feedback ID without triggering a lazy load on the {@link #feedback} proxy.
     * Read-only: mapped to the same column as the {@code @ManyToOne} relationship.
     */
    @Column(name = "feedback_id", nullable = false, insertable = false, updatable = false, columnDefinition = "UUID")
    private UUID feedbackId;

    /** Where this placement renders: SUMMARY, INLINE, or CONVERSATION_TURN. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "placement", length = 32, nullable = false)
    private PlacementSurface placement;

    // --- Diff anchor coordinates (all nullable: only INLINE placements anchor to a diff) ---

    /** Granularity of the anchor (LINE / RANGE / FILE / IMAGE). Null for non-inline placements. */
    @Enumerated(EnumType.STRING)
    @Column(name = "anchor_kind", length = 16)
    private PlacementAnchorKind anchorKind;

    /** Path of the anchored file on the head side. */
    @Column(name = "anchor_path", columnDefinition = "TEXT")
    private String anchorPath;

    /** Path of the anchored file on the base side (set when the file was renamed). */
    @Column(name = "anchor_old_path", columnDefinition = "TEXT")
    private String anchorOldPath;

    /** First anchored line (1-based). */
    @Column(name = "anchor_start_line")
    private Integer anchorStartLine;

    /** Last anchored line (1-based); equals start for a single-line anchor. */
    @Column(name = "anchor_end_line")
    private Integer anchorEndLine;

    /** Diff side of the anchor end / single line (OLD or NEW). */
    @Enumerated(EnumType.STRING)
    @Column(name = "anchor_side", length = 8)
    private PlacementAnchorSide anchorSide;

    /** Diff side of the anchor start for a multi-side range (OLD or NEW). */
    @Enumerated(EnumType.STRING)
    @Column(name = "anchor_start_side", length = 8)
    private PlacementAnchorSide anchorStartSide;

    /** The quoted source text the anchor refers to (content anchor, survives line drift). */
    @Column(name = "anchor_quote", columnDefinition = "TEXT")
    private String anchorQuote;

    /** Commit SHA the anchor was pinned to when posted. */
    @Column(name = "pinned_commit_sha", length = 64)
    private String pinnedCommitSha;

    // --- External delivery reconciliation ---

    /** External id of the posted comment/note (1:N — one per placement). */
    @Column(name = "external_ref", columnDefinition = "TEXT")
    private String externalRef;

    /** External id of the thread/discussion the comment was posted in. */
    @Column(name = "thread_external_ref", columnDefinition = "TEXT")
    private String threadExternalRef;

    /** Whether the placement's thread/note has been marked resolved on the surface. */
    @Column(name = "resolved")
    private Boolean resolved;

    /** When the placement was resolved. */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** External id of the resolving action/comment, if distinct from {@link #externalRef}. */
    @Column(name = "resolved_external_ref", columnDefinition = "TEXT")
    private String resolvedExternalRef;

    /** The posting lifecycle of this placement against its external surface. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "posted_state", length = 16, nullable = false)
    private PlacementPostedState postedState;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
