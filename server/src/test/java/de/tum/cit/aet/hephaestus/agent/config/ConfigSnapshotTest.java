package de.tum.cit.aet.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
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

    private WorkspaceAgentBinding createBinding() {
        Workspace ws = new Workspace();
        ws.setId(1L);

        WorkspaceAgentBinding binding = new WorkspaceAgentBinding();
        binding.setId(42L);
        binding.setWorkspace(ws);
        binding.setPurpose(AgentPurpose.PRACTICE_DETECTION);
        LlmModel model = new LlmModel();
        model.setId(20L);
        binding.setInstanceModel(model);
        binding.setTimeoutSeconds(600);
        binding.setAllowInternet(false);
        binding.setMaxConcurrentJobs(5);
        return binding;
    }

    private void stubResolver(WorkspaceAgentBinding binding) {
        when(resolver.resolve(binding)).thenReturn(
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
        when(resolver.connectionRef(binding)).thenReturn(
            new LlmModelResolver.ConnectionRef(FundingSource.INSTANCE, 7L)
        );
    }

    @Nested
    class FromBinding {

        @Test
        void shouldCaptureAllIncludedFields() {
            WorkspaceAgentBinding binding = createBinding();
            stubResolver(binding);
            ConfigSnapshot snapshot = ConfigSnapshot.from(binding, resolver);

            assertThat(snapshot.schemaVersion()).isEqualTo(ConfigSnapshot.SCHEMA_VERSION);
            assertThat(snapshot.modelVersion()).isNull(); // the catalog model carries the identity
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
        void shouldRejectNullSource() {
            assertThatThrownBy(() -> ConfigSnapshot.from(null, resolver)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectNullResolver() {
            assertThatThrownBy(() -> ConfigSnapshot.from(createBinding(), null)).isInstanceOf(
                NullPointerException.class
            );
        }
    }

    @Nested
    class JsonRoundTrip {

        @Test
        void shouldSerializeAndDeserializeCorrectly() {
            WorkspaceAgentBinding binding = createBinding();
            stubResolver(binding);
            ConfigSnapshot original = ConfigSnapshot.from(binding, resolver);

            JsonNode json = original.toJson(OBJECT_MAPPER);
            ConfigSnapshot deserialized = ConfigSnapshot.fromJson(json, OBJECT_MAPPER);

            assertThat(deserialized).isEqualTo(original);
            assertThat(deserialized.schemaVersion()).isEqualTo(ConfigSnapshot.SCHEMA_VERSION);
            assertThat(deserialized.timeoutSeconds()).isEqualTo(600);
        }

        @Test
        void shouldNotContainCredentialMaterialInJson() {
            // The binding itself never carries a key, and the snapshot must never grow a field that
            // copies one out of the resolved connection — the proxy re-resolves it live instead.
            WorkspaceAgentBinding binding = createBinding();
            stubResolver(binding);

            ConfigSnapshot snapshot = ConfigSnapshot.from(binding, resolver);
            JsonNode json = snapshot.toJson(OBJECT_MAPPER);
            String jsonString = json.toString();

            assertThat(jsonString).doesNotContain("llmApiKey");
            assertThat(jsonString).doesNotContain("llm_api_key");
            assertThat(jsonString).doesNotContain("apiKey");
            assertThat(jsonString).doesNotContain("api_key");
        }

        @Test
        void shouldNotContainAuthHeaderMaterialInJson() {
            // Locked decision (#1368 slice 5): NEVER freeze the credential OR any header material —
            // authHeaderName/authValuePrefix are re-resolved live from the connection, never from the
            // snapshot.
            WorkspaceAgentBinding binding = createBinding();
            stubResolver(binding);

            ConfigSnapshot snapshot = ConfigSnapshot.from(binding, resolver);
            String jsonString = snapshot.toJson(OBJECT_MAPPER).toString();

            assertThat(jsonString).doesNotContain("authHeaderName");
            assertThat(jsonString).doesNotContain("authValuePrefix");
        }

        @Test
        void shouldNotContainMaxConcurrentJobsInJson() {
            WorkspaceAgentBinding binding = createBinding();
            binding.setMaxConcurrentJobs(10);
            stubResolver(binding);

            ConfigSnapshot snapshot = ConfigSnapshot.from(binding, resolver);
            JsonNode json = snapshot.toJson(OBJECT_MAPPER);
            String jsonString = json.toString();

            assertThat(jsonString).doesNotContain("maxConcurrentJobs");
            assertThat(jsonString).doesNotContain("max_concurrent_jobs");
        }

        @Test
        void shouldIncludeSchemaVersionInJson() {
            WorkspaceAgentBinding binding = createBinding();
            stubResolver(binding);
            ConfigSnapshot snapshot = ConfigSnapshot.from(binding, resolver);
            JsonNode json = snapshot.toJson(OBJECT_MAPPER);

            assertThat(json.has("schemaVersion")).isTrue();
            assertThat(json.get("schemaVersion").asInt()).isEqualTo(ConfigSnapshot.SCHEMA_VERSION);
        }

        @Test
        void shouldRejectNewerSchemaVersion() {
            WorkspaceAgentBinding binding = createBinding();
            stubResolver(binding);
            ConfigSnapshot original = ConfigSnapshot.from(binding, resolver);
            JsonNode json = original.toJson(OBJECT_MAPPER);

            // Mutate schemaVersion to a future version
            ((tools.jackson.databind.node.ObjectNode) json).put("schemaVersion", 999);

            assertThatThrownBy(() -> ConfigSnapshot.fromJson(json, OBJECT_MAPPER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("newer than supported version");
        }

        @Test
        void shouldTolerateUnknownFields() {
            WorkspaceAgentBinding binding = createBinding();
            stubResolver(binding);
            ConfigSnapshot original = ConfigSnapshot.from(binding, resolver);
            JsonNode json = original.toJson(OBJECT_MAPPER);

            // Add an unknown field (simulates future schema addition)
            ((tools.jackson.databind.node.ObjectNode) json).put("futureField", "future-value");

            ConfigSnapshot deserialized = ConfigSnapshot.fromJson(json, OBJECT_MAPPER);
            assertThat(deserialized).isEqualTo(original);
        }

        @Test
        void shouldDeserializeV4SnapshotStillCarryingTheDroppedConfigFields() {
            // Regression guard for agent_job.config_snapshot rows already in production databases: v4
            // froze configId/configName, v5 dropped both components. Such a row must read through the
            // CURRENT shape with the two dead keys simply ignored — and must NOT be routed to the
            // pre-v4 legacy translation, which would null the connection identity and leave an
            // in-flight job unroutable at the proxy. Every catalog field asserted below is one the
            // legacy path would have nulled or defaulted away.
            String v4 =
                "{\"schemaVersion\":4,\"configId\":42,\"configName\":\"detection\"," +
                "\"apiProtocol\":\"openai-completions\",\"baseUrl\":\"https://gpu.example.com/v1\"," +
                "\"upstreamModelId\":\"gpt-oss-120b\",\"modelVersion\":\"2025-05-01\"," +
                "\"contextWindow\":128000,\"maxOutputTokens\":4096,\"supportsReasoning\":true," +
                "\"connectionScope\":\"WORKSPACE\",\"connectionId\":7,\"modelId\":20,\"workspaceId\":1," +
                "\"timeoutSeconds\":900,\"allowInternet\":true,\"priceSnapshot\":null}";
            JsonNode node = OBJECT_MAPPER.readTree(v4);

            ConfigSnapshot snapshot = ConfigSnapshot.fromJson(node, OBJECT_MAPPER);

            // The connection identity survives — proof the direct read ran, not fromLegacyJson.
            assertThat(snapshot.connectionScope()).isEqualTo(FundingSource.WORKSPACE);
            assertThat(snapshot.connectionId()).isEqualTo(7L);
            assertThat(snapshot.modelId()).isEqualTo(20L);
            assertThat(snapshot.workspaceId()).isEqualTo(1L);
            // ...and so does everything else the row froze.
            assertThat(snapshot.schemaVersion()).isEqualTo(4);
            assertThat(snapshot.apiProtocol()).isEqualTo("openai-completions");
            assertThat(snapshot.baseUrl()).isEqualTo("https://gpu.example.com/v1");
            assertThat(snapshot.upstreamModelId()).isEqualTo("gpt-oss-120b");
            assertThat(snapshot.modelVersion()).isEqualTo("2025-05-01");
            assertThat(snapshot.contextWindow()).isEqualTo(128000);
            assertThat(snapshot.maxOutputTokens()).isEqualTo(4096);
            assertThat(snapshot.supportsReasoning()).isTrue();
            assertThat(snapshot.timeoutSeconds()).isEqualTo(900);
            assertThat(snapshot.allowInternet()).isTrue();
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
                    ConfigSnapshot.SCHEMA_VERSION,
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
                    null,
                    600,
                    false,
                    null
                )
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectNullBaseUrl() {
            assertThatThrownBy(() ->
                new ConfigSnapshot(
                    ConfigSnapshot.SCHEMA_VERSION,
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
                    null,
                    600,
                    false,
                    null
                )
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectZeroTimeout() {
            assertThatThrownBy(() ->
                new ConfigSnapshot(
                    ConfigSnapshot.SCHEMA_VERSION,
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
                    null,
                    0,
                    false,
                    null
                )
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectNegativeTimeout() {
            assertThatThrownBy(() ->
                new ConfigSnapshot(
                    ConfigSnapshot.SCHEMA_VERSION,
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
                    null,
                    -1,
                    false,
                    null
                )
            ).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
