package de.tum.cit.aet.hephaestus.integration.slack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Durable first-writer-wins dedup marker for one inbound Slack Events API delivery, keyed on the Slack
 * {@code event_id}. Slack redelivers an un-acked event, and in a multi-replica deployment two pods can each
 * receive the same delivery — an in-memory {@code Set} on one controller instance cannot suppress that. A row here,
 * written with {@code INSERT … ON CONFLICT (event_id) DO NOTHING}, lets exactly one replica claim the event.
 *
 * <p><strong>Workspace-independent by construction.</strong> The controller dedups on the raw {@code event_id}
 * before it has resolved which workspace owns the Slack team, so this table carries no {@code workspace_id} and is
 * listed in {@code WorkspaceScopedTables.GLOBAL_TABLES} (and its repository is {@code @WorkspaceAgnostic}). It holds
 * no workspace content, so the Slack workspace-purge adapter deliberately does not cascade it.
 *
 * <p>Writes go through the repository's native upsert; the entity exists so Hibernate's {@code validate} boot check
 * and the tenancy metamodel walk both see the table. {@link #expiresAt} bounds the row's lifetime — a daily sweep
 * deletes anything past it, well beyond Slack's retry horizon.
 */
@Entity
@Table(
    name = "slack_event_dedup",
    indexes = { @Index(name = "idx_slack_event_dedup_expires_at", columnList = "expires_at") }
)
@Getter
@Setter
@NoArgsConstructor
public class SlackEventDedup {

    /** The Slack {@code event_id} of the delivery (globally unique per Slack event). */
    @Id
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    /** When this replica first claimed the event. */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /** After this instant the row is prunable — the retention sweep drops it. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
