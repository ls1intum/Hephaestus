package de.tum.cit.aet.hephaestus.integration.slack.conversation;

import de.tum.cit.aet.hephaestus.agent.conversation.ConversationCandidateSource;
import de.tum.cit.aet.hephaestus.agent.conversation.ConversationThreadCandidate;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Slack-owned implementation of the agent {@link ConversationCandidateSource} SPI: the settled-thread candidate
 * scan, the non-tombstoned turn counts, and the review-watermark advance — all over Slack's own
 * {@code slack_thread}/{@code slack_message}/{@code slack_monitored_channel} tables.
 *
 * <p><strong>Ownership.</strong> This lives in {@code integration.slack} because it reads/writes Slack's own
 * tables; the agent {@code ConversationThreadTriggerScheduler} consumes it through the SPI to enqueue
 * {@code CONVERSATION_REVIEW} jobs, and never reaches into the Slack schema. Raw {@link JdbcTemplate} (the tenancy
 * {@code StatementInspector} only hooks Hibernate); {@link #settledCandidates} is an inherently cross-workspace
 * sweep (each candidate carries its own {@code workspace_id}) and every other method carries an explicit
 * {@code workspace_id} predicate.
 */
@Component
@WorkspaceAgnostic(
    "settledCandidates is a cross-workspace sweep; every raw-JDBC read/write carries an explicit workspace_id " +
        "predicate (the per-candidate workspaceId for the counts and the watermark advance)"
)
public class SlackConversationCandidateSource implements ConversationCandidateSource {

    private final JdbcTemplate jdbc;

    public SlackConversationCandidateSource(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ConversationThreadCandidate> settledCandidates(int minMessageCount) {
        List<ConversationThreadCandidate> out = new ArrayList<>();
        jdbc.query(
            """
            SELECT t.workspace_id, t.id, t.slack_channel_id, t.slack_thread_ts, t.last_ts,
                   t.last_reviewed_ts, t.participant_member_ids
            FROM slack_thread t
            JOIN slack_monitored_channel c
              ON c.workspace_id = t.workspace_id AND c.slack_channel_id = t.slack_channel_id
            WHERE c.consent_state = 'ACTIVE'
              AND t.last_ts IS NOT NULL
              AND (t.last_reviewed_ts IS NULL OR t.last_ts > t.last_reviewed_ts)
              AND t.message_count >= ?
            ORDER BY t.last_ts ASC
            """,
            rs -> {
                long[] participants = readLongArray(rs.getArray("participant_member_ids"));
                out.add(
                    new ConversationThreadCandidate(
                        rs.getLong("workspace_id"),
                        rs.getLong("id"),
                        rs.getString("slack_channel_id"),
                        rs.getString("slack_thread_ts"),
                        rs.getString("last_ts"),
                        rs.getString("last_reviewed_ts"),
                        participants
                    )
                );
            },
            minMessageCount
        );
        return out;
    }

    @Override
    public long liveTurnCount(long workspaceId, String channelId, String threadTs) {
        Long n = jdbc.queryForObject(
            """
            SELECT count(*) FROM slack_message
            WHERE workspace_id = ? AND slack_channel_id = ?
              AND (slack_thread_ts = ? OR slack_ts = ?) AND deleted_at IS NULL
            """,
            Long.class,
            workspaceId,
            channelId,
            threadTs,
            threadTs
        );
        return n == null ? 0 : n;
    }

    @Override
    public long liveTurnCountSince(long workspaceId, String channelId, String threadTs, @Nullable String watermark) {
        // Slack ts strings sort lexicographically; '' is below any real ts, so a null watermark counts everything.
        String floor = watermark == null ? "" : watermark;
        Long n = jdbc.queryForObject(
            """
            SELECT count(*) FROM slack_message
            WHERE workspace_id = ? AND slack_channel_id = ?
              AND (slack_thread_ts = ? OR slack_ts = ?) AND deleted_at IS NULL
              AND slack_ts > ?
            """,
            Long.class,
            workspaceId,
            channelId,
            threadTs,
            threadTs,
            floor
        );
        return n == null ? 0 : n;
    }

    @Override
    public void markReviewed(long workspaceId, long threadId, String lastTs) {
        jdbc.update(
            "UPDATE slack_thread SET last_reviewed_ts = ? WHERE workspace_id = ? AND id = ?",
            lastTs,
            workspaceId,
            threadId
        );
    }

    private static long[] readLongArray(@Nullable Array array) {
        if (array == null) {
            return new long[0];
        }
        try {
            Object raw = array.getArray();
            if (raw instanceof Long[] boxed) {
                long[] out = new long[boxed.length];
                for (int i = 0; i < boxed.length; i++) {
                    out[i] = boxed[i] == null ? 0 : boxed[i];
                }
                return out;
            }
            if (raw instanceof Number[] nums) {
                long[] out = new long[nums.length];
                for (int i = 0; i < nums.length; i++) {
                    out[i] = nums[i] == null ? 0 : nums[i].longValue();
                }
                return out;
            }
            return new long[0];
        } catch (SQLException e) {
            return new long[0];
        }
    }
}
