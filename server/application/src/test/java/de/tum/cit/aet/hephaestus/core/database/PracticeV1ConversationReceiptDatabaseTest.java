package de.tum.cit.aet.hephaestus.core.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import java.sql.SQLException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("database")
class PracticeV1ConversationReceiptDatabaseTest {
    private static final String COPY = "00000000-0000-0000-0000-000000998172";
    private static final String MESSAGE = "00000000-0000-0000-0000-000000998192";

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldBindTheClaimAndRecordOnlyItsCompletedAssistantWords(String context) throws Exception {
        var database = prepared(context);
        reject(
                database,
                "UPDATE feedback_copy SET state='WRITE_STARTED',write_started_at='2026-02-01T00:00:00Z',"
                        + "chat_message_id='00000000-0000-0000-0000-000000998194' WHERE id='" + COPY + "'",
                "23514");
        claim(database);
        var digest = PracticeMigrationFixture.column(
                database, "SELECT feedback_content_digest('00000000-0000-0000-0000-000000998161')");
        reject(
                database,
                "UPDATE feedback_copy SET chat_message_id='00000000-0000-0000-0000-000000978052'" + " WHERE id='" + COPY
                        + "'",
                "55000");
        reject(database, "UPDATE feedback_copy SET chat_message_id=NULL WHERE id='" + COPY + "'", "55000");
        reject(database, "UPDATE feedback_copy SET body='Prepared brief.' WHERE id='" + COPY + "'", "55000");
        reject(database, receipt("Actual shown words."), "23514");
        PracticeMigrationFixture.execute(
                database,
                "UPDATE chat_message SET status='completed',parts='[{\"type\":\"text\",\"text\":\"Actual shown"
                        + " \"},{\"type\":\"text\",\"text\":\"words.\"}]'::jsonb WHERE id='" + MESSAGE + "'");
        reject(database, receipt("Prepared brief."), "23514");
        reject(
                database,
                "UPDATE feedback_copy SET state='POSTED',posted_at='2026-02-01T00:01:00Z'" + " WHERE id='" + COPY + "'",
                "23514");
        reject(
                database,
                "UPDATE feedback_copy SET state='POSTED',body='Actual shown words.',"
                        + "posted_at='2026-01-01T00:01:00Z' WHERE id='" + COPY + "'",
                "55000");
        // A failed transport can later supply confirmation, without another claim or a new fence.
        PracticeMigrationFixture.execute(database, "UPDATE feedback_copy SET state='FAILED' WHERE id='" + COPY + "'");
        PracticeMigrationFixture.execute(database, receipt("Actual shown words."));
        assertThat(PracticeMigrationFixture.column(
                        database,
                        "SELECT state || '|' || body || '|' || chat_message_id || '|' ||"
                                + " (write_started_at='2026-02-01T00:00:00Z'::timestamptz)::text"
                                + " FROM feedback_copy WHERE id='" + COPY + "'"))
                .containsExactly("POSTED|Actual shown words.|" + MESSAGE + "|true");
        assertThat(PracticeMigrationFixture.column(
                        database, "SELECT feedback_content_digest('00000000-0000-0000-0000-000000998161')"))
                .isEqualTo(digest);
        reject(database, "UPDATE feedback_copy SET body='Different words.' WHERE id='" + COPY + "'", "55000");
        reject(database, "UPDATE feedback_copy SET body=NULL WHERE id='" + COPY + "'", "55000");
        reject(
                database,
                "UPDATE feedback_copy SET write_started_at=write_started_at+interval '1 second'" + " WHERE id='" + COPY
                        + "'",
                "55000");
        reject(database, "UPDATE feedback_copy SET write_key='another-key' WHERE id='" + COPY + "'", "55000");
        reject(database, "UPDATE feedback_copy SET write_marker='another-marker' WHERE id='" + COPY + "'", "55000");
        reject(
                database,
                "UPDATE feedback_copy SET posted_at=posted_at+interval '1 second' WHERE id='" + COPY + "'",
                "55000");
        assertThat(PracticeMigrationFixture.column(
                        database,
                        "SELECT count(*)::text FROM feedback_send WHERE"
                                + " feedback_id='00000000-0000-0000-0000-000000998161'"))
                .containsExactly("0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldKeepReceiptCustodyWhenTheBoundMessageIsDeleted(String context) throws Exception {
        var database = prepared(context);
        claim(database);
        PracticeMigrationFixture.execute(
                database,
                "UPDATE chat_message SET status='completed',"
                        + "parts='[{\"type\":\"text\",\"text\":\"Actual shown words.\"}]'::jsonb WHERE id='" + MESSAGE
                        + "'");
        PracticeMigrationFixture.execute(database, receipt("Actual shown words."));
        PracticeMigrationFixture.execute(database, "DELETE FROM chat_message WHERE id='" + MESSAGE + "'");
        assertThat(PracticeMigrationFixture.column(
                        database,
                        "SELECT (chat_message_id IS NULL)::text || '|' || state || '|' || body || '|' ||"
                                + " (write_started_at IS NOT NULL AND posted_at IS NOT NULL)::text"
                                + " FROM feedback_copy WHERE id='" + COPY + "'"))
                .containsExactly("true|POSTED|Actual shown words.|true");
        reject(
                database,
                "UPDATE feedback_copy SET chat_message_id='00000000-0000-0000-0000-000000978052'" + " WHERE id='" + COPY
                        + "'",
                "55000");
        reject(
                database,
                "UPDATE feedback_copy SET write_started_at=NULL,state='PLANNED' WHERE id='" + COPY + "'",
                "55000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldKeepOnWorkWordsAndAnchorsImmutableAfterTheFence(String context) throws Exception {
        var database = prepared(context);
        PracticeMigrationFixture.execute(database, """
            INSERT INTO feedback_copy(id,workspace_id,feedback_id,kind,position,body,path,start_line,end_line,side,state,write_key,write_marker)
            VALUES('00000000-0000-0000-0000-000000998173',978001,'00000000-0000-0000-0000-000000978041',
                'LINE_NOTE',1,'Explain the retry.','retry.ts',10,12,'NEW','PLANNED','fixed-note-key','fixed-note-marker');
            UPDATE feedback_copy SET state='WRITE_STARTED',write_started_at='2026-02-01T00:00:00Z'
            WHERE id='00000000-0000-0000-0000-000000998173';
            """);
        for (String change : new String[] {
            "body='Other words.'",
            "path='other.ts'",
            "start_line=11",
            "end_line=13",
            "side='OLD'",
            "write_key='other-key'",
            "write_marker='other-marker'"
        }) {
            reject(
                    database,
                    "UPDATE feedback_copy SET " + change + " WHERE id='00000000-0000-0000-0000-000000998173'",
                    "55000");
        }
        PracticeMigrationFixture.execute(
                database,
                "UPDATE feedback_copy SET kind='FILE_NOTE',"
                        + "state='POSTED',posted_at='2026-02-01T00:01:00Z',external_ref='confirmed-note'"
                        + " WHERE id='00000000-0000-0000-0000-000000998173'");
    }

    private static TestDatabase prepared(String context) throws Exception {
        var database = NativePracticeFixture.feedbackParent(context);
        PracticeMigrationFixture.execute(database, """
            INSERT INTO feedback(id,workspace_id,review_id,item_id,developer_id,destination,state,body)
            SELECT '00000000-0000-0000-0000-000000998161',workspace_id,review_id,item_id,developer_id,
                'CONVERSATION','READY','Prepared brief.' FROM feedback WHERE id='00000000-0000-0000-0000-000000978044';
            INSERT INTO feedback_copy(id,workspace_id,feedback_id,kind,position,state,write_key,write_marker)
            VALUES('00000000-0000-0000-0000-000000998172',978001,'00000000-0000-0000-0000-000000998161',
                'CHAT_TURN',0,'PLANNED','fixed-chat-key','fixed-chat-marker');
            INSERT INTO chat_message(id,workspace_id,thread_id,role,status,parts,created_at)
            VALUES('00000000-0000-0000-0000-000000998192',978001,'00000000-0000-0000-0000-000000978051',
                'ASSISTANT','in_flight','[]'::jsonb,'2026-02-01T00:00:00Z');
            INSERT INTO chat_thread(id,workspace_id,account_id,surface,created_at)
            SELECT '00000000-0000-0000-0000-000000998193',978001,account_id,'WEB','2026-02-01T00:00:00Z'
                FROM membership WHERE workspace_id=978001 AND account_id<>978003 LIMIT 1;
            INSERT INTO chat_message(id,workspace_id,thread_id,role,status,parts,created_at)
            VALUES('00000000-0000-0000-0000-000000998194',978001,'00000000-0000-0000-0000-000000998193',
                'ASSISTANT','completed','[{"type":"text","text":"Another developer."}]'::jsonb,'2026-02-01T00:00:00Z');
            """);
        return database;
    }

    private static void claim(TestDatabase database) throws SQLException {
        PracticeMigrationFixture.execute(
                database,
                "UPDATE feedback_copy SET state='WRITE_STARTED',"
                        + "write_started_at='2026-02-01T00:00:00Z',chat_message_id='" + MESSAGE + "' WHERE id='" + COPY
                        + "'");
    }

    private static String receipt(String body) {
        return "UPDATE feedback_copy SET state='POSTED',posted_at='2026-02-01T00:01:00Z',body='" + body + "' WHERE id='"
                + COPY + "'";
    }

    private static void reject(TestDatabase database, String sql, String sqlState) {
        assertThatThrownBy(() -> PracticeMigrationFixture.execute(database, sql))
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        exception -> assertThat(exception.getSQLState()).isEqualTo(sqlState));
    }
}
