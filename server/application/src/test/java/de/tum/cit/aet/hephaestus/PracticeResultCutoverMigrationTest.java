package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Runs the practice result cutover from the released schema over populated rows: {@code master.xml} up to the
 * rename, the rows as the released server wrote them, then the rest. Every result keeps its id, every negative gains
 * one problem under a new id, and the delivered feedback keeps its evidence without being delivered again.
 */
@Tag("database")
class PracticeResultCutoverMigrationTest {
    private static final String CUTOVER = "1791486276211-23";
    private static final TestDatabase DATABASE = PostgreSQLTestContainer.createDatabase("practice_result_cutover");
    private static final String MET = "00000000-0000-0000-0000-000000992141";
    private static final String NOT_MET = "00000000-0000-0000-0000-000000992142";
    private static final String BARE_NOT_MET = "00000000-0000-0000-0000-000000992143";
    private static final String UNDETERMINED = "00000000-0000-0000-0000-000000992144";
    private static final String FEEDBACK = "00000000-0000-0000-0000-000000992161";
    private static final String JOB = "00000000-0000-0000-0000-000000992111";

    /** The rows as the released server wrote them. */
    private static final String RELEASED_ROWS = """
            INSERT INTO workspace(id,account_login,account_type,display_name,slug,status)
            VALUES (992101,'cutover','ORG','Cutover','result-cutover','ACTIVE'),
                   (992201,'other','ORG','Other','result-cutover-other','ACTIVE');
            INSERT INTO identity_provider(id,type,server_url) VALUES (992102,'GITHUB','https://result-cutover.example');
            INSERT INTO "user"(id,native_id,provider_id,login) VALUES (992103,992103,992102,'developer'), (992104,992104,992102,'reviewer');
            INSERT INTO agent_job(id,workspace_id,job_type,status,config_snapshot,job_token,retry_count,created_at,available_at,trace_id,metadata)
            VALUES ('00000000-0000-0000-0000-000000992111',992101,'PRACTICE_REVIEW','COMPLETED','{}','t1',0,now(),now(),repeat('a',32),'{}'),
                   ('00000000-0000-0000-0000-000000992112',992101,'PRACTICE_REVIEW','COMPLETED','{}','t2',0,now(),now(),repeat('b',32),'{"review_id": 4242}');
            INSERT INTO practice(id,workspace_id,slug,name,criteria,created_at,automated_review_policy,delivery_behavior,signals,evidence_requirements,review_when,subject)
            VALUES (992121,992101,'explains-why','Explains why','The change explains why.',now(),'{}','{}','["scm.pull_request.created"]','[]','{}','AUTHOR');
            INSERT INTO practice_revision(id,practice_id,revision_number,created_at,slug,name,applies_to,signals,evidence_requirements,review_when,subject,criteria,automated_review_policy,delivery_behavior)
            SELECT 992131,id,1,now(),slug,name,applies_to,signals,evidence_requirements,review_when,subject,criteria,automated_review_policy,delivery_behavior FROM practice WHERE id=992121;
            UPDATE practice SET current_revision_id=992131 WHERE id=992121;
            INSERT INTO practice(id,workspace_id,slug,name,criteria,created_at,automated_review_policy,delivery_behavior,signals,evidence_requirements,review_when,subject)
            VALUES (992122,992101,'reviews-risk','Reviews risk','The review weighs the risk.',now(),'{}','{}','["scm.pull_request.reviewed"]','[]','{}','REVIEWER');
            INSERT INTO practice_revision(id,practice_id,revision_number,created_at,slug,name,applies_to,signals,evidence_requirements,review_when,subject,criteria,automated_review_policy,delivery_behavior)
            SELECT 992132,id,1,now(),slug,name,applies_to,signals,evidence_requirements,review_when,subject,criteria,automated_review_policy,delivery_behavior FROM practice WHERE id=992122;
            UPDATE practice SET current_revision_id=992132 WHERE id=992122;
            INSERT INTO observation(id,occurrence_key,agent_job_id,workspace_id,practice_id,practice_revision_id,artifact_kind,artifact_id,about_user_id,summary,outcome,severity,evidence,evidence_rationale,recurrence_key,observed_at)
            VALUES
             ('00000000-0000-0000-0000-000000992141','occ-met','00000000-0000-0000-0000-000000992111',992101,992121,992131,'scm.pull_request',77,992103,'Explains the motivation','MET',NULL,
              '{"citations":[{"sourceKind":"scm.pull-request.core","artifactPath":"context/metadata.json","path":"metadata.json","startLine":3,"endLine":3,"quote":"Why: retries"}]}','The body says why.',NULL,now()),
             ('00000000-0000-0000-0000-000000992142','occ-not-met','00000000-0000-0000-0000-000000992111',992101,992121,992131,'scm.pull_request',77,992103,'No reason given','NOT_MET','MAJOR',
              '{"citations":[{"sourceKind":"scm.pull-request.diff","artifactPath":"context/diff.patch","path":"src/App.java","side":"NEW","startLine":9,"endLine":9,"quote":"retry();"}]}','The change adds a retry without saying why.','rk-legacy',now()),
             ('00000000-0000-0000-0000-000000992143','occ-not-met-bare','00000000-0000-0000-0000-000000992112',992101,992122,992132,'scm.pull_request',77,992104,'Review skipped the risk','NOT_MET','MINOR',
              NULL,NULL,NULL,now()),
             ('00000000-0000-0000-0000-000000992144','occ-undetermined','00000000-0000-0000-0000-000000992111',992101,992121,992131,'scm.pull_request',78,992103,'Cannot tell','UNDETERMINED',NULL,
              '{"citations":[],"undecidability":{"question":"Was the retry discussed elsewhere?"}}',NULL,NULL,now());
            INSERT INTO observation_invalidation(id,invalidated_at,observation_id,provider_copy,provider_copy_retry_at,reason,workspace_id)
            VALUES ('00000000-0000-0000-0000-000000992151',now(),'00000000-0000-0000-0000-000000992142','NONE',now(),'Wrong file',992101);
            INSERT INTO feedback(id,agent_job_id,workspace_id,recipient_user_id,about_user_id,channel,"position",delivery_state,source,created_at,delivered_at,body)
            VALUES ('00000000-0000-0000-0000-000000992161','00000000-0000-0000-0000-000000992111',992101,992103,992103,'IN_CONTEXT',0,'DELIVERED','AGENT',now(),now(),'Say why.');
            INSERT INTO feedback_observation(feedback_id,observation_id,role,ordinal)
            VALUES ('00000000-0000-0000-0000-000000992161','00000000-0000-0000-0000-000000992142','PRIMARY',0),
                   ('00000000-0000-0000-0000-000000992161','00000000-0000-0000-0000-000000992141','SUPPORTING',1);
            INSERT INTO agent_job_practice_script_run(job_id,attempt,practice_slug,workspace_id,practice_revision_id,status,leads,models,finished_at,duration_ms,error)
            VALUES ('00000000-0000-0000-0000-000000992111',0,'explains-why',992101,992131,'OK',2,'[]',now(),10,NULL),
                   ('00000000-0000-0000-0000-000000992112',0,'explains-why',992101,992131,'TIMED_OUT',0,NULL,now(),60000,'Timed out');
            INSERT INTO practice_catalog_installation(workspace_id,installed_at) VALUES (992101, now());
            """;

    @BeforeAll
    static void migrateFromTheReleasedSchema() throws Exception {
        try (Liquibase liquibase = liquibase()) {
            var pending = liquibase.listUnrunChangeSets(new Contexts("prod"), new LabelExpression());
            int cutover = IntStream.range(0, pending.size())
                    .filter(i -> pending.get(i).getId().equals(CUTOVER))
                    .findFirst()
                    .orElseThrow();
            liquibase.update(cutover, new Contexts("prod"), new LabelExpression());
        }
        execute(RELEASED_ROWS);
        try (Liquibase liquibase = liquibase()) {
            liquibase.update(new Contexts("prod"));
        }
    }

    @Test
    void shouldKeepEveryResultIdAndRetireTheReleasedTablesWhenMigrated() throws Exception {
        assertThat(column("SELECT id::text FROM practice_result ORDER BY id"))
                .containsExactly(MET, NOT_MET, BARE_NOT_MET, UNDETERMINED);
        assertThat(column("""
                SELECT table_name::text FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('observation', 'observation_invalidation', 'feedback_observation')
                """)).isEmpty();
        assertThat(column("""
                SELECT column_name::text FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'practice_result' AND column_name = 'recurrence_key'
                """)).isEmpty();
        assertThat(column("SELECT practice_result_id::text FROM practice_result_invalidation"))
                .containsExactly(NOT_MET);
    }

    @Test
    void shouldGiveEveryNegativeOneProblemUnderANewIdWhenMigrated() throws Exception {
        assertThat(column("""
                SELECT practice_result_id || '|' || position || '|' || severity || '|' || summary || '|' || explanation
                       || '|' || jsonb_array_length(citations) || '|' || coalesce(recurrence_key, '-')
                FROM practice_observation ORDER BY practice_result_id
                """))
                .containsExactly(
                        NOT_MET + "|0|MAJOR|No reason given|The change adds a retry without saying why.|1|rk-legacy",
                        BARE_NOT_MET + "|0|MINOR|Review skipped the risk|Review skipped the risk|0|-");
        assertThat(column("""
                SELECT (o.citations = r.evidence->'citations')::text
                FROM practice_observation o JOIN practice_result r ON r.id = o.practice_result_id
                WHERE o.practice_result_id = '%s'
                """.formatted(NOT_MET))).containsExactly("true");
        assertThat(column("SELECT count(*) FROM practice_observation WHERE id::text IN ('%s', '%s')"
                        .formatted(NOT_MET, BARE_NOT_MET)))
                .containsExactly("0");
    }

    @Test
    void shouldScopeEveryResultAndListALegacyOpenQuestionWhenMigrated() throws Exception {
        assertThat(column("SELECT scope_key FROM practice_result ORDER BY id"))
                .containsExactly(
                        "scm.pull_request:77:AUTHOR",
                        "scm.pull_request:77:AUTHOR",
                        "scm.pull_request:77:REVIEWER:review:4242",
                        "scm.pull_request:78:AUTHOR");
        assertThat(column("SELECT evidence->'undecidability'->0->>'question' FROM practice_result WHERE id = '%s'"
                        .formatted(UNDETERMINED)))
                .containsExactly("Was the retry discussed elsewhere?");
        assertThat(column("SELECT count(*) FROM practice_result"
                        + " WHERE example IS NOT NULL OR coverage IS NOT NULL OR provenance IS NOT NULL"))
                .containsExactly("0");
    }

    @Test
    void shouldKeepTheDeliveredEvidenceWithoutDeliveringItAgainWhenMigrated() throws Exception {
        assertThat(column("""
                SELECT practice_result_id || '|' || workspace_id || '|' || role || '|' || (observation_id IS NULL)
                FROM feedback_evidence ORDER BY ordinal
                """)).containsExactly(NOT_MET + "|992101|PRIMARY|true", MET + "|992101|SUPPORTING|true");
        assertThat(column("SELECT count(DISTINCT id) FROM feedback_evidence")).containsExactly("2");
        assertThat(column("SELECT id || '|' || delivery_state FROM feedback")).containsExactly(FEEDBACK + "|DELIVERED");
        assertThat(column("SELECT count(*) FROM agent_job")).containsExactly("2");
    }

    @Test
    void shouldCloseTheReleasedScriptRunsInTheNewVocabularyWhenMigrated() throws Exception {
        assertThat(column("""
                SELECT status || '|' || coalesce(stop_reason, '-') || '|' || program_kind
                FROM agent_job_practice_script_run ORDER BY job_id
                """)).containsExactly("OK|-|CUSTOM", "FAILED|DEADLINE|CUSTOM");
        assertThat(column("""
                SELECT column_name::text FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'agent_job_practice_script_run' AND column_name = 'leads'
                """)).isEmpty();
        // The one-shot script upgrade still owes this workspace its pass.
        assertThat(column("SELECT (practice_scripts_upgraded_at IS NULL)::text FROM practice_catalog_installation"
                        + " WHERE workspace_id = 992101"))
                .containsExactly("true");
    }

    @Test
    void shouldRefuseANegativeWithoutProblemsAndAPositiveWithOneWhenMigrated() {
        assertThatThrownBy(() -> execute(result("00000000-0000-0000-0000-000000992171")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("has 0 observations");
        assertThatThrownBy(() -> execute(problem("00000000-0000-0000-0000-000000992181", MET, 992101, 0, "MINOR")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("outcome is MET");
        assertThatThrownBy(
                        () -> execute(problem("00000000-0000-0000-0000-000000992182", NOT_MET, 992101, 1, "CRITICAL")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("less severe");
    }

    @Test
    void shouldAdmitANegativeWrittenWithItsProblemInOneTransactionWhenMigrated() throws Exception {
        String id = "00000000-0000-0000-0000-000000992172";
        execute(result(id), problem("00000000-0000-0000-0000-000000992183", id, 992101, 0, "MINOR"));

        assertThat(column("SELECT count(*) FROM practice_observation WHERE practice_result_id = '%s'".formatted(id)))
                .containsExactly("1");
        execute("DELETE FROM practice_result WHERE id = '%s'".formatted(id));
        assertThat(column("SELECT count(*) FROM practice_observation WHERE practice_result_id = '%s'".formatted(id)))
                .containsExactly("0");
    }

    @Test
    void shouldKeepProblemsAndEvidenceInsideTheirWorkspaceWhenMigrated() {
        assertThatThrownBy(() -> execute(problem("00000000-0000-0000-0000-000000992184", NOT_MET, 992201, 1, "MINOR")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("fk_practice_observation_result");
        assertThatThrownBy(() -> execute("""
                        INSERT INTO feedback_evidence(id, feedback_id, practice_result_id, workspace_id, role, ordinal)
                        VALUES (gen_random_uuid(), '%s', '%s', 992201, 'PRIMARY', 3)
                        """.formatted(FEEDBACK, UNDETERMINED)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("sfk_feedback_evidence_feedback_workspace");
        assertThatThrownBy(() -> execute("""
                        INSERT INTO feedback_evidence(id, feedback_id, practice_result_id, observation_id, workspace_id,
                                                      role, ordinal)
                        SELECT gen_random_uuid(), '%s', '%s', id, 992101, 'PRIMARY', 2
                        FROM practice_observation WHERE practice_result_id = '%s'
                        """.formatted(FEEDBACK, MET, NOT_MET)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("sfk_feedback_evidence_observation");
        assertThatThrownBy(() -> execute("""
                        INSERT INTO feedback_evidence(id, feedback_id, practice_result_id, workspace_id, role, ordinal)
                        VALUES (gen_random_uuid(), '%s', '%s', 992101, 'PRIMARY', 4)
                        """.formatted(FEEDBACK, MET)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("uk_feedback_evidence_ref");
    }

    @Test
    void shouldTakeAnExampleOnlyOnADecidedResultWhenMigrated() {
        assertThatThrownBy(() -> execute("UPDATE practice_result SET example = '{\"note\":\"x\"}' WHERE id = '%s'"
                        .formatted(UNDETERMINED)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_practice_result_example");
    }

    /** A new negative about the seeded pull request, written without its problems. */
    private static String result(String id) {
        return """
                INSERT INTO practice_result(id, occurrence_key, agent_job_id, workspace_id, practice_id, artifact_kind,
                                            artifact_id, about_user_id, summary, outcome, severity, observed_at,
                                            scope_key)
                VALUES ('%s', 'occ-%s', '%s', 992101, 992121, 'scm.pull_request', 79, 992103, 'New', 'NOT_MET', 'MINOR',
                        now(), 'scm.pull_request:79:AUTHOR')
                """.formatted(id, id, JOB);
    }

    private static String problem(String id, String resultId, long workspaceId, int position, String severity) {
        return """
                INSERT INTO practice_observation(id, workspace_id, practice_result_id, position, severity, summary,
                                                 explanation, citations)
                VALUES ('%s', %d, '%s', %d, '%s', 'A problem', 'Why it is one', '[]')
                """.formatted(id, workspaceId, resultId, position, severity);
    }

    private static void execute(String... statements) throws SQLException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                for (String sql : statements) statement.execute(sql);
                connection.commit();
            }
        }
    }

    private static List<@Nullable String> column(String sql) throws SQLException {
        try (Connection connection = connect();
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            List<@Nullable String> values = new ArrayList<>();
            while (rows.next()) values.add(rows.getString(1));
            return values;
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password());
    }

    private static Liquibase liquibase() throws Exception {
        return new Liquibase(
                "db/master.xml",
                new ClassLoaderResourceAccessor(),
                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connect())));
    }
}
