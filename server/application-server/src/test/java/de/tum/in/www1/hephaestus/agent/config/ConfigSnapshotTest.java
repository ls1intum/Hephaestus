package de.tum.in.www1.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ConfigSnapshot")
class ConfigSnapshotTest extends BaseUnitTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AgentConfig createConfig() {
        Workspace ws = new Workspace();
        ws.setId(1L);

        AgentConfig config = new AgentConfig();
        config.setId(42L);
        config.setWorkspace(ws);
        config.setName("my-agent");
        config.setAgentType(AgentType.CLAUDE_CODE);
        config.setLlmProvider(LlmProvider.ANTHROPIC);
        config.setCredentialMode(CredentialMode.PROXY);
        config.setModelName("claude-sonnet-4-20250514");
        config.setTimeoutSeconds(600);
        config.setAllowInternet(false);
        config.setLlmApiKey("sk-secret-key");
        config.setMaxConcurrentJobs(5);
        return config;
    }

    @Nested
    @DisplayName("from(AgentConfig)")
    class FromConfig {

        @Test
        @DisplayName("should capture all included fields")
        void shouldCaptureAllIncludedFields() {
            AgentConfig config = createConfig();
            ConfigSnapshot snapshot = ConfigSnapshot.from(config);

            assertThat(snapshot.schemaVersion()).isEqualTo(ConfigSnapshot.SCHEMA_VERSION);
            assertThat(snapshot.configId()).isEqualTo(42L);
            assertThat(snapshot.configName()).isEqualTo("my-agent");
            assertThat(snapshot.agentType()).isEqualTo(AgentType.CLAUDE_CODE);
            assertThat(snapshot.llmProvider()).isEqualTo(LlmProvider.ANTHROPIC);
            assertThat(snapshot.credentialMode()).isEqualTo(CredentialMode.PROXY);
            assertThat(snapshot.modelName()).isEqualTo("claude-sonnet-4-20250514");
            assertThat(snapshot.timeoutSeconds()).isEqualTo(600);
            assertThat(snapshot.allowInternet()).isFalse();
        }

        @Test
        @DisplayName("should handle null modelName")
        void shouldHandleNullModelName() {
            AgentConfig config = createConfig();
            config.setModelName(null);

            ConfigSnapshot snapshot = ConfigSnapshot.from(config);
            assertThat(snapshot.modelName()).isNull();
        }

        @Test
        @DisplayName("should reject null config")
        void shouldRejectNullConfig() {
            assertThatThrownBy(() -> ConfigSnapshot.from(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("JSON round-trip")
    class JsonRoundTrip {

        @Test
        @DisplayName("should serialize and deserialize correctly")
        void shouldSerializeAndDeserializeCorrectly() {
            AgentConfig config = createConfig();
            ConfigSnapshot original = ConfigSnapshot.from(config);

            JsonNode json = original.toJson(OBJECT_MAPPER);
            ConfigSnapshot deserialized = ConfigSnapshot.fromJson(json, OBJECT_MAPPER);

            assertThat(deserialized).isEqualTo(original);
            assertThat(deserialized.schemaVersion()).isEqualTo(ConfigSnapshot.SCHEMA_VERSION);
            assertThat(deserialized.configId()).isEqualTo(42L);
            assertThat(deserialized.configName()).isEqualTo("my-agent");
            assertThat(deserialized.agentType()).isEqualTo(AgentType.CLAUDE_CODE);
            assertThat(deserialized.timeoutSeconds()).isEqualTo(600);
        }

        @Test
        @DisplayName("should not contain llmApiKey in JSON")
        void shouldNotContainLlmApiKeyInJson() {
            AgentConfig config = createConfig();
            config.setLlmApiKey("sk-super-secret");

            ConfigSnapshot snapshot = ConfigSnapshot.from(config);
            JsonNode json = snapshot.toJson(OBJECT_MAPPER);
            String jsonString = json.toString();

            assertThat(jsonString).doesNotContain("llmApiKey");
            assertThat(jsonString).doesNotContain("llm_api_key");
            assertThat(jsonString).doesNotContain("sk-super-secret");
        }

        @Test
        @DisplayName("should not contain maxConcurrentJobs in JSON")
        void shouldNotContainMaxConcurrentJobsInJson() {
            AgentConfig config = createConfig();
            config.setMaxConcurrentJobs(10);

            ConfigSnapshot snapshot = ConfigSnapshot.from(config);
            JsonNode json = snapshot.toJson(OBJECT_MAPPER);
            String jsonString = json.toString();

            assertThat(jsonString).doesNotContain("maxConcurrentJobs");
            assertThat(jsonString).doesNotContain("max_concurrent_jobs");
        }

        @Test
        @DisplayName("should include schemaVersion in JSON")
        void shouldIncludeSchemaVersionInJson() {
            ConfigSnapshot snapshot = ConfigSnapshot.from(createConfig());
            JsonNode json = snapshot.toJson(OBJECT_MAPPER);

            assertThat(json.has("schemaVersion")).isTrue();
            assertThat(json.get("schemaVersion").asInt()).isEqualTo(ConfigSnapshot.SCHEMA_VERSION);
        }

        @Test
        @DisplayName("should include configId and configName in JSON")
        void shouldIncludeConfigIdAndName() {
            ConfigSnapshot snapshot = ConfigSnapshot.from(createConfig());
            JsonNode json = snapshot.toJson(OBJECT_MAPPER);

            assertThat(json.has("configId")).isTrue();
            assertThat(json.get("configId").asLong()).isEqualTo(42L);
            assertThat(json.has("configName")).isTrue();
            assertThat(json.get("configName").asText()).isEqualTo("my-agent");
        }

        @Test
        @DisplayName("should reject snapshot with newer schemaVersion")
        void shouldRejectNewerSchemaVersion() {
            ConfigSnapshot original = ConfigSnapshot.from(createConfig());
            JsonNode json = original.toJson(OBJECT_MAPPER);

            // Mutate schemaVersion to a future version
            ((com.fasterxml.jackson.databind.node.ObjectNode) json).put("schemaVersion", 999);

            assertThatThrownBy(() -> ConfigSnapshot.fromJson(json, OBJECT_MAPPER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("newer than supported version");
        }

        @Test
        @DisplayName("should tolerate unknown fields in JSON (forward compatibility)")
        void shouldTolerateUnknownFields() {
            ConfigSnapshot original = ConfigSnapshot.from(createConfig());
            JsonNode json = original.toJson(OBJECT_MAPPER);

            // Add an unknown field (simulates future schema addition)
            ((com.fasterxml.jackson.databind.node.ObjectNode) json).put("futureField", "future-value");

            ConfigSnapshot deserialized = ConfigSnapshot.fromJson(json, OBJECT_MAPPER);
            assertThat(deserialized).isEqualTo(original);
        }

        @Test
        @DisplayName("should handle null modelName in JSON round-trip")
        void shouldHandleNullModelNameInJson() {
            AgentConfig config = createConfig();
            config.setModelName(null);

            ConfigSnapshot original = ConfigSnapshot.from(config);
            JsonNode json = original.toJson(OBJECT_MAPPER);
            ConfigSnapshot deserialized = ConfigSnapshot.fromJson(json, OBJECT_MAPPER);

            assertThat(deserialized.modelName()).isNull();
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should reject null agentType")
        void shouldRejectNullAgentType() {
            assertThatThrownBy(() ->
                new ConfigSnapshot(1, 1L, "name", null, LlmProvider.ANTHROPIC, CredentialMode.PROXY, null, 600, false)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null llmProvider")
        void shouldRejectNullLlmProvider() {
            assertThatThrownBy(() ->
                new ConfigSnapshot(1, 1L, "name", AgentType.CLAUDE_CODE, null, CredentialMode.PROXY, null, 600, false)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null credentialMode")
        void shouldRejectNullCredentialMode() {
            assertThatThrownBy(() ->
                new ConfigSnapshot(1, 1L, "name", AgentType.CLAUDE_CODE, LlmProvider.ANTHROPIC, null, null, 600, false)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject zero timeout")
        void shouldRejectZeroTimeout() {
            assertThatThrownBy(() ->
                new ConfigSnapshot(
                    1,
                    1L,
                    "name",
                    AgentType.CLAUDE_CODE,
                    LlmProvider.ANTHROPIC,
                    CredentialMode.PROXY,
                    null,
                    0,
                    false
                )
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject negative timeout")
        void shouldRejectNegativeTimeout() {
            assertThatThrownBy(() ->
                new ConfigSnapshot(
                    1,
                    1L,
                    "name",
                    AgentType.CLAUDE_CODE,
                    LlmProvider.ANTHROPIC,
                    CredentialMode.PROXY,
                    null,
                    -1,
                    false
                )
            ).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
