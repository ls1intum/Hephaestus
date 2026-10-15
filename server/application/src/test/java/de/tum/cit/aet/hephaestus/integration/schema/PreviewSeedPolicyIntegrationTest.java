package de.tum.cit.aet.hephaestus.integration.schema;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import de.tum.cit.aet.hephaestus.testconfig.SchemaRowSeeder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Runs the preview clone policy of {@code docker/preview/compose.app.yaml} against the migrated
 * production schema.
 *
 * <p>The statements are read from the file a preview deploys, and they run against the schema
 * Liquibase produces. A copy of either here would pass while the deployed statement failed on a
 * column type or a constraint it does not carry, and the preview controller refuses to deploy a head
 * that changes {@code docker/preview/}, so no preview can rehearse the policy before it lands.
 *
 * <p>{@code scripts/check-preview-stack.ts} owns the other half: that the policy and the query
 * verifying it agree on which stores a clone has to be stripped of.
 */
@Tag("database")
class PreviewSeedPolicyIntegrationTest {

    private static final Path COMPOSE = Path.of("../../docker/preview/compose.app.yaml");
    private static final Pattern POLICY = Pattern.compile("<<'SQL'\n(.*?)\n[ \t]*SQL\n", Pattern.DOTALL);
    private static final Pattern VERIFICATION =
            Pattern.compile("LIVE=\\$\\$\\(psql[^\n]*-c \"(.*?)\"\\)", Pattern.DOTALL);
    private static final Pattern UPDATED_TABLE = Pattern.compile("\\bUPDATE\\s+([a-z_]+)");
    private static final Pattern EMPTIED_TABLE = Pattern.compile("\\bDELETE FROM\\s+([a-z_]+)");
    private static final String MARKER_PREFIX = "cleared-by-preview-clone:";

    private static final TestDatabase DATABASE =
            PostgreSQLTestContainer.createMigratedDatabase("hephaestus_preview_seed_policy");

    private static final TestDatabase RELEASED_DATABASE =
            PostgreSQLTestContainer.createReleasedDatabase("hephaestus_preview_released_policy", "prod");

    private JdbcTemplate jdbcTemplate = new JdbcTemplate(
            new SingleConnectionDataSource(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password(), true));
    private SchemaRowSeeder seeder = new SchemaRowSeeder(jdbcTemplate);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldClearEveryColumnOfTheSchemaThatCanHoldAStagingSecret(boolean released) {
        useDatabase(released);
        Map<String, String> updates = new HashMap<>();
        Set<String> emptied = new HashSet<>();
        for (String statement : read(POLICY).split(";", -1)) {
            Matcher updated = UPDATED_TABLE.matcher(statement);
            if (updated.find()) {
                updates.merge(updated.group(1), statement, String::concat);
            }
            Matcher deleted = EMPTIED_TABLE.matcher(statement);
            if (deleted.find()) {
                emptied.add(deleted.group(1));
            }
        }

        // A secret is stored as text or bytes; the same words on a numeric column count LLM tokens.
        List<String> uncleared = jdbcTemplate.queryForList("""
                        SELECT table_name, column_name FROM information_schema.columns
                         WHERE table_schema = 'public'
                           AND data_type IN ('text', 'character varying', 'bytea')
                           AND (column_name LIKE '%api_key%' OR column_name LIKE '%credential%'
                             OR column_name LIKE '%token%' OR column_name LIKE '%secret%'
                             OR column_name LIKE '%password%')""").stream()
                .filter(column -> {
                    String table = String.valueOf(column.get("table_name"));
                    return !emptied.contains(table)
                            && !updates.getOrDefault(table, "").contains(String.valueOf(column.get("column_name")));
                })
                .map(column -> column.get("table_name") + "." + column.get("column_name"))
                .toList();

        assertThat(uncleared)
                .as(
                        "a column that can hold a secret staging minted must be written by the clone policy in %s,"
                                + " or live in a table the policy empties",
                        COMPOSE)
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldStripStagingCredentialsAndKeepConnectionMetadataWhenACloneIsSeeded(boolean released) {
        useDatabase(released);
        // Foreign keys are not seeded. The clone must stop jobs and clear source credentials.
        jdbcTemplate.execute("SET session_replication_role = 'replica'");
        long workspaceId = 9_100_001L;
        long instanceCatalogId = 9_100_002L;
        long workspaceCatalogId = 9_100_003L;
        long gitHubConnectionId = 9_100_004L;
        long outlineConnectionId = 9_100_005L;
        UUID jobId = UUID.randomUUID();
        seeder.insert("workspace", Map.of("id", workspaceId, "public_activity_enabled", true));
        jdbcTemplate.execute("""
                INSERT INTO instance_settings(id, public_activity_allowed) VALUES (1, true)
                ON CONFLICT(id) DO UPDATE SET public_activity_allowed = true
                """);

        seeder.insert(
                "llm_connection",
                Map.of(
                        "id",
                        instanceCatalogId,
                        "api_protocol",
                        "openai-completions",
                        "api_key",
                        "staging-instance-key",
                        "enabled",
                        true));
        seeder.insert(
                "workspace_llm_connection",
                Map.of(
                        "id",
                        workspaceCatalogId,
                        "workspace_id",
                        workspaceId,
                        "api_protocol",
                        "openai-completions",
                        "api_key",
                        "staging-workspace-key",
                        "enabled",
                        true));
        seedConnection(gitHubConnectionId, workspaceId, "GITHUB", "{\"type\": \"GITHUB\"}");
        seedConnection(
                outlineConnectionId,
                workspaceId,
                "OUTLINE",
                "{\"type\": \"OUTLINE\", \"webhookSecret\": \"staging-signing-secret\"}");
        if (released) {
            seeder.insert(
                    "agent_job",
                    Map.of(
                            "id",
                            jobId,
                            "workspace_id",
                            workspaceId,
                            "status",
                            "COMPLETED",
                            "practice_trigger_mode",
                            "AUTO",
                            "job_token",
                            "staging-minted-token",
                            "job_token_hash",
                            "staging-token-hash"));
        } else {
            seeder.insert(
                    "review_job",
                    Map.of(
                            "id",
                            jobId,
                            "workspace_id",
                            workspaceId,
                            "status",
                            "COMPLETED",
                            "processing_status",
                            "PENDING"));
            seeder.insert(
                    "feedback_send",
                    Map.of(
                            "feedback_id",
                            UUID.randomUUID(),
                            "workspace_id",
                            workspaceId,
                            "action",
                            "POST",
                            "state",
                            "PENDING"));
        }
        jdbcTemplate.execute("SET session_replication_role = 'origin'");

        applyPolicy();

        assertThat(scalar("SELECT public_activity_allowed::text FROM instance_settings WHERE id = 1"))
                .isEqualTo("false");
        assertThat(scalar("SELECT public_activity_enabled::text FROM workspace WHERE id = " + workspaceId))
                .isEqualTo("false");
        assertThat(scalar("SELECT api_key FROM llm_connection WHERE id = " + instanceCatalogId))
                .isNull();
        assertThat(scalar("SELECT enabled::text FROM llm_connection WHERE id = " + instanceCatalogId))
                .isEqualTo("false");
        assertThat(scalar("SELECT api_key FROM workspace_llm_connection WHERE id = " + workspaceCatalogId))
                .isNull();
        assertThat(scalar("SELECT enabled::text FROM workspace_llm_connection WHERE id = " + workspaceCatalogId))
                .isEqualTo("false");

        for (long connectionId : List.of(gitHubConnectionId, outlineConnectionId)) {
            assertThat(scalar("SELECT credentials_encrypted::text FROM connection WHERE id = " + connectionId))
                    .isNull();
            assertThat(scalar("SELECT credentials_alg FROM connection WHERE id = " + connectionId))
                    .isNull();
            assertThat(scalar("SELECT credentials_key_version::text FROM connection WHERE id = " + connectionId))
                    .isNull();
            assertThat(scalar("SELECT state FROM connection WHERE id = " + connectionId))
                    .as("a clone keeps each connection's state; only what can act as staging goes")
                    .isEqualTo("ACTIVE");
        }
        assertThat(scalar("SELECT config ->> 'webhookSecret' FROM connection WHERE id = " + outlineConnectionId))
                .isNull();
        assertThat(scalar("SELECT config ->> 'type' FROM connection WHERE id = " + outlineConnectionId))
                .isEqualTo("OUTLINE");

        if (released) {
            assertThat(scalar("SELECT job_token FROM agent_job WHERE id = '" + jobId + "'"))
                    .isEqualTo(MARKER_PREFIX + jobId);
            assertThat(scalar("SELECT job_token_hash FROM agent_job WHERE id = '" + jobId + "'"))
                    .isNull();
        } else {
            assertThat(scalar("SELECT processing_status FROM review_job WHERE id = '" + jobId + "'"))
                    .isEqualTo("FAILED");
            assertThat(scalar("SELECT count(*)::text FROM feedback_send WHERE workspace_id = " + workspaceId))
                    .isEqualTo("0");
        }
        assertThat(residualCredentials())
                .as("the seed marker is earned by counting, so nothing the policy touches may be left")
                .isZero();

        applyPolicy();
        assertThat(residualCredentials()).isZero();
        if (released) {
            assertThat(scalar("SELECT job_token FROM agent_job WHERE id = '" + jobId + "'"))
                    .as("a second pass leaves the marker unchanged")
                    .isEqualTo(MARKER_PREFIX + jobId);
        }

        for (String publication : List.of(
                "UPDATE instance_settings SET public_activity_allowed = true WHERE id = 1",
                "UPDATE workspace SET public_activity_enabled = true WHERE id = " + workspaceId)) {
            jdbcTemplate.execute(publication);
            assertThat(residualCredentials()).isPositive();
            applyPolicy();
        }

        jdbcTemplate.execute(
                "UPDATE llm_connection SET api_key = 'restored-after-the-policy' WHERE id = " + instanceCatalogId);
        assertThat(residualCredentials())
                .as("a credential the policy did not reach leaves the preview un-booted rather than live")
                .isPositive();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldApplyThePolicyBeforePublicActivityColumnsExist(boolean released) {
        useDatabase(released);
        jdbcTemplate.execute("BEGIN");
        try {
            jdbcTemplate.execute("ALTER TABLE instance_settings DROP COLUMN public_activity_allowed");
            jdbcTemplate.execute("ALTER TABLE workspace DROP COLUMN public_activity_enabled");
            applyPolicy();
            assertThat(residualCredentials()).isZero();
        } finally {
            jdbcTemplate.execute("ROLLBACK");
        }
    }

    private void useDatabase(boolean released) {
        TestDatabase database = released ? RELEASED_DATABASE : DATABASE;
        jdbcTemplate = new JdbcTemplate(
                new SingleConnectionDataSource(database.jdbcUrl(), database.username(), database.password(), true));
        seeder = new SchemaRowSeeder(jdbcTemplate);
    }

    private void seedConnection(long id, long workspaceId, String kind, String config) {
        seeder.insert(
                "connection",
                Map.of(
                        "id",
                        id,
                        "workspace_id",
                        workspaceId,
                        "kind",
                        kind,
                        "state",
                        "ACTIVE",
                        "config",
                        config,
                        "credentials_encrypted",
                        new byte[] {1, 2, 3},
                        "credentials_alg",
                        "AES-GCM",
                        "credentials_key_version",
                        1));
    }

    private void applyPolicy() {
        jdbcTemplate.execute(read(POLICY));
    }

    private long residualCredentials() {
        Long live = jdbcTemplate.queryForObject(read(VERIFICATION), Long.class);
        return live == null ? -1 : live;
    }

    private @Nullable String scalar(String sql) {
        return jdbcTemplate.queryForObject(sql, String.class);
    }

    private static String read(Pattern block) {
        String compose;
        try {
            compose = Files.readString(COMPOSE);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read " + COMPOSE, exception);
        }
        Matcher matcher = block.matcher(compose);
        assertThat(matcher.find())
                .as("%s must contain the seed loader block matching %s", COMPOSE, block)
                .isTrue();
        return matcher.group(1);
    }
}
