package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
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
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

/**
 * Immutable record of a composed feedback message PUSHED to a channel at a point in time — the
 * Phase-2 "what we sent" artifact, distinct from the Phase-1 finding (evidence) and the contributor's
 * {@code FindingReaction} (their verdict). Append-only: a re-post / edit is a NEW row linked through
 * {@link #supersedes}, so the temporal sequence of what was shown over a PR's lifecycle is preserved
 * (the substrate for studying in-PR uptake).
 *
 * <p><b>Persist push, derive pull.</b> Only push channels ({@link FeedbackChannel#IN_CONTEXT_PR};
 * later email/inbox) write rows here, storing the exact {@link #renderedBody} SNAPSHOT — not a
 * finding-id reference — because the composer is re-versioned per cohort, so a reference would
 * silently change meaning and the as-shown bytes could not be reconstructed. Pull dashboards compose
 * on read and persist only {@code FeedbackInteraction} engagement; the conversational mentor consumes
 * feedback as context and is not a delivery (its turns live in the session transcript).
 *
 * <p>The findings rendered in this delivery are linked via {@link FeedbackDeliveryFinding} (one row per
 * finding, with its display role). The goal context is NOT stored here — a delivery can span goals, so
 * the goal is derived per finding via {@code finding → practice → practiceGoal}, the single source of truth.
 */
@Entity
@Immutable
@Table(
    name = "feedback_delivery",
    uniqueConstraints = @UniqueConstraint(name = "uk_feedback_delivery_idempotency", columnNames = "idempotency_key"),
    indexes = {
        @Index(name = "idx_feedback_delivery_recipient_delivered", columnList = "recipient_id, delivered_at DESC"),
        @Index(name = "idx_feedback_delivery_workspace_channel", columnList = "workspace_id, channel"),
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FeedbackDelivery {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "workspace_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_feedback_delivery_workspace")
    )
    @ToString.Exclude
    private de.tum.cit.aet.hephaestus.workspace.Workspace workspace;

    /** The channel this was pushed to (drives the persist-push/derive-pull rule + the Hattie level). */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 32)
    private FeedbackChannel channel;

    /** The user the feedback was shown to (the contributor; a facilitator once that channel exists). RESTRICT. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "recipient_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_feedback_delivery_recipient")
    )
    @ToString.Exclude
    private User recipient;

    /** The exact rendered text shown (SNAPSHOT — byte-exact provenance, never recomputed). */
    @NotNull
    @Column(name = "rendered_body", columnDefinition = "TEXT", nullable = false)
    @ToString.Exclude
    private String renderedBody;

    /** Which composition logic produced this, for per-cohort (DBR) comparability. */
    @Column(name = "composer_version", length = 64)
    private @Nullable String composerVersion;

    /** The channel-native id of the delivered artifact (SCM comment id), for round-tripping + edits. */
    @Column(name = "external_ref", length = 255)
    private @Nullable String externalRef;

    /** The earlier delivery this one edits/replaces (null = original; newest in the chain = current). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_id", foreignKey = @ForeignKey(name = "fk_feedback_delivery_supersedes"))
    @ToString.Exclude
    private @Nullable FeedbackDelivery supersedes;

    /** Dedupe key so a retried job does not double-record a delivery (mirrors PracticeFinding insertIfAbsent). */
    @NotNull
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "delivered_at")
    private @Nullable Instant deliveredAt;

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
