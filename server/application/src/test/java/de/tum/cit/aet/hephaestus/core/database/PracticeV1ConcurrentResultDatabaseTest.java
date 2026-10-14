package de.tum.cit.aet.hephaestus.core.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("database")
class PracticeV1ConcurrentResultDatabaseTest {
    @ParameterizedTest
    @ValueSource(ints = {Connection.TRANSACTION_READ_COMMITTED, Connection.TRANSACTION_REPEATABLE_READ})
    void shouldPreventConcurrentDeletionOfTheLastTwoProblems(int isolation) throws Exception {
        var database = PracticeV1ConstraintDatabaseTest.prepared("prod");
        PracticeMigrationFixture.execute(
                database, "BEGIN; " + PracticeV1ConstraintDatabaseTest.insert("NOT_MET", "NULL") + ";" + """
                    INSERT INTO problem(id,workspace_id,result_id,position,severity,summary,explanation,citations,basis)
                    VALUES('00000000-0000-0000-0000-000000995151',992101,'00000000-0000-0000-0000-000000995141',
                        0,'CRITICAL','First defect','First explanation','[{"quote":"first"}]','{}'),
                      ('00000000-0000-0000-0000-000000995152',992101,'00000000-0000-0000-0000-000000995141',
                        1,'MINOR','Second defect','Second explanation','[{"quote":"second"}]','{}');
                    COMMIT;
                    """);
        var secondSnapshot = new CountDownLatch(1);
        try (var first = DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password());
                var executor = Executors.newSingleThreadExecutor()) {
            first.setAutoCommit(false);
            first.setTransactionIsolation(isolation);
            try (var statement = first.createStatement()) {
                statement.execute("SET LOCAL statement_timeout='5s'");
                statement.execute("DELETE FROM problem WHERE id='00000000-0000-0000-0000-000000995151'");
            }
            var second = executor.submit(() -> {
                try (var connection =
                        DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password())) {
                    connection.setAutoCommit(false);
                    connection.setTransactionIsolation(isolation);
                    try (var statement = connection.createStatement()) {
                        statement.execute("SET LOCAL statement_timeout='5s'");
                        try (var rows = statement.executeQuery("SELECT count(*) FROM problem WHERE"
                                + " result_id='00000000-0000-0000-0000-000000995141'")) {
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getInt(1)).isEqualTo(2);
                        }
                        secondSnapshot.countDown();
                        statement.execute("DELETE FROM problem WHERE id='00000000-0000-0000-0000-000000995152'");
                        connection.commit();
                        return "committed";
                    } catch (SQLException exception) {
                        connection.rollback();
                        return Objects.requireNonNull(exception.getSQLState());
                    }
                }
            });
            assertThat(secondSnapshot.await(5, TimeUnit.SECONDS)).isTrue();
            first.commit();
            assertThat(second.get(10, TimeUnit.SECONDS)).isIn("23514", "40001");
        }
        assertThat(PracticeMigrationFixture.column(
                        database,
                        "SELECT count(*)::text FROM problem WHERE result_id='00000000-0000-0000-0000-000000995141'"))
                .containsExactly("1");
        assertThat(PracticeMigrationFixture.column(
                        database,
                        "SELECT outcome || '|' || severity FROM result WHERE"
                                + " id='00000000-0000-0000-0000-000000995141'"))
                .containsExactly("NOT_MET|MINOR");
    }
}
