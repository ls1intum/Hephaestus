package de.tum.cit.aet.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class AgentConfigSnapshotTest {

    private static final String SENTINEL = "sk-live-CANARY-must-never-be-audited";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void excludesAllDeprecatedProviderFields() throws Exception {
        AgentConfig config = config();
        config.setLlmApiKey(SENTINEL);
        config.setLlmBaseUrl("https://user:" + SENTINEL + "@gateway.example/v1");
        config.setModelName("legacy-model");

        String json = MAPPER.writeValueAsString(AgentConfigSnapshot.of(config));

        assertThat(json).doesNotContain(SENTINEL, "llmBaseUrl", "modelName", "llmProvider", "llmApiKey");
    }

    @Test
    void capturesSupportedSettingsAndCatalogBinding() {
        AgentConfig config = config();
        LlmModel model = new LlmModel();
        model.setId(42L);
        config.setInstanceModel(model);

        AgentConfigSnapshot snapshot = AgentConfigSnapshot.of(config);

        assertThat(snapshot.name()).isEqualTo("primary");
        assertThat(snapshot.enabled()).isTrue();
        assertThat(snapshot.instanceModelId()).isEqualTo(42L);
    }

    private static AgentConfig config() {
        AgentConfig config = new AgentConfig();
        config.setName("primary");
        config.setEnabled(true);
        return config;
    }
}
