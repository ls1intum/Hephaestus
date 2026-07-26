package de.tum.cit.aet.hephaestus.agent.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The upgrade path for an instance that already has workspaces configured.
 *
 * <p>Retiring {@code agent_config} destroys the only copy of every workspace's LLM endpoint, model
 * name and encrypted API key unless the migration carries them into the new catalog first. No other
 * tier can catch a regression there: every other test builds its schema with {@code ddl-auto: create}
 * and never executes a changelog, and {@code ConfigAuditImmutabilityIntegrationTest} — the one test
 * that does run the real changelog — starts from an empty database, where a data migration is
 * indistinguishable from a no-op.
 *
 * <p>So this test stops the migration where the release branch begins, writes the rows a real
 * upgrading instance would have, and then finishes the migration and asserts what survived. The split
 * point is derived from the changelog itself rather than hard-coded: everything outside {@code
 * RELEASE_CHANGELOG} is "the old schema", so a changelog that lands on {@code main} while this branch
 * is in review moves the boundary by itself instead of silently turning this into a no-op.
 *
 * <p>The fixture is built around what the OLD code actually did, not around what the pointer columns
 * suggest. {@code practice_config_id} / {@code mentor_config_id} were written by one explicit UI
 * action and by nothing else — the startup seeder ({@code AGENT_DEFAULT_CONFIG_*}, the documented
 * default provisioning route) left both NULL and relied on the resolvers' fallbacks. So the shape
 * that must survive the upgrade is an <em>enabled</em> config with <em>no</em> pointer at all, and
 * every case below exists to pin one branch of those resolvers:
 *
 * <ul>
 *   <li>{@code legacy-seeded} — the seeder shape: enabled, both pointers NULL, serving both features.
 *   <li>{@code legacy-bound} — one config both pointers name explicitly.
 *   <li>{@code legacy-orphan} — disabled AND unreferenced: unreachable then, legitimately dropped now.
 *   <li>{@code legacy-anthropic} — a provider whose public endpoint the migration has to supply.
 *   <li>{@code legacy-nomodel} — no model name and no endpoint: both placeholders, limits still kept.
 *   <li>{@code legacy-fanout} — two enabled configs, no pointer: detection fanned out over both.
 *   <li>{@code legacy-paused} — a pointer at a DISABLED config, which paused detection but not the
 *       mentor.
 * </ul>
 *
 * <p>Tests are ordered: the assertions run first, then the re-run, then the rollback, because the
 * last two mutate the database they inspect.
 */
@Testcontainers
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LegacyAgentConfigMigrationIntegrationTest {

    /** The consolidated changelog this release ships; everything before it is the "old" schema. */
    private static final String RELEASE_CHANGELOG = "1785015307013_changelog.xml";

    private static final String MASTER = "db/master.xml";

    /**
     * Tagged on the last pre-release changeset, so the rollback test names a point rather than a
     * count — a count would silently walk into the previous changelog if any release changeSet were
     * skipped.
     */
    private static final String PRE_RELEASE_TAG = "before-1368";

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("hephaestus_legacy_agent_config_migration")
        .withUsername("test")
        .withPassword("test");

    @BeforeAll
    static void migrateAcrossSeededLegacyData() throws Exception {
        assertThat(updateUpToTheReleaseChangelog())
            .as("the release changelog must still be the tail of master.xml for this test to mean anything")
            .isPositive();
        try (Liquibase liquibase = liquibase()) {
            liquibase.tag(PRE_RELEASE_TAG);
        }
        seedLegacyConfiguration();
        runRemainingChangeSets();
    }

    @Test
    @Order(1)
    void theRetiredLegacyTableIsGone() throws SQLException {
        assertThat(scalar("SELECT to_regclass('agent_config')::text")).isNull();
    }

    /**
     * The regression this whole class exists for. An instance provisioned through
     * {@code AGENT_DEFAULT_CONFIG_*} — the documented default, and the very variables MIGRATION.md
     * tells the operator to delete — has an enabled config with both pointers NULL that is actively
     * serving the mentor and detection. Carrying over only pointed-at configs skips it, and the drop
     * of {@code agent_config} then destroys its key with no way to read it back.
     */
    @Test
    @Order(2)
    void theSeederShapeSurvivesEvenThoughNoPointerNamesIt() throws SQLException {
        assertThat(connectionOf("legacy-seeded"))
            .as("an enabled config with no pointer was serving both features; its endpoint and key must survive")
            .containsExactly("https://gpu.example.invalid/v1", "encrypted-seeded-key", "gpt-4o");

        assertThat(bindingsOf("legacy-seeded"))
            .as("the mentor fell back to it and detection fanned out to it, so it keeps both purposes")
            .containsExactly(
                new String[] { "MENTOR", "900", "5", "true" },
                new String[] { "PRACTICE_DETECTION", "900", "5", "true" }
            );
    }

    @Test
    @Order(3)
    void everyConfiguredWorkspaceKeepsItsEndpointModelAndApiKey() throws SQLException {
        assertThat(
            carriedConnectionRows()
                .stream()
                .map(row -> row[0] + '/' + row[1])
                .toList()
        )
            .as("one connection per config that was reachable — enabled, or named by a pointer")
            .containsExactly(
                "legacy-anthropic/legacy-9504",
                "legacy-bound/legacy-9502",
                "legacy-fanout/legacy-9506",
                "legacy-fanout/legacy-9507",
                "legacy-nomodel/legacy-9505",
                "legacy-paused/legacy-9508",
                "legacy-paused/legacy-9509",
                "legacy-seeded/legacy-9501"
            );

        assertThat(connectionOf("legacy-bound"))
            .as("an endpoint, an encrypted key and a model name are all irreplaceable once the table is dropped")
            .containsExactly("https://bound.example.invalid/v1", "encrypted-bound-key", "gpt-4.1");
    }

    @Test
    @Order(4)
    void aConfigSharedByBothPurposesBecomesTwoBindingsOnOneModel() throws SQLException {
        assertThat(bindingsOf("legacy-bound")).containsExactly(
            new String[] { "MENTOR", "600", "3", "false" },
            new String[] { "PRACTICE_DETECTION", "600", "3", "false" }
        );
        assertThat(
            scalar(
                """
                SELECT count(DISTINCT b.workspace_model_id)::text
                FROM workspace_agent_binding b
                JOIN workspace w ON w.id = b.workspace_id
                WHERE w.slug = 'legacy-bound'
                """
            )
        )
            .as("both purposes point at the one model the single legacy config described")
            .isEqualTo("1");
    }

    /**
     * The provider fallbacks. Both arms matter: an endpoint the migration gets wrong is an endpoint a
     * disabled row silently carries until someone re-enables it and the key leaves for the wrong host.
     */
    @Test
    @Order(5)
    void aProviderWithNoRecordedEndpointGetsItsDocumentedPublicOne() throws SQLException {
        assertThat(connectionOf("legacy-anthropic")[0])
            .as("Anthropic's documented public endpoint, not the not-migrated placeholder")
            .isEqualTo("https://api.anthropic.com/v1");
        assertThat(connectionOf("legacy-anthropic")[1]).isEqualTo("encrypted-anthropic-key");

        assertThat(connectionsOf("legacy-fanout").get(0)[0])
            .as("OpenAI's documented public endpoint")
            .isEqualTo("https://api.openai.com/v1");
    }

    @Test
    @Order(6)
    void aProviderWhoseEndpointWasNeverRecordedAnywhereGetsAnUnresolvablePlaceholder() throws SQLException {
        assertThat(connectionOf("legacy-nomodel")[0])
            .as(
                "the reserved .invalid TLD can never resolve, so a re-enabled row fails loudly instead of leaking the key"
            )
            .isEqualTo("https://endpoint-not-migrated.invalid/v1");
    }

    @Test
    @Order(7)
    void everyCarriedConnectionSpeaksTheOneProtocolTheNewSchemaAdmits() throws SQLException {
        assertThat(
            query(
                """
                SELECT DISTINCT api_protocol, auth_mode
                FROM workspace_llm_connection
                WHERE slug LIKE 'legacy-%'
                """
            )
        )
            .as("the legacy runtime spoke OpenAI chat completions with a bearer token; nothing else is admitted")
            .containsExactly(new String[] { "openai-completions", "BEARER" });
    }

    /**
     * A config with no model name used to yield a connection and nothing else, which quietly dropped
     * that workspace's timeout, concurrency and internet limits — values that exist nowhere but
     * {@code agent_config}, which the very next changeSet drops.
     */
    @Test
    @Order(8)
    void aConfigThatNeverNamedAModelStillKeepsItsExecutionLimits() throws SQLException {
        assertThat(connectionOf("legacy-nomodel")[2])
            .as("upstream_model_id is NOT NULL, so an unmistakable placeholder stands in for the missing name")
            .isEqualTo("model-not-migrated");

        assertThat(bindingsOf("legacy-nomodel"))
            .as("the limits are what the binding exists to carry; skipping the model would have dropped them")
            .containsExactly(
                new String[] { "MENTOR", "1200", "7", "true" },
                new String[] { "PRACTICE_DETECTION", "1200", "7", "true" }
            );
    }

    /**
     * Detection with no pointer fanned out to every enabled config. One binding per (workspace,
     * purpose) cannot express that, so it collapses to the oldest — but every config still arrives as
     * a connection and a model, so no key is lost and the operator can bind another.
     */
    @Test
    @Order(9)
    void aFannedOutWorkspaceKeepsEveryKeyAndBindsTheOldestConfig() throws SQLException {
        assertThat(connectionsOf("legacy-fanout"))
            .as("both enabled configs were live; neither key may be dropped")
            .containsExactly(
                new String[] { "https://api.openai.com/v1", "encrypted-fanout-older-key", "gpt-4o-mini" },
                new String[] { "https://second.example.invalid/v1", "encrypted-fanout-newer-key", "o3" }
            );

        assertThat(modelSlugsBoundIn("legacy-fanout"))
            .as("the oldest enabled config is the deterministic pick, and the mentor already resolved that way")
            .containsExactly("legacy-9506", "legacy-9506");
    }

    /**
     * The deliberate asymmetry in the old resolvers: a bound-but-disabled config PAUSED detection,
     * while the mentor fell through to the oldest enabled config so a user mid-conversation still got
     * an answer.
     */
    @Test
    @Order(10)
    void aPointerAtADisabledConfigPausesDetectionButNotTheMentor() throws SQLException {
        assertThat(purposeToModelSlug("legacy-paused")).containsExactly(
            Map.entry("MENTOR", "legacy-9509"),
            Map.entry("PRACTICE_DETECTION", "legacy-9508")
        );
    }

    @Test
    @Order(11)
    void nothingCarriedOverIsEnabledUntilAnAdminHasLookedAtIt() throws SQLException {
        assertThat(
            scalar(
                """
                SELECT (
                    (SELECT count(*) FROM workspace_llm_connection WHERE enabled)
                  + (SELECT count(*) FROM workspace_llm_model WHERE enabled)
                  + (SELECT count(*) FROM workspace_agent_binding WHERE enabled))::text
                """
            )
        )
            .as("the endpoint a PROXY-mode config really used lived in an env var, so re-enabling is a human decision")
            .isEqualTo("0");
    }

    /**
     * The one config that is still legitimately dropped. Disabled AND unreferenced means neither
     * resolver could ever select it: the mentor's fallback and detection's fan-out both filter on
     * enabled, and no pointer named it. Note the fixture — the row is DISABLED. An unreferenced
     * <em>enabled</em> config is exactly the seeder shape and must survive (see the test above).
     */
    @Test
    @Order(12)
    void aDisabledConfigNothingCouldReachIsNotCarriedOver() throws SQLException {
        assertThat(scalar("SELECT count(*)::text FROM workspace_llm_connection WHERE display_name = 'Orphan'"))
            .as("an unreachable draft configured nothing and must not resurface as a connection")
            .isEqualTo("0");
        assertThat(
            scalar("SELECT count(*)::text FROM workspace_llm_connection WHERE api_key = 'encrypted-orphan-key'")
        ).isEqualTo("0");
    }

    /**
     * The FK hardening that rides along with the retirement: {@code feedback.agent_job_id} used to
     * CASCADE, which took feedback, its observations, its placements and its reactions — append-only
     * research data — with any agent_job delete. The seeded job has feedback hanging off it, so the
     * refusal is exercised rather than asserted from the catalog alone.
     */
    @Test
    @Order(13)
    void deletingAnAgentJobNoLongerTakesItsFeedbackWithIt() throws SQLException {
        assertThatThrownBy(() -> execute("DELETE FROM agent_job WHERE id = '9a000000-0000-0000-0000-000000000001'"))
            .as("the FK is RESTRICT now, so the delete is refused instead of cascading")
            .hasMessageContaining("sfk_feedback_agent_job");

        assertThat(scalar("SELECT count(*)::text FROM feedback")).isEqualTo("1");
    }

    /**
     * Pre-release spend is attributed to the instance purse, uniformly and by explicit literal. Which
     * purse really paid is not recoverable — the worker-pod key override that beat the per-workspace
     * key lived in an environment variable — so the choice is a documented assumption, and a
     * documented assumption has to be pinned or it drifts.
     */
    @Test
    @Order(14)
    void backfilledSpendIsAttributedToTheInstancePurse() throws SQLException {
        assertThat(
            query(
                """
                SELECT source_type, funding_source, pricing_state, cost_usd::text
                FROM llm_usage_event ORDER BY source_type
                """
            )
        ).containsExactly(
            new String[] { "AGENT_JOB", "INSTANCE", "PRICED", "0.250000" },
            new String[] { "MENTOR_TURN", "INSTANCE", "PRICED", "0.125000" }
        );
    }

    /**
     * The deploy log is the only place an operator learns which workspaces the carry-over could not
     * reproduce faithfully — {@code agent_config} is gone by the time they read it, so nothing can be
     * reconstructed afterwards. PostgreSQL writes {@code RAISE WARNING} to its server log, which the
     * container captures, so the warnings are assertable rather than taken on trust.
     */
    @Test
    @Order(15)
    void theDeployLogNamesEveryWorkspaceAnOperatorHasToTouch() {
        String deployLog = POSTGRES.getLogs();

        assertThat(deployLog)
            .as("a placeholder endpoint, a placeholder model id and a non-OpenAI protocol all need a human")
            .contains(
                "carried over with a placeholder endpoint, model id or non-OpenAI protocol in these " +
                    "workspaces: legacy-anthropic, legacy-fanout, legacy-nomodel"
            );
        assertThat(deployLog)
            .as("only legacy-fanout ran detection on more than one config; legacy-paused had an explicit pointer")
            .contains("ran on SEVERAL configurations at once in these workspaces: legacy-fanout");
        assertThat(deployLog)
            .as("a dropped config may hold a key worth revoking, so it is named before the table goes")
            .contains("are dropped with the old table: legacy-orphan/Orphan");
    }

    /**
     * Re-running the release changelog against a database it has already upgraded must change
     * nothing. That is what every {@code MARK_RAN} precondition in the file is for, and it is the
     * recovery an operator reaches for when a deploy's tracking is in doubt.
     */
    @Test
    @Order(90)
    void reRunningTheReleaseChangelogChangesNothing() throws Exception {
        List<String[]> connectionsBefore = carriedConnectionRows();
        String bindingsBefore = scalar("SELECT count(*)::text FROM workspace_agent_binding");
        String ledgerBefore = scalar("SELECT count(*)::text FROM llm_usage_event");

        execute("DELETE FROM databasechangelog WHERE filename LIKE '%" + RELEASE_CHANGELOG + "'");
        assertThatCode(LegacyAgentConfigMigrationIntegrationTest::runRemainingChangeSets)
            .as("every changeSet must guard itself; a second pass over an upgraded database must not throw")
            .doesNotThrowAnyException();

        assertThat(carriedConnectionRows()).containsExactlyElementsOf(connectionsBefore);
        assertThat(scalar("SELECT count(*)::text FROM workspace_agent_binding")).isEqualTo(bindingsBefore);
        assertThat(scalar("SELECT count(*)::text FROM llm_usage_event"))
            .as("the ledger backfills are NOT EXISTS-guarded, so a second pass must not double-bill")
            .isEqualTo(ledgerBefore);
    }

    /** Destructive, so it runs last: the whole release rolls back cleanly and takes the catalog with it. */
    @Test
    @Order(99)
    void theWholeReleaseRollsBack() throws Exception {
        try (Liquibase liquibase = liquibase()) {
            liquibase.rollback(PRE_RELEASE_TAG, contexts(), new LabelExpression());
        }

        assertThat(scalar("SELECT to_regclass('workspace_agent_binding')::text")).isNull();
        assertThat(scalar("SELECT to_regclass('workspace_llm_connection')::text")).isNull();
        assertThat(scalar("SELECT to_regclass('workspace_llm_model')::text")).isNull();
        assertThat(scalar("SELECT count(*)::text FROM workspace WHERE slug LIKE 'legacy-%'"))
            .as("a rollback of the AI catalog must not take the workspaces with it")
            .isEqualTo("7");
    }

    // ── migration driver ────────────────────────────────────────────────────────────────────────

    /** @return how many changesets the release changelog contributes, i.e. how many were NOT applied. */
    private static int updateUpToTheReleaseChangelog() throws Exception {
        try (Liquibase liquibase = liquibase()) {
            List<ChangeSet> pending = liquibase.listUnrunChangeSets(contexts(), new LabelExpression());
            long release = pending
                .stream()
                .filter(cs -> cs.getFilePath().endsWith(RELEASE_CHANGELOG))
                .count();
            liquibase.update((int) (pending.size() - release), contexts(), new LabelExpression());
            return (int) release;
        }
    }

    private static void runRemainingChangeSets() throws Exception {
        try (Liquibase liquibase = liquibase()) {
            liquibase.update(contexts(), new LabelExpression());
        }
    }

    /** Each caller gets its own connection: closing a {@link Liquibase} closes the one it was given. */
    private static Liquibase liquibase() throws Exception {
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
            new JdbcConnection(connect())
        );
        return new Liquibase(MASTER, new ClassLoaderResourceAccessor(), database);
    }

    /** {@code prod} is what turns on the config-audit immutability triggers a real deployment gets. */
    private static Contexts contexts() {
        return new Contexts("prod");
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────────

    private static void seedLegacyConfiguration() throws SQLException {
        execute(
            """
            INSERT INTO workspace (id, account_login, account_type, display_name, slug, status, is_publicly_viewable)
            VALUES (9401, 'legacy-seeded',    'ORG', 'Seeded',    'legacy-seeded',    'ACTIVE', false),
                   (9402, 'legacy-bound',     'ORG', 'Bound',     'legacy-bound',     'ACTIVE', false),
                   (9403, 'legacy-orphan',    'ORG', 'Orphan',    'legacy-orphan',    'ACTIVE', false),
                   (9404, 'legacy-anthropic', 'ORG', 'Anthropic', 'legacy-anthropic', 'ACTIVE', false),
                   (9405, 'legacy-nomodel',   'ORG', 'No model',  'legacy-nomodel',   'ACTIVE', false),
                   (9406, 'legacy-fanout',    'ORG', 'Fan-out',   'legacy-fanout',    'ACTIVE', false),
                   (9407, 'legacy-paused',    'ORG', 'Paused',    'legacy-paused',    'ACTIVE', false)
            """,
            // The startup seeder's shape is 9501: enabled, BOTH pointers left NULL. Only an explicit UI
            // action ever wrote a pointer, so this — not 9502 — is what a default install looks like.
            """
            INSERT INTO agent_config (id, workspace_id, name, enabled, model_name, llm_api_key, llm_base_url,
                                      llm_provider, credential_mode, timeout_seconds, max_concurrent_jobs,
                                      allow_internet, created_at)
            VALUES (9501, 9401, 'Default model', true, 'gpt-4o', 'encrypted-seeded-key',
                    'https://gpu.example.invalid/v1', 'OPENAI', 'PROXY', 900, 5, true, now()),
                   (9502, 9402, 'Bound', true, 'gpt-4.1', 'encrypted-bound-key',
                    'https://bound.example.invalid/v1', 'OPENAI', 'PROXY', 600, 3, false, now()),
                   (9503, 9403, 'Orphan', false, 'gpt-4o', 'encrypted-orphan-key',
                    'https://api.openai.com/v1', 'OPENAI', 'PROXY', 600, 3, false, now()),
                   (9504, 9404, 'Claude', true, 'claude-sonnet-4', 'encrypted-anthropic-key', NULL,
                    'ANTHROPIC', 'PROXY', 300, 2, false, now()),
                   (9505, 9405, 'Azure', true, NULL, 'encrypted-nomodel-key', NULL,
                    'AZURE_OPENAI', 'PROXY', 1200, 7, true, now()),
                   (9506, 9406, 'Fan-out older', true, 'gpt-4o-mini', 'encrypted-fanout-older-key', NULL,
                    'OPENAI', 'PROXY', 480, 4, false, now()),
                   (9507, 9406, 'Fan-out newer', true, 'o3', 'encrypted-fanout-newer-key',
                    'https://second.example.invalid/v1', 'OPENAI', 'PROXY', 540, 6, true, now()),
                   (9508, 9407, 'Paused detection', false, 'gpt-4o', 'encrypted-paused-key',
                    'https://paused.example.invalid/v1', 'OPENAI', 'PROXY', 660, 8, false, now()),
                   (9509, 9407, 'Live mentor', true, 'gpt-4o', 'encrypted-mentor-key',
                    'https://mentor.example.invalid/v1', 'OPENAI', 'PROXY', 720, 9, true, now())
            """,
            // Only two workspaces ever had a pointer written: one naming a live config for both purposes,
            // one naming a DISABLED config for detection alone (which paused it, while the mentor fell
            // through to the oldest enabled config).
            "UPDATE workspace SET practice_config_id = 9502, mentor_config_id = 9502 WHERE id = 9402",
            "UPDATE workspace SET practice_config_id = 9508 WHERE id = 9407",
            // A completed job with recorded spend, and a suppressed finding hanging off it so the FK
            // this release hardens to RESTRICT has a row to refuse a cascade on.
            "INSERT INTO \"user\" (id, native_id, provider_id) VALUES (9601, 9601, 1)",
            """
            INSERT INTO agent_job (id, workspace_id, config_id, job_type, status, config_snapshot, job_token,
                                   retry_count, created_at, completed_at, llm_model, llm_total_calls,
                                   llm_total_input_tokens, llm_total_output_tokens, llm_cost_usd)
            VALUES ('9a000000-0000-0000-0000-000000000001', 9401, 9501, 'PRACTICE_DETECTION', 'COMPLETED',
                    '{}'::jsonb, 'token', 0, now(), now(), 'gpt-4o', 3, 1000, 500, 0.25)
            """,
            """
            INSERT INTO feedback (id, agent_job_id, workspace_id, recipient_user_id, about_user_id, channel,
                                  position, delivery_state, suppression_reason, source, created_at)
            VALUES ('9b000000-0000-0000-0000-000000000001', '9a000000-0000-0000-0000-000000000001', 9401,
                    9601, 9601, 'IN_CONTEXT', 1, 'SUPPRESSED', 'VOLUME_CAPPED', 'AGENT', now())
            """,
            // One mentor turn with recorded spend, so the chat_message backfill has something to classify.
            """
            INSERT INTO chat_thread (id, workspace_id, user_id, created_at, surface)
            VALUES ('9c000000-0000-0000-0000-000000000001', 9401, 9601, now(), 'WEB')
            """,
            """
            INSERT INTO chat_message (id, thread_id, role, status, parts, version, created_at, metadata)
            VALUES ('9d000000-0000-0000-0000-000000000001', '9c000000-0000-0000-0000-000000000001',
                    'ASSISTANT', 'completed', '[]'::jsonb, 0, now(),
                    '{"model":"gpt-4o","usage":{"input":10,"output":20},"costUsd":"0.125"}'::jsonb)
            """
        );
    }

    // ── queries ─────────────────────────────────────────────────────────────────────────────────

    /** Every carried-over connection: (workspace slug, connection slug, endpoint, key, model id). */
    private static List<String[]> carriedConnectionRows() throws SQLException {
        return query(
            """
            SELECT w.slug, c.slug, c.base_url, c.api_key, m.upstream_model_id
            FROM workspace_llm_connection c
            JOIN workspace w ON w.id = c.workspace_id
            LEFT JOIN workspace_llm_model m ON m.connection_id = c.id
            ORDER BY w.slug, c.slug
            """
        );
    }

    /** The single connection of a workspace that has exactly one: (endpoint, key, model id). */
    private static String[] connectionOf(String workspaceSlug) throws SQLException {
        List<String[]> connections = connectionsOf(workspaceSlug);
        assertThat(connections).as("%s is expected to have exactly one connection", workspaceSlug).hasSize(1);
        return connections.get(0);
    }

    private static List<String[]> connectionsOf(String workspaceSlug) throws SQLException {
        List<String[]> rows = new ArrayList<>();
        for (String[] row : carriedConnectionRows()) {
            if (row[0].equals(workspaceSlug)) {
                rows.add(new String[] { row[2], row[3], row[4] });
            }
        }
        return rows;
    }

    /** A workspace's bindings as (purpose, timeout, concurrency, internet), ordered by purpose. */
    private static List<String[]> bindingsOf(String workspaceSlug) throws SQLException {
        return query(
            """
            SELECT b.purpose, b.timeout_seconds::text, b.max_concurrent_jobs::text, b.allow_internet::text
            FROM workspace_agent_binding b
            JOIN workspace w ON w.id = b.workspace_id
            WHERE w.slug = '%s'
            ORDER BY b.purpose
            """.formatted(workspaceSlug)
        );
    }

    private static List<String> modelSlugsBoundIn(String workspaceSlug) throws SQLException {
        return purposeToModelSlug(workspaceSlug).stream().map(Map.Entry::getValue).toList();
    }

    private static List<Map.Entry<String, String>> purposeToModelSlug(String workspaceSlug) throws SQLException {
        List<Map.Entry<String, String>> bound = new ArrayList<>();
        for (String[] row : query(
            """
            SELECT b.purpose, m.slug
            FROM workspace_agent_binding b
            JOIN workspace w ON w.id = b.workspace_id
            JOIN workspace_llm_model m ON m.id = b.workspace_model_id
            WHERE w.slug = '%s'
            ORDER BY b.purpose
            """.formatted(workspaceSlug)
        )) {
            bound.add(Map.entry(row[0], row[1]));
        }
        return bound;
    }

    // ── plumbing ────────────────────────────────────────────────────────────────────────────────

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void execute(String... statements) throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private static String scalar(String sql) throws SQLException {
        List<String[]> rows = query(sql);
        return rows.isEmpty() ? null : rows.get(0)[0];
    }

    private static List<String[]> query(String sql) throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(sql)) {
                int columns = rs.getMetaData().getColumnCount();
                List<String[]> rows = new ArrayList<>();
                while (rs.next()) {
                    String[] row = new String[columns];
                    for (int i = 0; i < columns; i++) {
                        row[i] = rs.getString(i + 1);
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }
}
