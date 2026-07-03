package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.context.providers.mentor.SlackConversationProjector;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Slice 6 conversation-projector firewall + tombstone/edit integration tests (Testcontainers — the participant
 * union rides a real Postgres {@code bigint[]}/GIN column and {@code = ANY(...)}). Deterministic: rows are seeded
 * with distinct workspace ids per test, so no shared-container bleed. Proves the single privacy invariant — a
 * non-participant never sees a thread — and the consent gate + tombstone/edit projection.
 */
class SlackConversationProjectorIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SlackConversationProjector projector;

    @Autowired
    private SlackMessageRepository messageRepository;

    @Autowired
    private JdbcTemplate jdbc;

    // Distinct workspace ids per test → isolation without a clean-between step (no FK to a workspace row).
    private static final AtomicLong WS_SEQ = new AtomicLong(9_000_000L);

    private long newWorkspace() {
        return WS_SEQ.incrementAndGet();
    }

    /**
     * {@code slack_thread.participant_member_ids} (bigint[] + GIN) and {@code last_reviewed_ts} are
     * deliberately UNMAPPED on the {@code SlackThread} entity (raw-JDBC-only — see SlackThreadRepository
     * and changelog changesets -12/-13). Production creates them via Liquibase; this integration profile
     * builds the schema with Hibernate {@code ddl-auto: create} and disables Liquibase, so they are absent
     * unless we add them. Idempotent DDL mirroring the production migration keeps the fixture green without
     * touching production code or the changelog.
     */
    @BeforeEach
    void ensureUnmappedSlackThreadColumns() {
        jdbc.execute(
            "ALTER TABLE slack_thread ADD COLUMN IF NOT EXISTS participant_member_ids BIGINT[] NOT NULL DEFAULT '{}'"
        );
        jdbc.execute("ALTER TABLE slack_thread ADD COLUMN IF NOT EXISTS last_reviewed_ts VARCHAR(32)");
        jdbc.execute(
            "CREATE INDEX IF NOT EXISTS idx_slack_thread_participants ON slack_thread USING GIN (participant_member_ids)"
        );
    }

    private void seedChannel(long workspaceId, String channelId, String consentState) {
        jdbc.update(
            "INSERT INTO slack_monitored_channel (workspace_id, slack_team_id, slack_channel_id, consent_state, backfill_state, created_at) " +
                "VALUES (?, 'T1', ?, ?, 'NONE', now())",
            workspaceId,
            channelId,
            consentState
        );
    }

    /** Seed a thread aggregate with an explicit participant member-id set (text array literal → bigint[]). */
    private void seedThread(
        long workspaceId,
        String channelId,
        String threadTs,
        String lastTs,
        String participantArrayLiteral
    ) {
        jdbc.update(
            "INSERT INTO slack_thread (workspace_id, slack_channel_id, slack_thread_ts, first_ts, last_ts, message_count, participant_member_ids, created_at) " +
                "VALUES (?, ?, ?, ?, ?, 1, CAST(? AS bigint[]), now())",
            workspaceId,
            channelId,
            threadTs,
            threadTs,
            lastTs,
            participantArrayLiteral
        );
    }

    private void seedMessage(long workspaceId, String channelId, String ts, String threadTs, String text) {
        jdbc.update(
            "INSERT INTO slack_message (workspace_id, slack_team_id, slack_channel_id, slack_ts, slack_thread_ts, author_slack_user_id, text, ingested_at) " +
                "VALUES (?, 'T1', ?, ?, ?, 'U1', ?, now())",
            workspaceId,
            channelId,
            ts,
            threadTs,
            text
        );
    }

    private ArrayNode conversations(ObjectNode payload) {
        return (ArrayNode) payload.get("conversations");
    }

    @Test
    @DisplayName("participant firewall: a non-participant never sees the thread; a participant does")
    void participantFirewall() {
        long ws = newWorkspace();
        seedChannel(ws, "C1", "ACTIVE");
        // Thread's participants are members 100 and 101 only.
        seedThread(ws, "C1", "100.0", "100.5", "{100,101}");
        seedMessage(ws, "C1", "100.0", null, "root");
        seedMessage(ws, "C1", "100.5", "100.0", "reply");

        // A participant sees the whole thread…
        ObjectNode forMember = projector.buildPayload(ws, 100L);
        assertThat(conversations(forMember)).hasSize(1);
        assertThat(conversations(forMember).get(0).get("messages")).hasSize(2);

        // …a workspace peer who never took part sees NOTHING.
        ObjectNode forOutsider = projector.buildPayload(ws, 999L);
        assertThat(conversations(forOutsider)).isEmpty();
        assertThat(forOutsider.get("totalThreads").asInt()).isZero();
    }

    @Test
    @DisplayName("consent gate: a participant of a non-ACTIVE channel's thread sees nothing")
    void consentGateHidesNonActiveChannels() {
        long ws = newWorkspace();
        seedChannel(ws, "C1", "PENDING");
        seedThread(ws, "C1", "100.0", "100.0", "{100}");
        seedMessage(ws, "C1", "100.0", null, "root");

        assertThat(conversations(projector.buildPayload(ws, 100L))).isEmpty();

        // Revoking a formerly-active channel also drops it out immediately.
        long ws2 = newWorkspace();
        seedChannel(ws2, "C2", "REVOKED");
        seedThread(ws2, "C2", "200.0", "200.0", "{100}");
        seedMessage(ws2, "C2", "200.0", null, "root");
        assertThat(conversations(projector.buildPayload(ws2, 100L))).isEmpty();
    }

    @Test
    @DisplayName("tenant isolation: a participant id shared across workspaces sees only its own workspace's thread")
    void workspaceIsolation() {
        long wsA = newWorkspace();
        long wsB = newWorkspace();
        seedChannel(wsA, "C1", "ACTIVE");
        seedThread(wsA, "C1", "100.0", "100.0", "{100}");
        seedMessage(wsA, "C1", "100.0", null, "A-only");
        seedChannel(wsB, "C1", "ACTIVE");
        seedThread(wsB, "C1", "100.0", "100.0", "{100}");
        seedMessage(wsB, "C1", "100.0", null, "B-only");

        ObjectNode forA = projector.buildPayload(wsA, 100L);
        assertThat(conversations(forA)).hasSize(1);
        assertThat(conversations(forA).get(0).get("messages").get(0).get("text").asString()).isEqualTo("A-only");
    }

    @Test
    @DisplayName("tombstone + edit: a deleted message drops out; an edited message shows its new text and edited flag")
    void tombstoneAndEditProjection() {
        long ws = newWorkspace();
        seedChannel(ws, "C1", "ACTIVE");
        seedThread(ws, "C1", "100.0", "100.5", "{100}");
        seedMessage(ws, "C1", "100.0", null, "root will be edited");
        seedMessage(ws, "C1", "100.5", "100.0", "reply will be deleted");

        // Edit the root, tombstone the reply (both via the scoped repository UPDATEs the ingest path drives).
        assertThat(messageRepository.applyEdit(ws, "C1", "100.0", "root EDITED")).isEqualTo(1);
        assertThat(messageRepository.tombstone(ws, "C1", "100.5")).isEqualTo(1);

        ObjectNode payload = projector.buildPayload(ws, 100L);
        ArrayNode messages = (ArrayNode) conversations(payload).get(0).get("messages");

        // Only the surviving (edited) root remains; the tombstoned reply is gone.
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).get("text").asString()).isEqualTo("root EDITED");
        assertThat(messages.get(0).get("edited").asBoolean()).isTrue();
    }
}
