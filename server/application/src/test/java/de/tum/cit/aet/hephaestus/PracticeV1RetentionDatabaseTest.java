package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("database")
class PracticeV1RetentionDatabaseTest {
    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldKeepProductFactsAndPricedReviewUsageAfterRuntimeDeletion(String context) throws Exception {
        var database = PracticeV1CutoverMigrationTest.releasedDatabase(context);
        PracticeV1CutoverMigrationTest.execute(database, """
            INSERT INTO llm_usage_event(id,workspace_id,job_type,source_id,source_type,source_attempt,
                model,input_tokens,output_tokens,total_calls,cost_usd,occurred_at,pricing_state)
            VALUES('00000000-0000-0000-0000-000000996181',992101,'PULL_REQUEST_REVIEW',
                '00000000-0000-0000-0000-000000992111','AGENT_JOB',0,'released-model',100,20,1,
                0.125,'2026-01-01T00:01:00Z','PRICED');
            SELECT setval(pg_get_serial_sequence('account','id'),2000000,true);
            SELECT setval(pg_get_serial_sequence('practice','id'),2000000,true);
            SELECT setval(pg_get_serial_sequence('practice_revision','id'),2000000,true);
            """);
        PracticeV1CutoverMigrationTest.upgrade(database, context);
        PracticeV1CutoverMigrationTest.execute(
                database, "DELETE FROM review_job WHERE id='00000000-0000-0000-0000-000000992111'");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT source_type || '|' || source_id || '|' || review_id || '|' || cost_usd FROM"
                                + " llm_usage_event WHERE id='00000000-0000-0000-0000-000000996181'"))
                .containsExactly(
                        "REVIEW_JOB|00000000-0000-0000-0000-000000992111|00000000-0000-0000-0000-000000992111|0.125000");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT (job_id IS NULL)::text || '|' || state || '|' || result_id FROM review_item"
                                + " WHERE review_id='00000000-0000-0000-0000-000000992111'"))
                .containsExactly("true|JUDGED|00000000-0000-0000-0000-000000992141");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT (ended_at IS NULL)::text FROM result WHERE id='00000000-0000-0000-0000-000000992141'"))
                .containsExactly("true");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT external_ref || '|' || state FROM feedback_copy WHERE"
                                + " id='00000000-0000-0000-0000-000000992171'"))
                .containsExactly("provider:123|POSTED");
        assertThatThrownBy(() -> PracticeV1CutoverMigrationTest.execute(
                        database, "DELETE FROM review WHERE id='00000000-0000-0000-0000-000000992111'"))
                .isInstanceOf(SQLException.class);
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT (nextval(pg_get_serial_sequence('account','id'))>2000000 AND"
                                + " nextval(pg_get_serial_sequence('practice','id'))>2000000 AND"
                                + " nextval(pg_get_serial_sequence('practice_version','id'))>2000000)::text"))
                .containsExactly("true");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldRequireProductCleanupBeforeMembershipAndKeepTheSharedProviderRows(String context) throws Exception {
        var database = PracticeV1CutoverMigrationTest.releasedDatabase(context);
        PracticeV1CutoverMigrationTest.upgrade(database, context);
        assertThatThrownBy(() -> PracticeV1CutoverMigrationTest.execute(
                        database, "DELETE FROM membership WHERE workspace_id=992101 AND account_id=992106"))
                .isInstanceOf(SQLException.class);
        PracticeV1CutoverMigrationTest.execute(database, """
            DELETE FROM feedback WHERE workspace_id=992101 AND developer_id=992106;
            DELETE FROM review_item WHERE workspace_id=992101 AND result_id IN
                (SELECT id FROM result WHERE workspace_id=992101 AND developer_id=992106);
            DELETE FROM result WHERE workspace_id=992101 AND developer_id=992106;
            DELETE FROM chat_thread WHERE workspace_id=992101 AND account_id=992106;
            DELETE FROM membership WHERE workspace_id=992101 AND account_id=992106;
            """);
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT (SELECT count(*) FROM problem WHERE workspace_id=992101)::text || '|' ||"
                                + " (SELECT count(*) FROM result_mark WHERE workspace_id=992101) || '|' || (SELECT"
                                + " count(*) FROM feedback_copy WHERE workspace_id=992101) || '|' || (SELECT"
                                + " count(*) FROM chat_message WHERE workspace_id=992101)"))
                .containsExactly("0|0|0|0");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT (SELECT count(*) FROM issue WHERE id=992109)::text || '|' || (SELECT count(*)"
                                + " FROM repository WHERE id=992108) || '|' || (SELECT count(*) FROM membership"
                                + " WHERE workspace_id=992201)"))
                .containsExactly("1|1|1");
    }
}
