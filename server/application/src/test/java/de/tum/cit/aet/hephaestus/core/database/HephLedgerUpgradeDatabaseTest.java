package de.tum.cit.aet.hephaestus.core.database;

import static de.tum.cit.aet.hephaestus.core.database.PracticeMigrationFixture.column;
import static de.tum.cit.aet.hephaestus.core.database.PracticeMigrationFixture.execute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import java.sql.SQLException;
import java.util.Objects;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The upgrade that replaces the Heph chat tables with the ledger keeps every conversation copy and its feedback, and
 * binds each Heph attempt measurement to its own turn.
 */
@Tag("database")
class HephLedgerUpgradeDatabaseTest {
    private static final String HEPH_CHANGELOG = "1792397538973_changelog.xml";
    private static final String TURN = "00000000-0000-0000-0000-000000996311";
    private static final String SIBLING_TURN = "00000000-0000-0000-0000-000000996312";
    private static final String MISSING_TURN = "00000000-0000-0000-0000-000000996319";
    private static final String TURN_REFERENCE = "sfk_mentor_turn_attempt_usage_turn_workspace";

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldKeepConversationCopiesWithoutTheDroppedChatHistory(String context) throws Exception {
        var database = PracticeMigrationFixture.releasedDatabase(context);
        PracticeMigrationFixture.upgradeBefore(database, context, HEPH_CHANGELOG);
        execute(database, """
            INSERT INTO chat_thread(id,workspace_id,account_id,surface,created_at)
            SELECT '00000000-0000-0000-0000-000000996201'::uuid,workspace_id,developer_id,'WEB','2026-01-04T00:00:00Z'::timestamptz
            FROM feedback WHERE id='00000000-0000-0000-0000-000000992193';
            INSERT INTO chat_message(id,workspace_id,thread_id,role,status,parts,created_at)
            VALUES('00000000-0000-0000-0000-000000996202',992101,'00000000-0000-0000-0000-000000996201',
                'ASSISTANT','completed','[{"type":"text","text":"Shown words."}]','2026-01-04T00:00:00Z');
            INSERT INTO chat_message_vote(message_id,created_at,is_upvoted,updated_at)
            VALUES('00000000-0000-0000-0000-000000996202','2026-01-04T00:03:00Z',true,'2026-01-04T00:03:00Z');
            INSERT INTO mentor_slack_thread(id,workspace_id,chat_thread_id,slack_team_id,slack_channel_id,
                slack_thread_ts,slack_user_id)
            VALUES('00000000-0000-0000-0000-000000996203',992101,'00000000-0000-0000-0000-000000996201',
                'T996','D996','996.1','U996');
            INSERT INTO feedback(id,workspace_id,review_id,item_id,developer_id,destination,state,body,created_at,delivered_at)
            SELECT '00000000-0000-0000-0000-000000996211'::uuid,workspace_id,review_id,item_id,developer_id,
                'CONVERSATION','READY','Fenced brief.','2026-01-04T00:00:00Z'::timestamptz,NULL::timestamptz
            FROM feedback WHERE id='00000000-0000-0000-0000-000000992193'
            UNION ALL
            SELECT '00000000-0000-0000-0000-000000996212'::uuid,workspace_id,review_id,item_id,developer_id,
                'CONVERSATION','DELIVERED','Delivered brief.','2026-01-04T00:00:00Z'::timestamptz,
                '2026-01-04T00:02:00Z'::timestamptz
            FROM feedback WHERE id='00000000-0000-0000-0000-000000992193';
            INSERT INTO feedback_copy(id,workspace_id,feedback_id,kind,position,state,body,write_started_at,
                write_key,chat_message_id,posted_at)
            VALUES('00000000-0000-0000-0000-000000996221',992101,'00000000-0000-0000-0000-000000996211','CHAT_TURN',0,
                'WRITE_STARTED',NULL,'2026-01-04T00:01:00Z','00000000-0000-0000-0000-000000996221',
                '00000000-0000-0000-0000-000000996202',NULL),
            ('00000000-0000-0000-0000-000000996222',992101,'00000000-0000-0000-0000-000000996212','CHAT_TURN',0,
                'POSTED','Shown words.','2026-01-04T00:01:00Z','00000000-0000-0000-0000-000000996222',
                '00000000-0000-0000-0000-000000996202','2026-01-04T00:02:00Z');
            """);

        PracticeMigrationFixture.upgrade(database, context);

        assertThat(column(database, """
            SELECT count(*)::text FROM information_schema.tables WHERE table_schema='public'
                AND table_name IN ('chat_thread','chat_message','chat_message_vote','mentor_slack_thread')
            """)).containsExactly("0");
        assertThat(column(database, """
            SELECT right(c.id::text,6) || '|' || c.state || '|' || coalesce(c.body,'none') || '|'
                || (c.write_started_at='2026-01-04T00:01:00Z'::timestamptz) || '|'
                || coalesce((c.posted_at='2026-01-04T00:02:00Z'::timestamptz)::text,'none') || '|'
                || (c.mentor_entry_id IS NULL AND c.mentor_turn_id IS NULL) || '|'
                || f.state || '|' || f.body
            FROM feedback_copy c JOIN feedback f ON f.id=c.feedback_id
            WHERE c.id IN ('00000000-0000-0000-0000-000000996221','00000000-0000-0000-0000-000000996222')
            ORDER BY c.id
            """))
                .containsExactly(
                        "996221|WRITE_STARTED|none|true|none|true|READY|Fenced brief.",
                        "996222|POSTED|Shown words.|true|true|true|DELIVERED|Delivered brief.");
        assertThat(column(database, """
            SELECT count(*)::text FROM pg_trigger
            WHERE tgname='feedback_copy_fence_monotonic' AND tgrelid='feedback_copy'::regclass AND NOT tgisinternal
            """)).containsExactly("1");
        assertThat(column(database, """
            SELECT conname || '|' || conrelid::regclass || '|' || confrelid::regclass || '|' || cardinality(conkey)
            FROM pg_constraint WHERE contype='f' AND conname IN
                ('sfk_feedback_copy_mentor_entry','sfk_feedback_copy_mentor_turn','sfk_mentor_entry_conversation')
            ORDER BY conname
            """))
                .containsExactly(
                        "sfk_feedback_copy_mentor_entry|feedback_copy|mentor_entry|2",
                        "sfk_feedback_copy_mentor_turn|feedback_copy|mentor_turn|2",
                        "sfk_mentor_entry_conversation|mentor_entry|mentor_conversation|2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldKeepEachAttemptMeasurementOnItsOwnTurnAndRemoveItOnlyWithThatTurn(String context) throws Exception {
        var database = PracticeMigrationFixture.migratedDatabase(context);
        assertForeignKey(
                database,
                TURN_REFERENCE,
                "FOREIGN KEY (turn_id, workspace_id) REFERENCES mentor_turn(id, workspace_id) ON DELETE CASCADE");
        assertForeignKey(
                database,
                "sfk_mentor_turn_attempt_usage_workspace",
                "FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE RESTRICT");
        var primaryKeys = column(database, """
            SELECT conname FROM pg_constraint
            WHERE conrelid='public.mentor_turn_attempt_usage'::regclass AND contype='p'
            """);
        assertThat(primaryKeys).hasSize(1);
        String primaryKey = Objects.requireNonNull(primaryKeys.getFirst());
        assertThat(column(database, """
            SELECT string_agg(a.attname,',' ORDER BY a.attname) FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=ANY(c.conkey)
            WHERE c.conrelid='public.mentor_turn_attempt_usage'::regclass AND c.contype='p'
            """)).containsExactly("attempt,turn_id");
        // Both seeded workspaces exist, so only the turn reference can refuse a measurement in the other workspace.
        assertThat(column(database, "SELECT count(*)::text FROM workspace WHERE id IN (992101,992201)"))
                .containsExactly("2");
        execute(database, """
            INSERT INTO mentor_conversation(id,workspace_id,account_id,created_at,last_activity_at)
            SELECT '00000000-0000-0000-0000-000000996301'::uuid,workspace_id,developer_id,
                '2026-01-05T00:00:00Z'::timestamptz,'2026-01-05T00:00:00Z'::timestamptz
            FROM feedback WHERE id='00000000-0000-0000-0000-000000992193' AND workspace_id=992101;
            INSERT INTO mentor_turn(id,workspace_id,conversation_id,request_id,status,surface,created_at)
            VALUES('00000000-0000-0000-0000-000000996311',992101,'00000000-0000-0000-0000-000000996301',
                'native-usage-996311','DONE','APP','2026-01-05T00:00:00Z'),
            ('00000000-0000-0000-0000-000000996312',992101,'00000000-0000-0000-0000-000000996301',
                'native-usage-996312','DONE','APP','2026-01-05T00:01:00Z');
            """);

        execute(database, attemptInsert(TURN, 0, 992101) + ";" + attemptInsert(SIBLING_TURN, 0, 992101) + ";" + """
            INSERT INTO mentor_turn_attempt_usage(turn_id,attempt,workspace_id,model,funding_source,pricing_state,
                applied_price_id,applied_workspace_model_id,occurred_at,calls,input_tokens)
            VALUES('00000000-0000-0000-0000-000000996311',1,992101,'frozen-model','WORKSPACE','PRICED',
                996398,996399,'2026-01-05T00:02:00Z',2,40)
            """);

        // An attempt that still runs has no date; the frozen price ids are history, not references.
        assertThat(column(database, """
            SELECT attempt || '|' || model || '|' || funding_source || '|' || pricing_state || '|' || calls || '|'
                || input_tokens || '|' || output_tokens || '|' || cache_read_tokens || '|' || cache_write_tokens || '|'
                || reasoning_tokens || '|' || coalesce((occurred_at='2026-01-05T00:02:00Z'::timestamptz)::text,'none')
                || '|' || coalesce(applied_price_id::text,'none') || '|'
                || coalesce(applied_workspace_model_id::text,'none') || '|'
                || (applied_per_1m_input_usd IS NULL AND applied_per_1m_output_usd IS NULL
                    AND applied_per_1m_cache_read_usd IS NULL AND applied_per_1m_cache_write_usd IS NULL)
            FROM mentor_turn_attempt_usage WHERE turn_id='00000000-0000-0000-0000-000000996311' ORDER BY attempt
            """))
                .containsExactly(
                        "0|frozen-model|INSTANCE|NO_CHARGE|0|0|0|0|0|0|none|none|none|true",
                        "1|frozen-model|WORKSPACE|PRICED|2|40|0|0|0|0|true|996398|996399|true");

        assertRefused(database, attemptInsert(MISSING_TURN, 0, 992101), "23503", TURN_REFERENCE);
        assertRefused(database, attemptInsert(TURN, 2, 992201), "23503", TURN_REFERENCE);
        assertRefused(database, attemptInsert(TURN, 0, 992101), "23505", primaryKey);
        assertThat(column(database, """
            SELECT right(turn_id::text,6) || ':' || attempt || ':' || workspace_id FROM mentor_turn_attempt_usage
            WHERE turn_id IN ('00000000-0000-0000-0000-000000996311','00000000-0000-0000-0000-000000996312',
                '00000000-0000-0000-0000-000000996319')
            ORDER BY turn_id, attempt
            """)).containsExactly("996311:0:992101", "996311:1:992101", "996312:0:992101");

        execute(database, """
            INSERT INTO llm_usage_event(id,workspace_id,job_type,source_type,source_id,source_attempt,model,
                input_tokens,total_calls,cost_usd,occurred_at,pricing_state,usage_provenance,measurement_verification)
            VALUES('00000000-0000-0000-0000-000000996321',992101,'MENTOR_TURN','MENTOR_TURN',
                '00000000-0000-0000-0000-000000996311',1,'frozen-model',40,2,0.125,'2026-01-05T00:02:00Z','PRICED',
                'PROXY','VERIFIED')
            """);
        String ledger =
                "SELECT to_jsonb(e)::text FROM llm_usage_event e WHERE id='00000000-0000-0000-0000-000000996321'";
        String sibling = """
            SELECT to_jsonb(t)::text || '|' || to_jsonb(u)::text FROM mentor_turn t
            JOIN mentor_turn_attempt_usage u ON (u.turn_id,u.workspace_id)=(t.id,t.workspace_id)
            WHERE t.id='00000000-0000-0000-0000-000000996312'
            """;
        var ledgerBefore = column(database, ledger);
        var siblingBefore = column(database, sibling);
        assertThat(ledgerBefore).hasSize(1);
        assertThat(siblingBefore).hasSize(1);

        execute(database, "DELETE FROM mentor_turn WHERE id='" + TURN + "' AND workspace_id=992101");

        assertThat(column(database, "SELECT count(*)::text FROM mentor_turn WHERE id='" + TURN + "'"))
                .containsExactly("0");
        assertThat(column(
                        database, "SELECT count(*)::text FROM mentor_turn_attempt_usage WHERE turn_id='" + TURN + "'"))
                .containsExactly("0");
        assertThat(column(database, sibling)).isEqualTo(siblingBefore);
        assertThat(column(database, ledger)).isEqualTo(ledgerBefore);
    }

    private static String attemptInsert(String turnId, int attempt, long workspaceId) {
        return "INSERT INTO mentor_turn_attempt_usage(turn_id,attempt,workspace_id,model,funding_source,pricing_state)"
                + " VALUES ('" + turnId + "'," + attempt + "," + workspaceId
                + ",'frozen-model','INSTANCE','NO_CHARGE')";
    }

    private static void assertForeignKey(TestDatabase database, String name, String definition) {
        assertThat(column(
                        database,
                        "SELECT convalidated::text || ':' || pg_get_constraintdef(oid) FROM pg_constraint WHERE"
                                + " conrelid='public.mentor_turn_attempt_usage'::regclass AND contype='f' AND conname='"
                                + name + "'"))
                .containsExactly("true:" + definition);
    }

    private static void assertRefused(TestDatabase database, String sql, String sqlState, String constraint) {
        assertThatThrownBy(() -> execute(database, sql))
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, failure -> {
                    assertThat(failure.getSQLState()).isEqualTo(sqlState);
                    assertThat(failure.getMessage()).contains(constraint);
                });
    }
}
