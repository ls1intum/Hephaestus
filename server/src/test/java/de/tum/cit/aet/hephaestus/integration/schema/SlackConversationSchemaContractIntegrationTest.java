package de.tum.cit.aet.hephaestus.integration.schema;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.conversation.ConversationThreadCandidate;
import de.tum.cit.aet.hephaestus.integration.slack.SlackConversationTestSupport;
import de.tum.cit.aet.hephaestus.integration.slack.conversation.SlackConversationCandidateSource;
import de.tum.cit.aet.hephaestus.integration.slack.conversation.SlackConversationProjector;
import de.tum.cit.aet.hephaestus.testconfig.TestAsyncConfiguration;
import de.tum.cit.aet.hephaestus.testconfig.TestSecurityConfig;
import java.util.List;
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
 * Drives the <b>production</b> Slack conversation SPIs against the <b>real Liquibase schema</b> so the raw-JDBC
 * {@code bigint[]} / {@code VARCHAR(32)} paths run over the migrated column types — the thing the fast, entity-derived
 * SPI ITs cannot prove, because they hand-roll their own {@code participant_member_ids BIGINT[]} / {@code last_reviewed_ts
 * VARCHAR(32)} copies (which cannot drift from themselves). Boots the full {@code db/master.xml} on a dedicated
 * Testcontainer with {@code ddl-auto=validate}, exactly like {@code ObservationAssessmentBackfillIntegrationTest}.
 *
 * <p>What each hop uniquely pins (all against changesets {@code 1782980500800-12/-13}):
 * <ul>
 *   <li>{@link SlackConversationProjector#buildPayload} — the participant firewall over the real
 *       {@code ? = ANY(participant_member_ids)} GIN membership test (a participant sees the thread; a non-participant
 *       does not). If {@code -12} were {@code TEXT[]} the {@code CAST(... AS bigint[])} seed / decode breaks and the
 *       participant is lost.</li>
 *   <li>{@link SlackConversationCandidateSource#settledCandidates} — the {@code _int8} array decode through
 *       {@code readLongArray} returns the real {@code {100,101}} participant ids (empty if the column type drifted,
 *       which would silently enqueue nothing downstream).</li>
 *   <li>{@link SlackConversationCandidateSource#markReviewed} then re-scan — the {@code last_ts > last_reviewed_ts}
 *       growth watermark compares two real {@code VARCHAR(32)} Slack-ts strings; if {@code -13} drifted to
 *       {@code timestamptz} the stamp/compare breaks.</li>
 * </ul>
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
    private SlackConversationCandidateSource candidateSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("production SPIs drive the real bigint[]/VARCHAR(32) columns: firewall, decode, and watermark")
    void productionSpisDriveTheMigratedColumnTypes() {
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

        long threadId = jdbcTemplate.queryForObject(
            "SELECT id FROM slack_thread WHERE workspace_id = ? AND slack_channel_id = ? AND slack_thread_ts = ?",
            Long.class,
            WS,
            CHANNEL,
            ROOT_TS
        );

        // Hop 1 — participant firewall over the real GIN `? = ANY(participant_member_ids)`.
        ObjectNode forParticipant = projector.buildPayload(WS, 100L);
        assertThat(conversations(forParticipant)).as("a participant (100) sees the thread").hasSize(1);
        assertThat(conversations(forParticipant).get(0).get("channelName").asString()).isEqualTo("engineering");
        assertThat(conversations(forParticipant).get(0).get("messages")).hasSize(2);
        assertThat(
            conversations(forParticipant).get(0).get("messages").get(0).get("authorMemberId").asLong()
        ).isEqualTo(100L);

        ObjectNode forOutsider = projector.buildPayload(WS, 999L);
        assertThat(conversations(forOutsider)).as("a non-participant (999) sees nothing").isEmpty();

        // Hop 2 — the _int8 array decode returns the real participant ids.
        ConversationThreadCandidate candidate = settledForWorkspace(WS);
        assertThat(candidate).as("the settled thread is a candidate").isNotNull();
        assertThat(candidate.participantMemberIds())
            .as("participant_member_ids decode through readLongArray on the real _int8 column")
            .containsExactly(100L, 101L);
        assertThat(candidate.lastTs()).isEqualTo(LAST_TS);

        // Hop 3 — advance the VARCHAR(32) watermark; the now-quiescent thread drops out of the settled scan.
        candidateSource.markReviewed(WS, threadId, LAST_TS);
        assertThat(settledForWorkspace(WS))
            .as("after markReviewed (last_ts == last_reviewed_ts) the thread is no longer settled")
            .isNull();
    }

    private ConversationThreadCandidate settledForWorkspace(long workspaceId) {
        return candidateSource
            .settledCandidates(4)
            .stream()
            .filter(c -> c.workspaceId() == workspaceId)
            .findFirst()
            .orElse(null);
    }

    private static ArrayNode conversations(ObjectNode payload) {
        return (ArrayNode) payload.get("conversations");
    }
}
