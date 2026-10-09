package de.tum.cit.aet.hephaestus.core.database;

import static de.tum.cit.aet.hephaestus.core.database.PracticeMigrationFixture.column;
import static de.tum.cit.aet.hephaestus.core.database.PracticeMigrationFixture.execute;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The upgrade that replaces the Heph chat tables with the ledger keeps every conversation copy and its feedback. */
@Tag("database")
class HephLedgerUpgradeDatabaseTest {
    private static final String HEPH_CHANGELOG = "1792397538973_changelog.xml";

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
}
