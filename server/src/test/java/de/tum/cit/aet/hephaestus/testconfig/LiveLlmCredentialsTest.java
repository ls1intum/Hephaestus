package de.tum.cit.aet.hephaestus.testconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class LiveLlmCredentialsTest {

    @Test
    void shouldRequireEveryLiveEndpointSetting() {
        assertThatThrownBy(() -> LiveLlmCredentials.from(Map.of("HEPHAESTUS_LIVE_LLM_API_KEY", "secret")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("HEPHAESTUS_LIVE_LLM_BASE_URL")
            .hasMessageContaining("HEPHAESTUS_LIVE_LLM_MODEL");
    }

    @Test
    void shouldLoadExplicitOpenAiCompatibleEndpoint() {
        var credentials = LiveLlmCredentials.from(
            Map.of(
                "HEPHAESTUS_LIVE_LLM_BASE_URL",
                "https://llm.example/v1",
                "HEPHAESTUS_LIVE_LLM_API_KEY",
                "secret",
                "HEPHAESTUS_LIVE_LLM_MODEL",
                "example-model"
            )
        );

        assertThat(credentials.baseUrl()).isEqualTo("https://llm.example/v1");
        assertThat(credentials.model()).isEqualTo("example-model");
        assertThat(credentials.asProcessEnv())
            .containsEntry("OPENAI_BASE_URL", "https://llm.example/v1")
            .containsEntry("OPENAI_API_KEY", "secret");
    }
}
