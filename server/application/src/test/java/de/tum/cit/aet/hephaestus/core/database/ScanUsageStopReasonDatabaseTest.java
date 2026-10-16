package de.tum.cit.aet.hephaestus.core.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import java.sql.DriverManager;
import java.sql.SQLException;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("database")
class ScanUsageStopReasonDatabaseTest {
    private static final String REVIEW = "00000000-0000-0000-0000-000000978011";

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void shouldPreserveReviewsAndPermitTheUsageReasonWhenTheConstraintIsUpgraded(String context) throws Exception {
        var database = NativePracticeFixture.resultParent(context);
        PracticeMigrationFixture.execute(database, """
            UPDATE review SET status='PARTIAL',stop_reason='BUDGET',completed_at=now()
            WHERE id='00000000-0000-0000-0000-000000978011'
            """);
        var before = PracticeMigrationFixture.column(
                database, "SELECT to_jsonb(r)::text FROM review r WHERE id='" + REVIEW + "'");

        try (var liquibase = liquibase(database)) {
            liquibase.rollback(1, new Contexts(context), new LabelExpression());
        }
        assertThatThrownBy(() -> reason(database, "USAGE_UNVERIFIABLE")).hasRootCauseInstanceOf(SQLException.class);

        PracticeMigrationFixture.upgrade(database, context);

        assertThat(PracticeMigrationFixture.column(
                        database, "SELECT to_jsonb(r)::text FROM review r WHERE id='" + REVIEW + "'"))
                .isEqualTo(before);
        reason(database, "USAGE_UNVERIFIABLE");
        assertThat(PracticeMigrationFixture.column(
                        database, "SELECT stop_reason FROM review WHERE id='" + REVIEW + "'"))
                .containsExactly("USAGE_UNVERIFIABLE");
        assertThatThrownBy(() -> reason(database, "UNKNOWN_USAGE_REASON")).hasRootCauseInstanceOf(SQLException.class);

        try (var liquibase = liquibase(database)) {
            assertThatThrownBy(() -> liquibase.rollback(1, new Contexts(context), new LabelExpression()))
                    .hasMessageContaining("Cannot remove a stop reason used by existing reviews");
        }
        reason(database, "BUDGET");
        try (var liquibase = liquibase(database)) {
            liquibase.rollback(1, new Contexts(context), new LabelExpression());
        }
        assertThatThrownBy(() -> reason(database, "USAGE_UNVERIFIABLE")).hasRootCauseInstanceOf(SQLException.class);
        reason(database, "EXECUTION_FAILED");
    }

    private static void reason(TestDatabase database, String reason) {
        PracticeMigrationFixture.execute(
                database, "UPDATE review SET stop_reason='" + reason + "' WHERE id='" + REVIEW + "'");
    }

    private static Liquibase liquibase(TestDatabase database) throws Exception {
        var connection = DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password());
        return new Liquibase(
                "db/master.xml",
                new ClassLoaderResourceAccessor(),
                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)));
    }
}
