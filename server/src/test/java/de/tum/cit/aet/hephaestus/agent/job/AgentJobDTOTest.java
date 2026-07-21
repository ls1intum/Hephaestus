package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link AgentJobDTO#from}'s base-URL redaction (#1368 fix wave): a workspace admin must never see the
 * full path/query of a base URL they don't own end-to-end — that previously covered only
 * {@code connectionScope=INSTANCE}, leaving a legacy (pre-catalog, {@code connectionScope=null})
 * config's {@code llmBaseUrl} to echo verbatim even though it is exactly as operator-sensitive.
 */
class AgentJobDTOTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static AgentJob jobWithSnapshot(ConfigSnapshot snapshot) {
        AgentJob job = new AgentJob();
        job.prePersist();
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.COMPLETED);
        job.setConfigSnapshot(snapshot.toJson(MAPPER));
        return job;
    }

    private static ConfigSnapshot snapshotWithScope(FundingSource scope) {
        return new ConfigSnapshot(
            ConfigSnapshot.SCHEMA_VERSION,
            10L,
            "test-config",
            "openai-completions",
            "https://gateway.example.com/v1/openai?tenant=secret-project",
            "gpt-5",
            null,
            null,
            null,
            false,
            null,
            scope,
            scope != null ? 42L : null,
            600,
            false
        );
    }

    @Nested
    class Redaction {

        @Test
        @DisplayName("an INSTANCE-scoped connection's baseUrl is reduced to scheme://host")
        void redactsInstanceScoped() {
            AgentJobDTO dto = AgentJobDTO.from(jobWithSnapshot(snapshotWithScope(FundingSource.INSTANCE)));

            ObjectNode snapshot = (ObjectNode) dto.configSnapshot();
            assertThat(snapshot.path("baseUrl").asString()).isEqualTo("https://gateway.example.com");
        }

        @Test
        @DisplayName(
            "#1368 fix wave: a legacy (connectionScope=null) config's baseUrl is ALSO reduced to " +
                "scheme://host — it is operator-sensitive routing detail exactly like an INSTANCE connection's"
        )
        void redactsLegacyNullScope() {
            AgentJobDTO dto = AgentJobDTO.from(jobWithSnapshot(snapshotWithScope(null)));

            ObjectNode snapshot = (ObjectNode) dto.configSnapshot();
            assertThat(snapshot.path("baseUrl").asString()).isEqualTo("https://gateway.example.com");
        }

        @Test
        @DisplayName("a WORKSPACE-scoped (BYO) connection's baseUrl is left intact — it's the workspace's own config")
        void leavesWorkspaceScopedIntact() {
            AgentJobDTO dto = AgentJobDTO.from(jobWithSnapshot(snapshotWithScope(FundingSource.WORKSPACE)));

            ObjectNode snapshot = (ObjectNode) dto.configSnapshot();
            assertThat(snapshot.path("baseUrl").asString()).isEqualTo(
                "https://gateway.example.com/v1/openai?tenant=secret-project"
            );
        }
    }
}
