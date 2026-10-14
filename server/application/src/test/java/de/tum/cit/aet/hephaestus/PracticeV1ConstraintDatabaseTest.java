package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("database")
class PracticeV1ConstraintDatabaseTest {
    private static final String REVIEW = "00000000-0000-0000-0000-000000995111";
    private static final String RESULT = "00000000-0000-0000-0000-000000995141";

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldEnforceCompleteChildrenAndRestoreTheSeverityCacheOnRollback(String context) throws Exception {
        var database = prepared(context);
        assertThatThrownBy(() -> PracticeV1CutoverMigrationTest.execute(database, insert("NOT_MET", "NULL")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("invalid problem count");
        assertThatThrownBy(() -> PracticeV1CutoverMigrationTest.execute(database, insert("MET", "NULL")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_result_coverage");
        assertThatThrownBy(
                        () -> PracticeV1CutoverMigrationTest.execute(database, insert("COULD_NOT_DETERMINE", "NULL")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_result_open_questions");
        try (var connection =
                DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password())) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute(insert("NOT_MET", "NULL"));
                statement.execute("INSERT INTO"
                        + " problem(id,workspace_id,result_id,position,severity,summary,explanation,citations,basis)"
                        + " VALUES('00000000-0000-0000-0000-000000995151',992101,'"
                        + RESULT
                        + "',0,'MINOR','Missing purpose','Describe the"
                        + " purpose.','[{\"sourcePath\":\"description.md\",\"quote\":\"Retry\"}]','{}')");
                statement.execute("SET CONSTRAINTS ALL IMMEDIATE");
                connection.commit();
                statement.execute("INSERT INTO"
                        + " problem(id,workspace_id,result_id,position,severity,summary,explanation,citations,basis)"
                        + " VALUES(gen_random_uuid(),992101,'"
                        + RESULT
                        + "',1,'CRITICAL','Other purpose','Describe the"
                        + " purpose.','[{\"sourcePath\":\"description.md\",\"quote\":\"Retry\"}]','{}')");
                statement.execute("SET CONSTRAINTS ALL IMMEDIATE");
                try (var rows = statement.executeQuery("SELECT severity FROM result WHERE id='" + RESULT + "'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("CRITICAL");
                }
                connection.rollback();
            }
        }
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database, "SELECT severity FROM result WHERE id='" + RESULT + "'"))
                .containsExactly("MINOR");
        assertThatThrownBy(() -> PracticeV1CutoverMigrationTest.execute(
                        database, "DELETE FROM problem WHERE result_id='" + RESULT + "'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("invalid problem count");
        assertThatThrownBy(() -> PracticeV1CutoverMigrationTest.execute(
                        database, "UPDATE result SET summary='Rewrite the claim' WHERE id='" + RESULT + "'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("immutable");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldRejectIncompleteProofAndCrossTenantParents(String context) throws Exception {
        var database = prepared(context);
        assertThatThrownBy(() -> PracticeV1CutoverMigrationTest.execute(
                        database,
                        "BEGIN; " + insert("NOT_MET", "NULL")
                                + "; INSERT INTO"
                                + " problem(id,workspace_id,result_id,position,severity,summary,explanation,citations)"
                                + " VALUES(gen_random_uuid(),992101,'"
                                + RESULT
                                + "',0,'MINOR','Missing purpose','Describe the purpose.','[]'); SET"
                                + " CONSTRAINTS ALL IMMEDIATE; COMMIT"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("incomplete problem proof");
        assertThatThrownBy(() -> PracticeV1CutoverMigrationTest.execute(
                        database,
                        "INSERT INTO item(workspace_id,kind,parent_id,path) SELECT 992201,'scm.file',item_id,'file.ts'"
                                + " FROM review WHERE id='"
                                + REVIEW + "'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("parent must already exist");
        assertThatThrownBy(() -> PracticeV1CutoverMigrationTest.execute(
                        database, insert("MET", "'{}'").replace("item_version,992106", "'wrong-version',992106")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("frozen review assignment");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldKeepNobodyInTheCurrentClaimUniquenessKey(String context) throws Exception {
        var database = prepared(context);
        PracticeV1CutoverMigrationTest.execute(
                database, insert("MET", "'{}'").replace("item_version,992106", "item_version,NULL"));
        PracticeV1CutoverMigrationTest.execute(database, """
            INSERT INTO review(id,workspace_id,item_id,kind,item_version,origin,status,ordered_at)
            SELECT '00000000-0000-0000-0000-000000995112',workspace_id,item_id,kind,item_version,origin,status,ordered_at
            FROM review WHERE id='00000000-0000-0000-0000-000000995111';
            INSERT INTO review_practice(review_id,workspace_id,practice_id,practice_version_id,status)
            VALUES('00000000-0000-0000-0000-000000995112',992101,992121,992131,'READY');
            INSERT INTO review_item(review_id,workspace_id,practice_id,item_id,item_version)
            SELECT id,workspace_id,992121,item_id,item_version FROM review
            WHERE id='00000000-0000-0000-0000-000000995112';
            """);
        assertThatThrownBy(() -> PracticeV1CutoverMigrationTest.execute(
                        database,
                        insert("MET", "'{}'")
                                .replace("item_version,992106", "item_version,NULL")
                                .replace(REVIEW, "00000000-0000-0000-0000-000000995112")
                                .replace(RESULT, "00000000-0000-0000-0000-000000995142")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("uk_result_current");
    }

    static TestDatabase prepared(String context) throws Exception {
        var database = PracticeV1CutoverMigrationTest.migratedDatabase(context);
        PracticeV1CutoverMigrationTest.execute(database, """
            UPDATE result SET ended_at=now(),end_reason='REPLACED' WHERE workspace_id=992101 AND ended_at IS NULL;
            INSERT INTO review(id,workspace_id,item_id,kind,item_version,origin,status,ordered_at)
            SELECT '00000000-0000-0000-0000-000000995111',workspace_id,item_id,'ITEM',item_version,'REQUEST','QUEUED',now()
            FROM review WHERE id='00000000-0000-0000-0000-000000992111';
            INSERT INTO review_practice(review_id,workspace_id,practice_id,practice_version_id,status)
            VALUES('00000000-0000-0000-0000-000000995111',992101,992121,992131,'READY');
            INSERT INTO review_item(review_id,workspace_id,practice_id,item_id,item_version)
            SELECT id,workspace_id,992121,item_id,item_version FROM review
            WHERE id='00000000-0000-0000-0000-000000995111';
            """);
        return database;
    }

    static String insert(String outcome, String coverage) {
        return "INSERT INTO"
            + " result(id,workspace_id,review_id,practice_id,practice_version_id,item_id,item_version,developer_id,outcome,summary,coverage,recorded_at)"
            + " SELECT '"
                + RESULT + "',workspace_id,id,992121,992131,item_id,item_version,992106,'" + outcome
                + "','Review summary'," + coverage + ",now() FROM review WHERE id='" + REVIEW + "'";
    }
}
