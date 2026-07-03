package de.tum.cit.aet.hephaestus.integration.slack.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.providers.mentor.SlackConversationProjector;
import de.tum.cit.aet.hephaestus.agent.handler.ConversationReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobService;
import de.tum.cit.aet.hephaestus.agent.job.conversation.ConversationThreadTriggerScheduler;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.node.ObjectNode;

/**
 * Conversation-detection integration tests (Testcontainers — the candidate scan rides a real Postgres
 * {@code bigint[]} column, the growth count rides lexicographic Slack-{@code ts} comparison, and the watermark
 * advance is a real UPDATE). {@link AgentJobService} is mocked so the enqueue is observable without seeding a
 * workspace/agent-config graph; the raw SQL the scheduler owns is exercised for real.
 *
 * <p>Channel ingestion is off by default (a deliberate, privacy-sensitive parked capability), so this test — which
 * exercises subsystem B directly — enables it via {@code hephaestus.integration.slack.conversation-ingest.enabled}.
 */
@TestPropertySource(properties = "hephaestus.integration.slack.conversation-ingest.enabled=true")
class ConversationThreadDetectionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ConversationThreadTriggerScheduler scheduler;

    @Autowired
    private SlackConversationProjector projector;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private AgentJobService agentJobService;

    // Distinct workspace ids per test → isolation in the shared container (no FK to a workspace row).
    private static final AtomicLong WS_SEQ = new AtomicLong(9_500_000L);

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

    private void seedThread(
        long workspaceId,
        String channelId,
        String threadTs,
        String lastTs,
        int messageCount,
        String participants
    ) {
        jdbc.update(
            "INSERT INTO slack_thread (workspace_id, slack_channel_id, slack_thread_ts, first_ts, last_ts, message_count, participant_member_ids, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS bigint[]), now())",
            workspaceId,
            channelId,
            threadTs,
            threadTs,
            lastTs,
            messageCount,
            participants
        );
    }

    private void seedMessage(long workspaceId, String channelId, String ts, String threadTs) {
        jdbc.update(
            "INSERT INTO slack_message (workspace_id, slack_team_id, slack_channel_id, slack_ts, slack_thread_ts, author_slack_user_id, text, ingested_at) " +
                "VALUES (?, 'T1', ?, ?, ?, 'U1', 'hi', now())",
            workspaceId,
            channelId,
            ts,
            threadTs
        );
    }

    @Test
    @DisplayName(
        "a settled, deep, grown thread enqueues one CONVERSATION_REVIEW per participant and advances the watermark"
    )
    void detectsSettledThreadAndEnqueuesPerParticipant() {
        long ws = newWorkspace();
        long baseSecond = Instant.now().getEpochSecond() - 1200; // 20 minutes ago → past the 10-minute quiescence
        String rootTs = baseSecond + ".000000";
        String t1 = (baseSecond + 1) + ".000000";
        String t2 = (baseSecond + 2) + ".000000";
        String lastTs = (baseSecond + 3) + ".000000";

        seedChannel(ws, "C1", "ACTIVE");
        seedThread(ws, "C1", rootTs, lastTs, 4, "{100,101}");
        seedMessage(ws, "C1", rootTs, null);
        seedMessage(ws, "C1", t1, rootTs);
        seedMessage(ws, "C1", t2, rootTs);
        seedMessage(ws, "C1", lastTs, rootTs);

        when(agentJobService.submit(eq(ws), eq(AgentJobType.CONVERSATION_REVIEW), any())).thenReturn(
            Optional.of(new AgentJob())
        );

        scheduler.detectNow();

        ArgumentCaptor<ConversationReviewSubmissionRequest> captor = ArgumentCaptor.forClass(
            ConversationReviewSubmissionRequest.class
        );
        verify(agentJobService, times(2)).submit(eq(ws), eq(AgentJobType.CONVERSATION_REVIEW), captor.capture());
        assertThat(captor.getAllValues())
            .extracting(ConversationReviewSubmissionRequest::aboutUserId)
            .containsExactlyInAnyOrder(100L, 101L);
        assertThat(captor.getAllValues()).allSatisfy(r -> {
            assertThat(r.slackChannelId()).isEqualTo("C1");
            assertThat(r.slackThreadTs()).isEqualTo(rootTs);
            assertThat(r.lastTs()).isEqualTo(lastTs);
        });

        // Watermark advanced to the thread's newest ts after enqueue → a no-growth re-sweep now enqueues nothing.
        String watermark = jdbc.queryForObject(
            "SELECT last_reviewed_ts FROM slack_thread WHERE workspace_id = ? AND slack_channel_id = ? AND slack_thread_ts = ?",
            String.class,
            ws,
            "C1",
            rootTs
        );
        assertThat(watermark).isEqualTo(lastTs);
    }

    @Test
    @DisplayName("a thread that has not settled (recent last message) enqueues nothing")
    void skipsNonQuiescentThread() {
        long ws = newWorkspace();
        long recentSecond = Instant.now().getEpochSecond() - 60; // 1 minute ago → inside quiescence window
        String rootTs = recentSecond + ".000000";
        String lastTs = (recentSecond + 3) + ".000000";

        seedChannel(ws, "C1", "ACTIVE");
        seedThread(ws, "C1", rootTs, lastTs, 4, "{100}");
        seedMessage(ws, "C1", rootTs, null);
        seedMessage(ws, "C1", (recentSecond + 1) + ".000000", rootTs);
        seedMessage(ws, "C1", (recentSecond + 2) + ".000000", rootTs);
        seedMessage(ws, "C1", lastTs, rootTs);

        scheduler.detectNow();

        verify(agentJobService, times(0)).submit(eq(ws), eq(AgentJobType.CONVERSATION_REVIEW), any());
    }

    @Test
    @DisplayName("buildThreadPayload materialises the non-tombstoned turns of one thread under the quarantine envelope")
    void projectsSingleThread() {
        long ws = newWorkspace();
        long baseSecond = Instant.now().getEpochSecond() - 1200;
        String rootTs = baseSecond + ".000000";
        String replyTs = (baseSecond + 1) + ".000000";
        seedChannel(ws, "C1", "ACTIVE");
        seedThread(ws, "C1", rootTs, replyTs, 2, "{100}");
        seedMessage(ws, "C1", rootTs, null);
        seedMessage(ws, "C1", replyTs, rootTs);

        ObjectNode payload = projector.buildThreadPayload(ws, "C1", rootTs);

        assertThat(payload.get("channel").asString()).isEqualTo("C1");
        assertThat(payload.get("messageCount").asInt()).isEqualTo(2);
        assertThat(payload.get("_meta").get("trustLevel").asString()).isEqualTo("UNTRUSTED_EXTERNAL");
        assertThat(payload.get("messages")).hasSize(2);
    }

    @Test
    @DisplayName("consent gate is atomic with the read: a revoked/paused channel yields an EMPTY detection projection")
    void projectsNothingWhenChannelConsentIsNotActive() {
        long baseSecond = Instant.now().getEpochSecond() - 1200;
        String rootTs = baseSecond + ".000000";
        String replyTs = (baseSecond + 1) + ".000000";

        // A channel whose consent was REVOKED between enqueue and execution: the settled thread is unchanged,
        // but the detection projection must not leak its messages into the LLM.
        long wsRevoked = newWorkspace();
        seedChannel(wsRevoked, "C1", "REVOKED");
        seedThread(wsRevoked, "C1", rootTs, replyTs, 2, "{100}");
        seedMessage(wsRevoked, "C1", rootTs, null);
        seedMessage(wsRevoked, "C1", replyTs, rootTs);

        ObjectNode revoked = projector.buildThreadPayload(wsRevoked, "C1", rootTs);
        assertThat(revoked.get("messageCount").asInt()).isZero();
        assertThat(revoked.get("messages")).isEmpty();

        // A PAUSED channel is likewise gated out.
        long wsPaused = newWorkspace();
        seedChannel(wsPaused, "C1", "PAUSED");
        seedThread(wsPaused, "C1", rootTs, replyTs, 2, "{100}");
        seedMessage(wsPaused, "C1", rootTs, null);
        seedMessage(wsPaused, "C1", replyTs, rootTs);
        assertThat(projector.buildThreadPayload(wsPaused, "C1", rootTs).get("messages")).isEmpty();

        // Control: the SAME thread shape on an ACTIVE channel still projects both turns.
        long wsActive = newWorkspace();
        seedChannel(wsActive, "C1", "ACTIVE");
        seedThread(wsActive, "C1", rootTs, replyTs, 2, "{100}");
        seedMessage(wsActive, "C1", rootTs, null);
        seedMessage(wsActive, "C1", replyTs, rootTs);
        assertThat(projector.buildThreadPayload(wsActive, "C1", rootTs).get("messages")).hasSize(2);
    }

    @Test
    @DisplayName(
        "candidate scan is workspace-pinned: another workspace's thread with the same ids is not enqueued for this one"
    )
    void doesNotLeakAcrossWorkspaces() {
        long wsA = newWorkspace();
        long wsB = newWorkspace();
        long baseSecond = Instant.now().getEpochSecond() - 1200;
        String rootTs = baseSecond + ".000000";
        String lastTs = (baseSecond + 3) + ".000000";
        // Only wsB's channel is ACTIVE; wsA's is PENDING, so wsA must never be enqueued.
        seedChannel(wsA, "C1", "PENDING");
        seedThread(wsA, "C1", rootTs, lastTs, 4, "{100}");
        seedChannel(wsB, "C1", "ACTIVE");
        seedThread(wsB, "C1", rootTs, lastTs, 4, "{100}");
        for (long s = baseSecond; s <= baseSecond + 3; s++) {
            seedMessage(wsB, "C1", s + ".000000", s == baseSecond ? null : rootTs);
        }

        when(agentJobService.submit(eq(wsB), eq(AgentJobType.CONVERSATION_REVIEW), any())).thenReturn(
            Optional.of(new AgentJob())
        );

        scheduler.detectNow();

        verify(agentJobService, times(0)).submit(eq(wsA), any(), any());
        verify(agentJobService, times(1)).submit(eq(wsB), eq(AgentJobType.CONVERSATION_REVIEW), any());
    }
}
