package de.tum.cit.aet.hephaestus.integration.slack;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared raw-JDBC seed helpers for the Slack conversation SPI integration tests
 * ({@code SlackConversationProjectorIntegrationTest}, {@code ConversationThreadDetectionIntegrationTest}).
 *
 * <p>Two things are single-sourced here:
 *
 * <ul>
 *   <li>{@link #ensureUnmappedSlackThreadColumns()} — the {@code participant_member_ids bigint[]} + GIN and
 *       {@code last_reviewed_ts VARCHAR(32)} DDL. These columns are deliberately UNMAPPED on the {@code SlackThread}
 *       entity (raw-JDBC-only — changelog changesets -12/-13), so the entity-derived integration profile
 *       ({@code ddl-auto: create}, Liquibase off) does not build them. Both SPI ITs used to carry a verbatim copy of
 *       this DDL; centralising it means the two copies cannot drift from each other. That the hand-rolled
 *       {@code bigint[]}/{@code VARCHAR(32)} shape actually matches the production Liquibase migration is proven
 *       independently by {@code SlackConversationSchemaContractIntegrationTest} against the real schema — that
 *       contract test is what makes keeping this fast, hand-rolled DDL safe.</li>
 *   <li>{@link #seedChannel}/{@link #seedThread}/{@link #seedMessage} — the raw {@code INSERT}s the projector and
 *       detection scans read over. Superset signatures so both callers share one SQL string per table.</li>
 * </ul>
 *
 * <p>The seed helpers are also reused by the real-Liquibase {@code SlackConversationSchemaContractIntegrationTest}
 * (where {@link #ensureUnmappedSlackThreadColumns()} is deliberately NOT called — those columns already exist as the
 * real migrated types).
 */
public final class SlackConversationTestSupport {

    private final JdbcTemplate jdbc;

    public SlackConversationTestSupport(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Idempotently add the raw-JDBC-only {@code slack_thread} columns to the entity-derived test schema, mirroring
     * the production Liquibase migration. No-op on the real Liquibase schema (the columns already exist).
     */
    public void ensureUnmappedSlackThreadColumns() {
        jdbc.execute(
            "ALTER TABLE slack_thread ADD COLUMN IF NOT EXISTS participant_member_ids BIGINT[] NOT NULL DEFAULT '{}'"
        );
        jdbc.execute("ALTER TABLE slack_thread ADD COLUMN IF NOT EXISTS last_reviewed_ts VARCHAR(32)");
        jdbc.execute(
            "CREATE INDEX IF NOT EXISTS idx_slack_thread_participants ON slack_thread USING GIN (participant_member_ids)"
        );
    }

    /** Seed one monitored channel with an explicit consent state. */
    public void seedChannel(long workspaceId, String channelId, String consentState) {
        jdbc.update(
            "INSERT INTO slack_monitored_channel (workspace_id, slack_team_id, slack_channel_id, channel_name, consent_state, created_at) " +
                "VALUES (?, 'T1', ?, 'engineering', ?, now())",
            workspaceId,
            channelId,
            consentState
        );
    }

    /**
     * Seed a thread aggregate with an explicit participant member-id set (text array literal → {@code bigint[]}).
     *
     * @param participantArrayLiteral e.g. {@code "{100,101}"}
     */
    public void seedThread(
        long workspaceId,
        String channelId,
        String threadTs,
        String lastTs,
        int messageCount,
        String participantArrayLiteral
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
            participantArrayLiteral
        );
    }

    /** Seed one non-tombstoned message (author {@code U1}). */
    public void seedMessage(long workspaceId, String channelId, String ts, String threadTs, String text) {
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
}
