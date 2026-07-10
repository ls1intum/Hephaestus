package de.tum.cit.aet.hephaestus.integration.schema;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.slack.SlackConversationTestSupport;
import de.tum.cit.aet.hephaestus.integration.slack.conversation.SlackConversationProjector;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.testconfig.TestAsyncConfiguration;
import de.tum.cit.aet.hephaestus.testconfig.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Drives the <b>production</b> Slack conversation SPIs against the <b>real Liquibase schema</b> so the one
 * remaining native-SQL path in {@code integration.slack.conversation} runs over the migrated column type. Boots
 * the full {@code db/master.xml} on a dedicated Testcontainer with {@code ddl-auto=validate}, exactly like
 * {@code ObservationAssessmentBackfillIntegrationTest}.
 *
 * <p><b>Why this test shrank.</b> The conversation package used to hand-roll every read as raw
 * {@code JdbcTemplate} SQL, including its own {@code bigint[]} {@code ResultSet.getArray()} decode — bespoke code
 * that could silently drift from the real changelog column types, which is what this test originally existed to
 * pin (three hops: the participant firewall, the candidate scan's array decode, and the watermark compare). That
 * raw-JDBC tunnel is gone: every read now rides a Spring Data repository. Plain JPQL reads/writes (the settled-
 * candidate scan, the watermark advance, the message projection) are ordinary Hibernate-mapped fields — protected
 * by {@code ddl-auto=validate} at boot and by Hibernate's own (well-tested) {@code @JdbcTypeCode(SqlTypes.ARRAY)}
 * marshalling, not by bespoke code of ours — so they no longer need a dedicated contract test here.
 *
 * <p>What's left, and still IS a hand-written native {@code @Query} that can drift silently against changeset
 * {@code 1782980500800-12}: {@link SlackThreadRepository#findParticipatingThreadRows}' real Postgres
 * {@code ? = ANY(participant_member_ids)} GIN membership test, exercised end-to-end through
 * {@link SlackConversationProjector#buildPayload} (a participant sees the thread; a non-participant does not). If
 * {@code -12} were {@code TEXT[]} the array literal seed / {@code ANY(...)} match breaks and the participant is
 * lost — exactly the drift class this test catches that a unit test against the fast, entity-derived schema
 * cannot.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ TestSecurityConfig.class, TestAsyncConfiguration.class })
@Testcontainers
@Tag("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SlackConversationSchemaContractIntegrationTest {

    /** Dedicated container so Liquibase builds the real production schema from empty. Bound to this class. */
    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("hephaestus_slack_schema_contract")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void overrideForProductionBootContract(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Flip the test-profile defaults (liquibase off, ddl-auto:create) back to the production boot contract so the
        // REAL migration set builds the schema (participant_member_ids bigint[], last_reviewed_ts VARCHAR(32)).
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log", () -> "classpath:db/master.xml");
        registry.add("spring.liquibase.contexts", () -> "dev");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
        registry.add("spring.datasource.hikari.leak-detection-threshold", () -> "0");
    }

    private static final long WS = 900_101L;
    private static final String CHANNEL = "C1";
    private static final String ROOT_TS = "100.000000";
    private static final String LAST_TS = "100.500000";

    @Autowired
    private SlackConversationProjector projector;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("participant firewall drives the real bigint[] column: ANY(participant_member_ids) over Postgres")
    void participantFirewallDrivesTheMigratedArrayColumn() {
        SlackConversationTestSupport support = new SlackConversationTestSupport(jdbcTemplate);
        // Real schema already has the migrated column types. Replica mode keeps fixture seeding independent of
        // unrelated workspace rows.
        jdbcTemplate.execute("SET session_replication_role = 'replica'");
        support.seedChannel(WS, CHANNEL, "ACTIVE");
        support.seedThread(WS, CHANNEL, ROOT_TS, LAST_TS, 4, "{100,101}");
        support.seedMessage(WS, CHANNEL, ROOT_TS, null, "root");
        support.seedMessage(WS, CHANNEL, LAST_TS, ROOT_TS, "reply");
        jdbcTemplate.update(
            "UPDATE slack_message SET author_member_id = 100 WHERE workspace_id = ? AND slack_channel_id = ?",
            WS,
            CHANNEL
        );
        jdbcTemplate.execute("SET session_replication_role = 'origin'");

        // The real GIN `? = ANY(participant_member_ids)` native query: a participant sees the thread…
        ObjectNode forParticipant = projector.buildPayload(WS, 100L);
        assertThat(conversations(forParticipant)).as("a participant (100) sees the thread").hasSize(1);
        assertThat(conversations(forParticipant).get(0).get("channelName").asString()).isEqualTo("engineering");
        assertThat(conversations(forParticipant).get(0).get("messages")).hasSize(2);
        assertThat(
            conversations(forParticipant).get(0).get("messages").get(0).get("authorMemberId").asLong()
        ).isEqualTo(100L);

        // …a non-participant sees nothing. If changeset 1782980500800-12 drifted off bigint[], the array-literal
        // seed above or the ANY(...) match itself would fail (a type mismatch or a broken membership test), so the
        // PARTICIPANT assertion above is what actually pins the real column type — this one just confirms the
        // firewall isn't vacuously true (e.g. "everyone sees everything").
        ObjectNode forOutsider = projector.buildPayload(WS, 999L);
        assertThat(conversations(forOutsider)).as("a non-participant (999) sees nothing").isEmpty();
    }

    private static ArrayNode conversations(ObjectNode payload) {
        return (ArrayNode) payload.get("conversations");
    }
}
