package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Replays the released prefix, writes released rows, and applies the one atomic 1.0 cutover. */
@Tag("database")
class PracticeV1CutoverMigrationTest {
    private static final String BRANCH_FILE = "1791486276211_changelog.xml";
    private static final String RESULT = "00000000-0000-0000-0000-000000992141";
    private static final String FEEDBACK = "00000000-0000-0000-0000-000000992161";

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldPreserveReleasedProofAndStopOldExecutionWithoutRepeatingDelivery(String context) throws Exception {
        var database = releasedDatabase(context);
        upgrade(database, context);
        try (var input =
                Objects.requireNonNull(getClass().getResourceAsStream("/db/practice-cutover/assert-migrated.sql"))) {
            execute(database, new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertThat(column(
                        database,
                        "SELECT outcome || '|' || severity || '|' || evidence_version || '|' || (ended_at IS NULL)"
                                + " FROM result WHERE id='"
                                + RESULT + "'"))
                .containsExactly("NOT_MET|TRIVIAL|0|true");
        assertThat(column(
                        database,
                        "SELECT (p.id<>r.id)::text || '|' || (p.citations->0 ? 'sourcePath') || '|' || (p.basis IS"
                                + " NULL) FROM problem p JOIN result r ON r.id=p.result_id WHERE r.id='"
                                + RESULT + "'"))
                .containsExactly("true|true|true");
        assertThat(column(
                        database,
                        "SELECT count(*)::text FROM result r LEFT JOIN item i ON"
                                + " (i.id,i.workspace_id)=(r.item_id,r.workspace_id) WHERE r.id='"
                                + RESULT + "' AND i.id IS NULL"))
                .containsExactly("0");
        assertThat(column(database, "SELECT state || '|' || body FROM feedback WHERE id='" + FEEDBACK + "'"))
                .containsExactly("DELIVERED|Say why.");
        assertThat(column(database, "SELECT checks::text FROM feedback WHERE id='" + FEEDBACK + "'"))
                .containsExactly("{}");
        assertThat(column(
                        database,
                        "SELECT checks->'MIGRATION'->'feedback'->'" + FEEDBACK
                                + "'->'MIGRATION'->>'deliveryState' FROM review WHERE"
                                + " id='00000000-0000-0000-0000-000000992111'"))
                .containsExactly("DELIVERED");
        assertThat(column(
                        database,
                        "SELECT id::text || '|' || external_ref || '|' || state FROM feedback_copy WHERE feedback_id='"
                                + FEEDBACK + "'"))
                .containsExactly("00000000-0000-0000-0000-000000992171|provider:123|POSTED");
        assertThat(column(
                        database,
                        "SELECT count(*)::text FROM feedback_send WHERE feedback_id='" + FEEDBACK
                                + "' AND state='PENDING'"))
                .containsExactly("0");
        assertThat(column(
                        database,
                        "SELECT f.state || '|' || (f.delivered_at IS NULL) || '|' || c.state || '|' || c.body"
                                + " FROM feedback f JOIN feedback_copy c ON c.feedback_id=f.id WHERE"
                                + " f.id='00000000-0000-0000-0000-000000992193'"))
                .containsExactly("UNCONFIRMED|true|WITHHELD|Words shown for this result.");
        assertThat(column(
                        database,
                        "SELECT count(*)::text FROM feedback_send WHERE"
                                + " feedback_id='00000000-0000-0000-0000-000000992193'"))
                .containsExactly("0");
        assertThat(column(
                        database,
                        "SELECT workspace_id || '|' || (parts->0->>'text') || '|' || (parts->1->>'type') ||"
                                + " '|' || (parts->1->'data'->>'resultId') || '|' || (parts->1->'data' ?"
                                + " 'observationId') FROM chat_message WHERE"
                                + " id='00000000-0000-0000-0000-000000992192'"))
                .containsExactly(
                        "992101|Unrelated assistant text.|data-result|00000000-0000-0000-0000-000000992141|false");
        assertThat(column(
                        database,
                        "SELECT a.status || '|' || count(m.workspace_id) FROM identity i JOIN account a ON"
                                + " a.id=i.account_id JOIN membership m ON m.account_id=a.id WHERE"
                                + " i.provider_id=992102 AND i.provider_user_key='104' GROUP BY a.status"))
                .containsExactly("UNCLAIMED|2");
        assertThat(column(
                        database,
                        "SELECT count(*)::text FROM identity WHERE provider_id=992102 AND provider_user_key='105'"))
                .containsExactly("0");
        assertThat(column(
                        database,
                        "SELECT status || '|' || processing_status || '|' || cancellation_reason || '|' ||"
                                + " (worker_id IS NULL AND job_token_hash IS NULL) FROM review_job WHERE"
                                + " id='00000000-0000-0000-0000-000000992112'"))
                .containsExactly("CANCELLED|DONE|CONTRACT_CHANGED|true");
        assertThat(column(
                        database,
                        "SELECT status || '|' || processing_status FROM review_job WHERE"
                                + " id='00000000-0000-0000-0000-000000992113'"))
                .containsExactly("COMPLETED|FAILED");
        assertThat(column(
                        database,
                        "SELECT i.kind || '|' || i.source_id || '|' || (i.removed_at IS NOT NULL) || '|' ||"
                                + " p.kind || '|' || p.source_id || '|' || (i.workspace_id=p.workspace_id) FROM"
                                + " review r JOIN item i ON i.id=r.item_id JOIN item p ON p.id=i.parent_id WHERE"
                                + " r.id='00000000-0000-0000-0000-000000992112'"))
                .containsExactly("scm.pull_request_review|992119|true|scm.pull_request|992109|true");
        assertThat(column(
                        database,
                        "SELECT e.name || '|' || e.actor_user_id || '|' || (e.item_id=r.item_id) FROM"
                                + " item_event e JOIN review r ON r.id='00000000-0000-0000-0000-000000992112'"
                                + " WHERE e.id='00000000-0000-0000-0000-000000992181'"))
                .containsExactly("scm.pull_request_review.submitted|992104|true");
        assertThat(column(
                        database,
                        "SELECT count(*)::text FROM practice_version WHERE id=992131 AND"
                                + " definition->'reads'->>'contract'='1.3.0'"))
                .containsExactly("1");
        assertThat(column(
                        database,
                        "SELECT v.definition->'reads'->>'contract' FROM practice p JOIN practice_version v ON"
                                + " v.id=p.current_version_id WHERE p.id=992121"))
                .containsExactly("2.0.0");
        assertThat(column(
                        database,
                        "SELECT count(*)::text FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid WHERE"
                                + " c.relname='uk_result_current' AND i.indisvalid AND i.indnullsnotdistinct"))
                .containsExactly("1");
        assertThat(
                        column(
                                database,
                                "SELECT count(*)::text FROM information_schema.tables WHERE table_schema='public' AND"
                                        + " table_name IN"
                                        + " ('observation','practice_result','practice_observation','agent_job','artifact_signal','identity_link','workspace_membership')"))
                .containsExactly("0");
        assertThatThrownBy(() ->
                        execute(database, "DELETE FROM membership WHERE workspace_id=992101 AND account_id=992106"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute(
                        database,
                        "INSERT INTO"
                                + " result(id,workspace_id,review_id,practice_id,practice_version_id,item_id,item_version,developer_id,outcome,summary,evidence_version,recorded_at)"
                                + " SELECT"
                                + " gen_random_uuid(),workspace_id,review_id,practice_id,practice_version_id,item_id,item_version,developer_id,outcome,summary,0,recorded_at"
                                + " FROM result WHERE id='"
                                + RESULT + "'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("evidence version 1");
        assertThatThrownBy(() -> execute(
                        database,
                        "INSERT INTO"
                                + " problem(id,workspace_id,result_id,position,severity,summary,explanation,citations)"
                                + " VALUES(gen_random_uuid(),992201,'"
                                + RESULT + "',1,'MINOR','Other','Other','[]')"))
                .isInstanceOf(SQLException.class);
        execute(
                database,
                "INSERT INTO result_mark(id,workspace_id,result_id,kind,reason) VALUES(gen_random_uuid(),992101,'"
                        + RESULT + "','ACCEPTED','Accept this risk')");
        assertThat(column(database, "SELECT (ended_at IS NULL)::text FROM result WHERE id='" + RESULT + "'"))
                .containsExactly("true");
        execute(
                database,
                "UPDATE result_mark SET removed_at=now(),removal_reason='Reconsidered' WHERE result_id='" + RESULT
                        + "' AND kind='ACCEPTED'");
        assertThat(column(database, "SELECT (ended_at IS NULL)::text FROM result WHERE id='" + RESULT + "'"))
                .containsExactly("true");
        var beforeReplay = column(
                database,
                "SELECT (SELECT count(*) FROM result WHERE workspace_id=992101)::text || '|' || (SELECT count(*) FROM"
                        + " feedback_copy WHERE workspace_id=992101) || '|' || (SELECT count(*) FROM feedback_send WHERE"
                        + " workspace_id=992101)");
        upgrade(database, context);
        assertThat(column(
                        database,
                        "SELECT (SELECT count(*) FROM result WHERE workspace_id=992101)::text || '|' ||"
                                + " (SELECT count(*) FROM feedback_copy WHERE workspace_id=992101) || '|' ||"
                                + " (SELECT count(*) FROM feedback_send WHERE workspace_id=992101)"))
                .isEqualTo(beforeReplay);
        assertThat(column(database, "SELECT locked::text FROM databasechangeloglock WHERE id=1"))
                .containsExactly("false");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldHaltAndRollBackWhenFeedbackPeopleDisagree(String context) throws Exception {
        var database = releasedDatabase(context);
        execute(database, "UPDATE feedback SET about_user_id=992104 WHERE id='" + FEEDBACK + "'");
        assertThatThrownBy(() -> upgrade(database, context)).hasMessageContaining("feedback person is ambiguous");
        assertThat(column(database, "SELECT count(*)::text FROM observation WHERE id='" + RESULT + "'"))
                .containsExactly("1");
        assertThat(column(
                        database,
                        "SELECT count(*)::text FROM information_schema.tables WHERE table_schema='public' AND"
                                + " table_name='result'"))
                .containsExactly("0");
        assertThat(column(database, "SELECT locked::text FROM databasechangeloglock WHERE id=1"))
                .containsExactly("false");
    }

    static TestDatabase releasedDatabase(String context) throws Exception {
        var database = PostgreSQLTestContainer.createDatabase(
                "practice_v1_" + context + "_" + UUID.randomUUID().toString().replace("-", ""));
        release(database, context);
        assertThat(column(
                        database,
                        "SELECT count(*)::text FROM databasechangelog WHERE filename LIKE '%" + BRANCH_FILE + "'"))
                .containsExactly("0");
        assertThat(column(
                        database,
                        "SELECT count(*)::text FROM information_schema.tables WHERE table_schema='public' AND"
                                + " table_name='observation'"))
                .containsExactly("1");
        try (var input = Objects.requireNonNull(
                PracticeV1CutoverMigrationTest.class.getResourceAsStream("/db/practice-cutover/released-seed.sql"))) {
            execute(database, new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        return database;
    }

    static void upgrade(TestDatabase database, String context) throws Exception {
        try (var liquibase = liquibase(database, "db/changelog/" + BRANCH_FILE)) {
            liquibase.update(new Contexts(context), new LabelExpression());
        }
    }

    static void release(TestDatabase database, String context) throws Exception {
        try (var liquibase = liquibase(database, "db/practice-cutover/released-main.xml")) {
            liquibase.update(new Contexts(context), new LabelExpression());
        }
        assertThat(column(
                        database,
                        "SELECT count(*)::text FROM databasechangelog WHERE id='1791941746633-2' AND"
                                + " filename='db/changelog/1791941746633_changelog.xml'"))
                .containsExactly("1");
        assertThat(column(
                        database,
                        "SELECT data_type FROM information_schema.columns WHERE table_schema='public' AND"
                                + " table_name='auth_event' AND column_name='details'"))
                .containsExactly("text");
    }

    private static Liquibase liquibase(TestDatabase database, String changelog) throws Exception {
        return new Liquibase(
                changelog,
                new ClassLoaderResourceAccessor(),
                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connect(database))));
    }

    private static Connection connect(TestDatabase database) throws SQLException {
        return DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password());
    }

    static void execute(TestDatabase database, String sql) throws SQLException {
        try (var connection = connect(database);
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    static List<@Nullable String> column(TestDatabase database, String sql) throws SQLException {
        try (var connection = connect(database);
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            List<@Nullable String> values = new ArrayList<>();
            while (rows.next()) values.add(rows.getString(1));
            return values;
        }
    }
}
