package de.tum.cit.aet.hephaestus.agent.practice.live;

import static de.tum.cit.aet.hephaestus.agent.practice.live.LiveModelSettings.CONTEXT_WINDOW_PROPERTY;
import static de.tum.cit.aet.hephaestus.agent.practice.live.LiveModelSettings.MAX_OUTPUT_TOKENS_PROPERTY;
import static de.tum.cit.aet.hephaestus.agent.practice.live.LiveModelSettings.REASONING_EFFORT_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.catalog.ReasoningEffort;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
class LiveModelSettingsTest {

    @Test
    void shouldKeepTheHarnessDefaultsWhenNoSettingIsGiven() {
        LiveModelSettings settings = LiveModelSettings.from(Map.<String, String>of()::get);

        assertThat(settings).isEqualTo(new LiveModelSettings(131_072, 4096, null));
    }

    @Test
    void shouldUseEachGivenSettingAndKeepTheOthersWhenSomeAreGiven() {
        LiveModelSettings settings = LiveModelSettings.from(
                Map.of(MAX_OUTPUT_TOKENS_PROPERTY, "32768", REASONING_EFFORT_PROPERTY, "HIGH")::get);

        assertThat(settings).isEqualTo(new LiveModelSettings(131_072, 32_768, ReasoningEffort.HIGH));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "", "4k", "2147483648"})
    void shouldRefuseATokenCountWhenItIsNotAPositiveInteger(String value) {
        assertThatThrownBy(() -> LiveModelSettings.from(Map.of(CONTEXT_WINDOW_PROPERTY, value)::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(CONTEXT_WINDOW_PROPERTY + " must be a positive integer, not '" + value + "'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"high", "", "EXTREME"})
    void shouldRefuseAReasoningEffortWhenItIsNotACatalogValue(String value) {
        assertThatThrownBy(() -> LiveModelSettings.from(Map.of(REASONING_EFFORT_PROPERTY, value)::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(REASONING_EFFORT_PROPERTY + " must be one of [NONE,")
                .hasMessageEndingWith(", not '" + value + "'");
    }
}
