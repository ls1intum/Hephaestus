package de.tum.cit.aet.hephaestus.integration.slack.domain;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

/**
 * One append-only audit row per Slack channel consent-state transition ({@code PENDING → ACTIVE},
 * {@code ACTIVE ⇄ PAUSED}, {@code * → REVOKED}), written by {@code SlackChannelConsentService.transition}. The row
 * captures the GDPR-accountability record — who changed a channel to what, when, and why — independently of the
 * mutable current state on {@link SlackMonitoredChannel}.
 *
 * <p><strong>Immutable by construction.</strong> The entity is {@link Immutable} and its repository exposes only
 * insert + workspace-scoped read; there is no update or delete path, so the audit trail cannot be rewritten. The
 * only mutation to the table is appending a new transition. Workspace-scoped (scalar {@code workspaceId}, absent
 * from {@code WorkspaceScopedTables.GLOBAL_TABLES}) → a tenancy predicate rides every read.
 */
@Entity
@Immutable
@Table(name = "slack_channel_consent_event")
@Getter
@NoArgsConstructor
public class SlackChannelConsentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "slack_channel_id", nullable = false, length = 32)
    private String slackChannelId;

    /** The state the channel left. Null only for a hypothetical transition of a not-yet-persisted channel. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 16)
    private @Nullable ConsentState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 16)
    private ConsentState toState;

    /** The workspace {@code User} (admin) who made the change; null if driven by a system path rather than an admin. */
    @Column(name = "actor_user_id")
    private @Nullable Long actorUserId;

    @Column(name = "reason")
    private @Nullable String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SlackChannelConsentEvent(
        Long workspaceId,
        String slackChannelId,
        @Nullable ConsentState fromState,
        ConsentState toState,
        @Nullable Long actorUserId,
        @Nullable String reason
    ) {
        this.workspaceId = workspaceId;
        this.slackChannelId = slackChannelId;
        this.fromState = fromState;
        this.toState = toState;
        this.actorUserId = actorUserId;
        this.reason = reason;
    }
}
