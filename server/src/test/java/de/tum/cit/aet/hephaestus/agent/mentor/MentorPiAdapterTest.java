package de.tum.cit.aet.hephaestus.agent.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentImageProperties;
import de.tum.cit.aet.hephaestus.agent.runtime.PiPlanSpec;
import de.tum.cit.aet.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.cit.aet.hephaestus.agent.runtime.PiRuntimeFactory.PiPlan;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.InteractiveSandboxSpec;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * Unit coverage for {@link MentorPiAdapter#buildSandboxSpec}: the genuinely error-prone branches the
 * orchestration-level {@code MentorChatServiceTest} stubs over — context-key validation, base-URL precedence,
 * session-restore injection, and the always-present system prompt. {@link PiRuntimeFactory} is mocked so the
 * captured {@link PiPlanSpec} can be asserted on directly.
 */
class MentorPiAdapterTest extends BaseUnitTest {

    private static final MentorAgentRequest REQUEST = new MentorAgentRequest(7L, 42L);

    @Mock
    private PiRuntimeFactory runtimeFactory;

    private MentorPiAdapter adapter;

    @BeforeEach
    void setUp() {
        // A minimal valid plan; the tests assert on the captured spec, not on the returned plan content.
        PiPlan plan = new PiPlan(
            List.of("sh", "-c", "true"),
            Map.of(),
            Map.of(),
            new NetworkPolicy(true, null, null, null)
        );
        when(runtimeFactory.build(any())).thenReturn(plan);
        adapter = newAdapter("");
    }

    private MentorPiAdapter newAdapter(String propertyBaseUrl) {
        return new MentorPiAdapter(
            runtimeFactory,
            new MentorAgentProperties(100000, propertyBaseUrl),
            new AgentImageProperties("test-image:latest", null)
        );
    }

    /** API_KEY mode so {@link PiPlanSpec} validates (the mentor path passes no jobToken, which PROXY requires). */
    private static MentorLlmConfig llmConfig(String baseUrl) {
        return new MentorLlmConfig(LlmProvider.OPENAI, CredentialMode.API_KEY, "sk-test-key", "gpt-5.4", baseUrl, 120);
    }

    private PiPlanSpec capturePlanSpec(MentorLlmConfig config, Map<String, byte[]> contexts, SessionRestore restore) {
        adapter.buildSandboxSpec(REQUEST, config, contexts, restore);
        ArgumentCaptor<PiPlanSpec> captor = ArgumentCaptor.forClass(PiPlanSpec.class);
        verify(runtimeFactory).build(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("a context-prefixed context key passes; a stray key is rejected")
    void contextKeyValidation() {
        Map<String, byte[]> ok = Map.of(
            MentorPiAdapter.CONTEXT_INPUT_PREFIX + "recent_authored_work.json",
            "{}".getBytes(StandardCharsets.UTF_8)
        );
        // does not throw
        adapter.buildSandboxSpec(REQUEST, llmConfig(null), ok, null);

        Map<String, byte[]> stray = Map.of("out/leak.json", "{}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> adapter.buildSandboxSpec(REQUEST, llmConfig(null), stray, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(MentorPiAdapter.CONTEXT_INPUT_PREFIX);
    }

    @Test
    @DisplayName("llmConfig base URL overrides the instance property")
    void llmConfigBaseUrlWins() {
        adapter = newAdapter("https://property.example");
        PiPlanSpec spec = capturePlanSpec(llmConfig("https://config.example"), Map.of(), null);
        assertThat(spec.baseUrl()).isEqualTo("https://config.example");
    }

    @Test
    @DisplayName("a blank instance base URL property yields a null baseUrl when the config has none")
    void blankPropertyYieldsNullBaseUrl() {
        PiPlanSpec spec = capturePlanSpec(llmConfig(null), Map.of(), null);
        assertThat(spec.baseUrl()).isNull();
    }

    @Test
    @DisplayName("a blank config base URL falls through to the instance property")
    void blankConfigBaseUrlFallsBackToProperty() {
        adapter = newAdapter("https://property.example");
        PiPlanSpec spec = capturePlanSpec(llmConfig("   "), Map.of(), null);
        assertThat(spec.baseUrl()).isEqualTo("https://property.example");
    }

    @Test
    @DisplayName("sessionRestore injects exactly .sessions/<threadId>.jsonl with the supplied bytes")
    void sessionRestoreInjectsJsonl() {
        UUID threadId = UUID.randomUUID();
        byte[] bytes = "{\"replay\":true}".getBytes(StandardCharsets.UTF_8);
        PiPlanSpec spec = capturePlanSpec(llmConfig(null), Map.of(), new SessionRestore(threadId, bytes));

        String expectedKey = MentorPiAdapter.SESSIONS_DIR_PREFIX + threadId + ".jsonl";
        assertThat(spec.extraInputs()).containsKey(expectedKey);
        assertThat(spec.extraInputs().get(expectedKey)).isEqualTo(bytes);
    }

    @Test
    @DisplayName("no sessionRestore adds no .sessions entry")
    void noSessionRestoreAddsNoSessionsEntry() {
        PiPlanSpec spec = capturePlanSpec(llmConfig(null), Map.of(), null);
        assertThat(spec.extraInputs().keySet()).noneMatch(k -> k.startsWith(MentorPiAdapter.SESSIONS_DIR_PREFIX));
    }

    @Test
    @DisplayName("the mentor system prompt is always injected at SYSTEM_PROMPT_PATH")
    void systemPromptAlwaysInjected() {
        PiPlanSpec spec = capturePlanSpec(llmConfig(null), Map.of(), null);
        assertThat(spec.extraInputs()).containsKey(MentorPiAdapter.SYSTEM_PROMPT_PATH);
        assertThat(spec.extraInputs().get(MentorPiAdapter.SYSTEM_PROMPT_PATH)).isNotEmpty();
    }

    @Test
    @DisplayName("the sandbox spec carries the routing identity from the request")
    void specCarriesRoutingIdentity() {
        InteractiveSandboxSpec spec = adapter.buildSandboxSpec(REQUEST, llmConfig(null), Map.of(), null);
        assertThat(spec.userId()).isEqualTo("42");
        assertThat(spec.workspaceId()).isEqualTo("7");
        assertThat(spec.image()).isEqualTo("test-image:latest");
    }
}
