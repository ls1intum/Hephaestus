package de.tum.cit.aet.hephaestus.integration.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.audit.ConfigAuditRetentionJob;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditAction;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditActorKind;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountFeature;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountFeatureRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import de.tum.cit.aet.hephaestus.core.runtime.ShedLockConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.slack.SlackConversationTestSupport;
import de.tum.cit.aet.hephaestus.integration.slack.conversation.SlackConversationProjector;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import de.tum.cit.aet.hephaestus.testconfig.TestCacheConfiguration;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Validates the production schema, its JPA mappings, and database-enforced contracts. */
@DataJpaTest(
    properties = {
        "spring.liquibase.enabled=true",
        "spring.liquibase.change-log=classpath:db/master.xml",
        "spring.liquibase.contexts=dev,prod",
        "spring.jpa.hibernate.ddl-auto=validate",
    }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    {
        TestCacheConfiguration.class,
        SlackConversationProjector.class,
        ConfigAuditRetentionJob.class,
        ShedLockConfig.class,
        ProductionSchemaContractIntegrationTest.JsonConfiguration.class,
    }
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ActiveProfiles("test")
@Tag("database")
class ProductionSchemaContractIntegrationTest {

    private static final String ACCOUNT_FEATURE = "run_practice_review";
    private static final String IDENTITY_SUBJECT = "583231";

    private static final TestDatabase DATABASE = PostgreSQLTestContainer.createDatabase(
        "hephaestus_liquibase_validation"
    );

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::jdbcUrl);
        registry.add("spring.datasource.username", DATABASE::username);
        registry.add("spring.datasource.password", DATABASE::password);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SlackConversationProjector slackConversationProjector;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ConfigAuditRetentionJob retentionJob;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private IdentityLinkRepository identityLinkRepository;

    @Autowired
    private AccountFeatureRepository accountFeatureRepository;

    @Autowired
    private IdentityProviderRepository identityProviderRepository;

    @Test
    void accountFeatureIsScopedToTheProviderIdentity() {
        long providerA = provider("https://provider-a.example");
        long providerB = provider("https://provider-b.example");
        Account enabled = accountRepository.save(new Account("Provider A account"));
        Account disabled = accountRepository.save(new Account("Provider B account"));
        accountFeatureRepository.save(new AccountFeature(Objects.requireNonNull(enabled.getId()), ACCOUNT_FEATURE));
        link(enabled, providerA);
        link(disabled, providerB);

        assertThat(
            accountFeatureRepository.existsActiveFeatureForProviderSubject(providerA, IDENTITY_SUBJECT, ACCOUNT_FEATURE)
        ).isTrue();
        assertThat(
            accountFeatureRepository.existsActiveFeatureForProviderSubject(providerB, IDENTITY_SUBJECT, ACCOUNT_FEATURE)
        ).isFalse();
        assertThat(
            accountFeatureRepository.existsActiveFeatureForProviderSubject(providerA, IDENTITY_SUBJECT, "mentor_access")
        ).isFalse();
        assertThat(
            accountFeatureRepository.existsActiveFeatureForProviderSubject(999L, IDENTITY_SUBJECT, ACCOUNT_FEATURE)
        ).isFalse();
    }

    @Test
    void disabledIdentityDoesNotCarryAccountFeature() {
        long provider = provider("https://disabled-provider.example");
        Account account = accountRepository.save(new Account("Disabled identity account"));
        accountFeatureRepository.save(new AccountFeature(Objects.requireNonNull(account.getId()), ACCOUNT_FEATURE));
        IdentityLink identity = link(account, provider);

        assertThat(
            accountFeatureRepository.existsActiveFeatureForProviderSubject(provider, IDENTITY_SUBJECT, ACCOUNT_FEATURE)
        ).isTrue();

        identity.setDisabledAt(java.time.Instant.now());
        identityLinkRepository.save(identity);

        assertThat(
            accountFeatureRepository.existsActiveFeatureForProviderSubject(provider, IDENTITY_SUBJECT, ACCOUNT_FEATURE)
        ).isFalse();
    }

    private IdentityLink link(Account account, long providerId) {
        IdentityLink identity = new IdentityLink();
        identity.setAccount(account);
        identity.setProviderId(providerId);
        identity.setSubject(IDENTITY_SUBJECT);
        identity.setUsernameAtSignup("octocat");
        return identityLinkRepository.save(identity);
    }

    private long provider(String serverUrl) {
        return Objects.requireNonNull(
            identityProviderRepository.save(new IdentityProvider(IdentityProviderType.GITHUB, serverUrl)).getId()
        );
    }

    @Test
    @DisplayName("Production Liquibase schema applies cleanly and the JPA entities validate against it")
    void productionSchemaAppliesAndEntitiesValidate() {
        Integer appliedChangesets = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM databasechangelog",
            Integer.class
        );
        assertThat(appliedChangesets)
            .as("Liquibase DATABASECHANGELOG ledger should record the full production migration set")
            .isNotNull()
            .isGreaterThan(500);

        assertColumnExists("workspace", "account_login");
        assertColumnExists("connection", "credentials_encrypted");
        assertColumnExists("slack_message", "author_member_id");
        assertColumnExists("slack_thread", "participant_member_ids");
        assertColumnExists("slack_thread", "last_reviewed_ts");
        assertColumnExists("identity_provider", "type");
        assertIndexExists("idx_slack_thread_participants");
        assertIndexExists("idx_slack_message_ingest");
    }

    /**
     * {@code WorkspaceReviewScope} is a two-key vocabulary whose closure is enforced at the column, not by
     * the deserializer: a reader configured to ignore unknown fields would drop a third key in silence and
     * leave a workspace believing a restriction was in force. The column outlives every version of the code
     * that reads it, so {@code chk_workspace_review_scope} is the thing that has to refuse the write — and
     * only the Liquibase-built schema carries it, because the shared test profile builds the schema with
     * Hibernate {@code ddl-auto: create} and never runs a changeset.
     */
    @Test
    @DisplayName("The coverage-mode columns refuse a mode the vocabulary does not have")
    void coverageModeColumnsRefuseAnythingOutsideTheirVocabulary() {
        long workspaceId = insertWorkspace("coverage-mode-check");

        assertThatCode(() -> setCoverageMode(workspaceId, "practice_repository_coverage_mode", "SELECTED"))
            .as("a declared mode is what the entity writes")
            .doesNotThrowAnyException();

        assertThatThrownBy(() -> setCoverageMode(workspaceId, "practice_repository_coverage_mode", "ALL"))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("chk_workspace_practice_repository_coverage");

        assertThatThrownBy(() -> setCoverageMode(workspaceId, "practice_person_coverage_mode", "EVERYONE"))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("chk_workspace_practice_person_coverage");

        assertThatThrownBy(() -> setCoverageMode(workspaceId, "practice_delivery_status", "STOPPED"))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("chk_workspace_practice_delivery_status");

        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT practice_repository_coverage_mode FROM workspace WHERE id = ?",
                String.class,
                workspaceId
            )
        )
            .as("a refused write leaves the last accepted mode in force")
            .isEqualTo("SELECTED");
    }

    @Test
    @DisplayName("A covered repository's base branches must be an array, not a bare value")
    void baseBranchesRefusesAnythingThatIsNotAnArray() {
        assertThatThrownBy(() ->
            jdbcTemplate.update(
                "INSERT INTO practice_review_repository_target (workspace_id, repository_monitor_id, base_branches) " +
                    "VALUES (?, ?, CAST(? AS jsonb))",
                insertWorkspace("base-branches-check"),
                1L,
                "\"main\""
            )
        )
            .as("an axis that is not an array cannot be read as the list it claims to be")
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** A workspace row with only the columns the schema demands; every setting below is left at its default. */
    private long insertWorkspace(String slug) {
        Long id = jdbcTemplate.queryForObject(
            "INSERT INTO workspace (slug, display_name, status, account_type, account_login, is_publicly_viewable) " +
                "VALUES (?, ?, 'ACTIVE', 'ORG', ?, false) RETURNING id",
            Long.class,
            slug,
            "Review scope constraint fixture",
            slug
        );
        return Objects.requireNonNull(id, "workspace insert returned no id");
    }

    private void setCoverageMode(long workspaceId, String column, String mode) {
        jdbcTemplate.update("UPDATE workspace SET " + column + " = ? WHERE id = ?", mode, workspaceId);
    }

    private void assertIndexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
            Integer.class,
            indexName
        );
        assertThat(count).as("Liquibase-built schema must contain index %s", indexName).isNotNull().isEqualTo(1);
    }

    private void assertColumnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
            Integer.class,
            table,
            column
        );
        assertThat(count)
            .as("Liquibase-built schema must contain column %s.%s", table, column)
            .isNotNull()
            .isEqualTo(1);
    }

    @Test
    void participantFirewallUsesTheMigratedArrayColumn() {
        long workspaceId = 900_101L;
        String channel = "C1";
        SlackConversationTestSupport support = new SlackConversationTestSupport(jdbcTemplate);
        jdbcTemplate.execute("SET session_replication_role = 'replica'");
        try {
            support.seedChannel(workspaceId, channel, "ACTIVE");
            support.seedThread(workspaceId, channel, "100.000000", "100.500000", 4, "{100,101}");
            support.seedMessage(workspaceId, channel, "100.000000", null, "root");
            support.seedMessage(workspaceId, channel, "100.500000", "100.000000", "reply");
            jdbcTemplate.update(
                "UPDATE slack_message SET author_member_id = 100 WHERE workspace_id = ? AND slack_channel_id = ?",
                workspaceId,
                channel
            );
        } finally {
            jdbcTemplate.execute("SET session_replication_role = 'origin'");
        }

        ObjectNode participant = slackConversationProjector.buildPayload(workspaceId, 100L);
        ArrayNode conversations = (ArrayNode) participant.get("conversations");
        assertThat(conversations).hasSize(1);
        assertThat(conversations.get(0).get("channelName").asString()).isEqualTo("engineering");
        ArrayNode messages = (ArrayNode) conversations.get(0).get("messages");
        assertThat(messages)
            .hasSize(2)
            .allSatisfy(message -> {
                assertThat(message.get("text").asString()).isIn("root", "reply");
                assertThat(message.has("authorMemberId")).isFalse();
            });
        ArrayNode outsiderConversations = (ArrayNode) slackConversationProjector
            .buildPayload(workspaceId, 999L)
            .get("conversations");
        assertThat(outsiderConversations).isEmpty();
    }

    @Test
    void configAuditRowsAreAppendOnlyWithTheRetentionException() {
        long workspaceId = auditWorkspaceId();
        long current = insertAuditRow(workspaceId, 0);
        assertThatThrownBy(() ->
            jdbcTemplate.update("UPDATE config_audit_event SET entity_type = 'AGENT_CONFIG' WHERE id = ?", current)
        ).hasMessageContaining("append-only");
        assertThatThrownBy(() ->
            jdbcTemplate.update("DELETE FROM config_audit_event WHERE id = ?", current)
        ).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE config_audit_event")).hasMessageContaining(
            "append-only"
        );

        assertThatCode(() ->
            jdbcTemplate.update("UPDATE config_audit_event SET actor_account_id = NULL WHERE id = ?", current)
        ).doesNotThrowAnyException();
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT new_value::text FROM config_audit_event WHERE id = ?",
                String.class,
                current
            )
        ).contains("cooldownMinutes");
        assertThatThrownBy(() ->
            jdbcTemplate.update(
                "UPDATE config_audit_event SET actor_account_id = NULL, entity_type = 'AGENT_CONFIG' WHERE id = ?",
                current
            )
        ).hasMessageContaining("append-only");

        long directlyExpired = insertAuditRow(workspaceId, ConfigAuditRetentionJob.RETENTION_DAYS + 1);
        assertThatCode(() ->
            jdbcTemplate.update("DELETE FROM config_audit_event WHERE id = ?", directlyExpired)
        ).doesNotThrowAnyException();
        long stale = insertAuditRow(workspaceId, ConfigAuditRetentionJob.RETENTION_DAYS + 1);
        long inside = insertAuditRow(workspaceId, ConfigAuditRetentionJob.RETENTION_DAYS - 1);
        retentionJob.sweep();
        assertThat(auditRowExists(stale)).isFalse();
        assertThat(auditRowExists(inside)).isTrue();
        assertThat(auditRowExists(current)).isTrue();
    }

    static Stream<Arguments> auditEnumConstraints() {
        return Stream.of(
            Arguments.of("ck_config_audit_event_entity_type", ConfigAuditEntityType.values()),
            Arguments.of("ck_config_audit_event_action", ConfigAuditAction.values()),
            Arguments.of("ck_config_audit_event_actor_kind", ConfigAuditActorKind.values())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("auditEnumConstraints")
    void auditConstraintsAcceptEveryApplicationValue(String constraintName, Enum<?>[] values) {
        String definition = jdbcTemplate.queryForObject(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?",
            String.class,
            constraintName
        );
        List<String> accepted = Arrays.stream(Objects.requireNonNull(definition).split("'"))
            .filter(part -> part.matches("[A-Z_]{2,}"))
            .distinct()
            .toList();
        assertThat(accepted).containsExactlyInAnyOrderElementsOf(Arrays.stream(values).map(Enum::name).toList());
    }

    private long auditWorkspaceId() {
        return workspaceRepository
            .findByWorkspaceSlug("audit-immutability")
            .orElseGet(() -> {
                Workspace workspace = new Workspace();
                workspace.setWorkspaceSlug("audit-immutability");
                workspace.setDisplayName("Immutability");
                workspace.setAccountLogin("audit-immutability-org");
                workspace.setAccountType(AccountType.ORG);
                return workspaceRepository.save(workspace);
            })
            .getId();
    }

    private long insertAuditRow(long workspaceId, int ageInDays) {
        return Objects.requireNonNull(
            jdbcTemplate.queryForObject(
                """
                INSERT INTO config_audit_event
                    (workspace_id, entity_type, entity_id, action, actor_kind, actor_account_id,
                     old_value, new_value, changed_keys, occurred_at)
                VALUES (?, 'PRACTICE_REVIEW_SETTINGS', '1', 'UPDATED', 'USER', NULL,
                        '{"cooldownMinutes":30}'::jsonb, '{"cooldownMinutes":10}'::jsonb,
                        ARRAY['cooldownMinutes'], now() - make_interval(days => ?))
                RETURNING id
                """,
                Long.class,
                workspaceId,
                ageInDays
            )
        );
    }

    private boolean auditRowExists(long id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM config_audit_event WHERE id = ?",
            Integer.class,
            id
        );
        return count != null && count > 0;
    }

    @TestConfiguration
    static class JsonConfiguration {

        @Bean
        tools.jackson.databind.ObjectMapper objectMapper() {
            return new tools.jackson.databind.ObjectMapper();
        }
    }
}
