package de.tum.cit.aet.hephaestus.practices.feedback.interaction;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.delivery.FeedbackDelivery;
import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
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
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Append-only engagement event — how a user interacted with feedback on any channel. An
 * actor–verb–object record in the xAPI / IMS Caliper tradition (immutable event log; we borrow the
 * shape, not the wire format). This is the "contributor interaction log" the research uses across
 * In-Context exposure, dashboard sessions, mentor sessions, and facilitator usage.
 *
 * <p>Distinct from the two other reception concepts: a {@code FindingReaction} is a contributor's
 * explicit verdict on a finding (applied/disputed); this is lower-level behavioural telemetry.
 * Pull dashboards (which compose feedback on read) emit ONLY these events — never a delivery row.
 */
@Entity
@Immutable
@Table(
    name = "feedback_interaction",
    indexes = {
        @Index(name = "idx_feedback_interaction_actor_occurred", columnList = "actor_id, occurred_at DESC"),
        @Index(name = "idx_feedback_interaction_workspace_channel", columnList = "workspace_id, channel"),
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FeedbackInteraction {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "workspace_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_feedback_interaction_workspace")
    )
    @ToString.Exclude
    private Workspace workspace;

    /** Who interacted (the contributor, or a facilitator). RESTRICT — engagement survives the join. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, foreignKey = @ForeignKey(name = "fk_feedback_interaction_actor"))
    @ToString.Exclude
    private User actor;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 32)
    private FeedbackChannel channel;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "verb", nullable = false, length = 24)
    private FeedbackInteractionVerb verb;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 24)
    private FeedbackInteractionTarget targetType;

    /** Typed FK when {@link #targetType} is FINDING — the high-value join ("was this finding's note resolved?"). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finding_id", foreignKey = @ForeignKey(name = "fk_feedback_interaction_finding"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ToString.Exclude
    private @Nullable PracticeFinding finding;

    /** Typed FK when {@link #targetType} is DELIVERY. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", foreignKey = @ForeignKey(name = "fk_feedback_interaction_delivery"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ToString.Exclude
    private @Nullable FeedbackDelivery delivery;

    /** Opaque id for targets with no table (PRACTICE_GOAL id, DASHBOARD_SESSION / MENTOR_SESSION). */
    @Column(name = "target_ref", length = 64)
    private @Nullable String targetRef;

    /** Free-form event detail (dwell ms, emoji, session id, turn ref). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @ToString.Exclude
    private @Nullable JsonNode metadata;

    @NotNull
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
