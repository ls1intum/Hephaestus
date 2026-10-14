package de.tum.cit.aet.hephaestus.core.database;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

final class PracticeMigrationFixture {
    private static final String BRANCH_FILE = "1791486276211_changelog.xml";

    private PracticeMigrationFixture() {}

    private static final Map<String, TestDatabase> NATIVE_TEMPLATES = new HashMap<>();

    static synchronized TestDatabase migratedDatabase(String context) throws Exception {
        var template = NATIVE_TEMPLATES.get(context);
        if (template == null) {
            template = releasedDatabase(context);
            upgrade(template, context);
            NATIVE_TEMPLATES.put(context, template);
        }
        return PostgreSQLTestContainer.copyDatabase(
                "native_model_" + UUID.randomUUID().toString().replace("-", ""), template);
    }

    static TestDatabase releasedDatabase(String context) throws Exception {
        var database = PostgreSQLTestContainer.createReleasedDatabase(
                "practice_v1_" + context + "_" + UUID.randomUUID().toString().replace("-", ""), context);
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
                PracticeMigrationFixture.class.getResourceAsStream("/db/practice-cutover/released-seed.sql"))) {
            execute(database, new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        return database;
    }

    static void upgrade(TestDatabase database, String context) throws Exception {
        try (var connection = dataSource(database).getConnection();
                var liquibase = new Liquibase(
                        "db/master.xml",
                        new ClassLoaderResourceAccessor(),
                        DatabaseFactory.getInstance()
                                .findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            liquibase.update(new Contexts(context), new LabelExpression());
        }
    }

    private static DriverManagerDataSource dataSource(TestDatabase database) {
        return new DriverManagerDataSource(database.jdbcUrl(), database.username(), database.password());
    }

    static void execute(TestDatabase database, String sql) {
        new JdbcTemplate(dataSource(database)).execute(sql);
    }

    static List<@Nullable String> column(TestDatabase database, String sql) {
        return JdbcClient.create(dataSource(database))
                .sql(sql)
                .query((RowMapper<@Nullable String>) (rows, index) -> rows.getString(1))
                .list();
    }
}
