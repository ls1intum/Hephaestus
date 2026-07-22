package de.tum.cit.aet.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.ResolvedLlmModel;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ConfigSnapshotTest extends BaseUnitTest {

    // Mirrors spring.jackson.deserialization.fail-on-null-for-primitives=false from application.yml.
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .build();

    @Mock
    private LlmModelResolver resolver;

    private AgentConfig createConfig() {
        Workspace ws = new Workspace();
        ws.setId(1L);

        AgentConfig config = new AgentConfig();
        config.setId(42L);
        config.setWorkspace(ws);
        config.setName("my-agent");
        config.setLlmProvider(LlmProvider.ANTHROPIC);
        config.setModelName("claude-sonnet-4-20250514");
        config.setTimeoutSeconds(600);
        config.setAllowInternet(false);
        config.setLlmApiKey("sk-secret-key");
        config.setMaxConcurrentJobs(5);
        return config;
    }

    private void stubResolver(AgentConfig config) {
        when(resolver.resolve(config)).thenReturn(
            new ResolvedLlmModel(
                "https://api.anthropic.com",
                "anthropic-messages",
                "claude-sonnet-4-20250514",
                200000,
                8192,
                false,
                FundingSource.INSTANCE
            )
        );
        when(resolver.connectionRef(config)).thenReturn(new LlmModelResolver.ConnectionRef(FundingSource.INSTANCE, 7L));
    }

    @Nested
    class FromConfig {

        @Test
        void shouldCaptureAllIncludedFields() {
            AgentConfig config = createConfig();
            stubResolver(config);
            ConfigSnapshot snapshot = ConfigSnapshot.from(config, resolver);

            assertThat(snapshot.schemaVersion()).isEqualTo(ConfigSnapshot.SCHEMA_VERSION);
            assertThat(snapshot.configId()).isEqualTo(42L);
            assertThat(snapshot.configName()).isEqualTo("my-agent");
            assertThat(snapshot.apiProtocol()).isEqualTo("anthropic-messages");
            assertThat(snapshot.baseUrl()).isEqualTo("https://api.anthropic.com");
            assertThat(snapshot.upstreamModelId()).isEqualTo("claude-sonnet-4-20250514");
            assertThat(snapshot.contextWindow()).isEqualTo(200000);
            assertThat(snapshot.maxOutputTokens()).isEqualTo(8192);
            assertThat(snapshot.connectionScope()).isEqualTo(FundingSource.INSTANCE);
            assertThat(snapshot.connectionId()).isEqualTo(7L);
            assertThat(snapshot.timeoutSeconds()).isEqualTo(600);
            assertThat(snapshot.allowInternet()).isFalse();
        }

        @Test
        void shouldRejectNullConfig() {
            assertThatThrownBy(() -> ConfigSnapshot.from(null, resolver)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectNullResolver() {
            assertThatThrownBy(() -> ConfigSnapshot.from(createConfig(), null)).isInstanceOf(
                NullPointerException.class
            );
        }
    }

    @Nested
    class JsonRoundTrip {

        @Test
        void shouldSerializeAndDeserializeCorrectly() {
            AgentConfig config = createConfig();
            stubResolver(config);
            ConfigSnapshot original = ConfigSnapshot.from(config, resolver);

            JsonNode json = original.toJson(OBJECT_MAPPER);
            ConfigSnapshot deserialized = ConfigSnapshot.fromJson(json, OBJECT_MAPPER);

            assertThat(deserialized).isEqualTo(original);
            assertThat(deserialized.schemaVersion()).isEqualTo(ConfigSnapshot.SCHEMA_VERSION);
            assertThat(deserialized.configId()).isEqualTo(42L);
            assertThat(deserialized.configName()).isEqualTo("my-agent");
            assertThat(deserialized.timeoutSeconds()).isEqualTo(600);
        }

        @Test
        void shouldNotContainLlmApiKeyInJson() {
            AgentConfig config = createConfig();
            config.setLlmApiKey("sk-super-secret");
            stubResolver(config);

            ConfigSnapshot snapshot = ConfigSnapshot.from(config, resolver);
            JsonNode json = snapshot.toJson(OBJECT_MAPPER);
            String jsonString = json.toString();

            assertThat(jsonString).doesNotContain("llmApiKey");
            assertThat(jsonString).doesNotContain("llm_api_key");
            assertThat(jsonString).doesNotContain("sk-super-secret");
        }

        @Test
        void shouldNotContainAuthHeaderMaterialInJson() {
            // Locked decision (#1368 slice 5): NEVER freeze the credential OR any header material —
            // authHeaderName/authValuePrefix are re-resolved live from the connection, never from the
            // snapshot.
            AgentConfig config = createConfig();
            stubResolver(config);

            ConfigSnapshot snapshot = ConfigSnapshot.from(config, resolver);
            String jsonString = snapshot.toJson(OBJECT_MAPPER).toString();

            assertThat(jsonString).doesNotContain("authHeaderName");
            assertThat(jsonString).doesNotContain("authValuePrefix");
        }

        @Test
        void shouldNotContainMaxConcurrentJobsInJson() {
            AgentConfig config = createConfig();
            config.setMaxConcurrentJobs(10);
            stubResolver(config);

            ConfigSnapshot snapshot = ConfigSnapshot.from(config, resolver);
            JsonNode json = snapshot.toJson(OBJECT_MAPPER);
            String jsonString = json.toString();

            assertThat(jsonString).doesNotContain("maxConcurrentJobs");
            assertThat(jsonString).doesNotContain("max_concurrent_jobs");
        }

        @Test
        void shouldIncludeSchemaVersionInJson() {
            AgentConfig config = createConfig();
            stubResolver(config);
            ConfigSnapshot snapshot = ConfigSnapshot.from(config, resolver);
            JsonNode json = snapshot.toJson(OBJECT_MAPPER);

            assertThat(json.has("schemaVersion")).isTrue();
            assertThat(json.get("schemaVersion").asInt()).isEqualTo(ConfigSnapshot.SCHEMA_VERSION);
        }

        @Test
        void shouldIncludeConfigIdAndName() {
            AgentConfig config = createConfig();
            stubResolver(config);
            ConfigSnapshot snapshot = ConfigSnapshot.from(config, resolver);
            JsonNode json = snapshot.toJson(OBJECT_MAPPER);

            assertThat(json.has("configId")).isTrue();
            assertThat(json.get("configId").asLong()).isEqualTo(42L);
            assertThat(json.has("configName")).isTrue();
            assertThat(json.get("configName").asString()).isEqualTo("my-agent");
        }

        @Test
        void shouldRejectNewerSchemaVersion() {
            AgentConfig config = createConfig();
            stubResolver(config);
            ConfigSnapshot original = ConfigSnapshot.from(config, resolver);
            JsonNode json = original.toJson(OBJECT_MAPPER);

            // Mutate schemaVersion to a future version
            ((tools.jackson.databind.node.ObjectNode) json).put("schemaVersion", 999);

            assertThatThrownBy(() -> ConfigSnapshot.fromJson(json, OBJECT_MAPPER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("newer than supported version");
        }

        @Test
        void shouldTolerateUnknownFields() {
            AgentConfig config = createConfig();
            stubResolver(config);
            ConfigSnapshot original = ConfigSnapshot.from(config, resolver);
            JsonNode json = original.toJson(OBJECT_MAPPER);

            // Add an unknown field (simulates future schema addition)
            ((tools.jackson.databind.node.ObjectNode) json).put("futureField", "future-value");

            ConfigSnapshot deserialized = ConfigSnapshot.fromJson(json, OBJECT_MAPPER);
            assertThat(deserialized).isEqualTo(original);
        }

        @Test
        void shouldDeserializeLegacyV3Snapshot() {
            // Pre-v4 snapshot shape: llmProvider/credentialMode/llmBaseUrl/modelName, no
            // apiProtocol/connectionScope/connectionId. fromJson must translate it rather than
            // default-null the new fields — an in-flight job dispatched before the v4 deploy still
            // needs a usable apiProtocol/baseUrl/upstreamModelId.
            String legacy =
                "{\"schemaVersion\":3,\"configId\":42,\"configName\":\"legacy\"," +
                "\"llmProvider\":\"ANTHROPIC\",\"credentialMode\":\"PROXY\"," +
                "\"modelName\":\"claude-sonnet-4-20250514\"," +
                "\"modelVersion\":null,\"llmBaseUrl\":null,\"timeoutSeconds\":600,\"allowInternet\":false}";
            JsonNode node = OBJECT_MAPPER.readTree(legacy);

            ConfigSnapshot snapshot = ConfigSnapshot.fromJson(node, OBJECT_MAPPER);

            assertThat(snapshot.configId()).isEqualTo(42L);
            assertThat(snapshot.configName()).isEqualTo("legacy");
            assertThat(snapshot.apiProtocol()).isEqualTo("anthropic-messages");
            assertThat(snapshot.baseUrl()).isEqualTo("https://api.anthropic.com");
            assertThat(snapshot.upstreamModelId()).isEqualTo("claude-sonnet-4-20250514");
            assertThat(snapshot.timeoutSeconds()).isEqualTo(600);
            assertThat(snapshot.connectionScope()).isNull();
            assertThat(snapshot.connectionId()).isNull();
        }

        @Test
        void shouldDeserializeLegacyV3SnapshotWithExplicitBaseUrl() {
            String legacy =
                "{\"schemaVersion\":3,\"configId\":9,\"configName\":\"gateway\"," +
                "\"llmProvider\":\"OPENAI\",\"credentialMode\":\"API_KEY\"," +
                "\"modelName\":\"gpt-oss-120b\",\"llmBaseUrl\":\"https://gpu.example.com\"," +
                "\"timeoutSeconds\":300,\"allowInternet\":true}";
            JsonNode node = OBJECT_MAPPER.readTree(legacy);

            ConfigSnapshot snapshot = ConfigSnapshot.fromJson(node, OBJECT_MAPPER);

            assertThat(snapshot.apiProtocol()).isEqualTo("openai-completions");
            assertThat(snapshot.baseUrl()).isEqualTo("https://gpu.example.com");
            assertThat(snapshot.upstreamModelId()).isEqualTo("gpt-oss-120b");
        }

        @Test
        void shouldDeserializeV1WithoutSchemaVersion() {
            // Earliest snapshot shape predates the schemaVersion guard. fromJson reads
            // missing schemaVersion as 0 (< v4), so v1 rows are translated via the legacy path.
            String v1 =
                "{\"configId\":7,\"configName\":\"v1\",\"agentType\":\"OPENCODE\"," +
                "\"llmProvider\":\"OPENAI\",\"credentialMode\":\"PROXY\"," +
                "\"modelName\":\"gpt-4o-mini\",\"timeoutSeconds\":300,\"allowInternet\":false}";
            JsonNode node = OBJECT_MAPPER.readTree(v1);

            ConfigSnapshot snapshot = ConfigSnapshot.fromJson(node, OBJECT_MAPPER);

            assertThat(snapshot.apiProtocol()).isEqualTo("openai-completions");
            assertThat(snapshot.upstreamModelId()).isEqualTo("gpt-4o-mini");
        }
    }

    @Nested
    class Validation {

        @Test
        void shouldRejectNullApiProtocol() {
            assertThatThrownBy(() ->
                new ConfigSnapshot(
                    4,
                    1L,
                    "name",
                    null,
                    "https://api.openai.com",
                    "gpt",
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    600,
                    false
                )
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectNullBaseUrl() {
            assertThatThrownBy(() ->
                new ConfigSnapshot(
                    4,
                    1L,
                    "name",
                    "openai-completions",
                    null,
                    "gpt",
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    600,
                    false
                )
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectZeroTimeout() {
            assertThatThrownBy(() ->
                new ConfigSnapshot(
                    4,
                    1L,
                    "name",
                    "openai-completions",
                    "https://api.openai.com",
                    "gpt",
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    0,
                    false
                )
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectNegativeTimeout() {
            assertThatThrownBy(() ->
                new ConfigSnapshot(
                    4,
                    1L,
                    "name",
                    "openai-completions",
                    "https://api.openai.com",
                    "gpt",
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    -1,
                    false
                )
            ).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
