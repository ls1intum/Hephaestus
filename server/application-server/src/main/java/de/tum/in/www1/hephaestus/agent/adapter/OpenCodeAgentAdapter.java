package de.tum.in.www1.hephaestus.agent.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentAdapter;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentAdapterRequest;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentSandboxSpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for the OpenCode CLI agent (provider-agnostic).
 *
 * <p>CLI: {@code opencode run "prompt" --format json}
 *
 * <p>OpenCode requires a JSON config file. In proxy mode, the config references
 * {@code {env:LLM_PROXY_URL}} and {@code {env:LLM_PROXY_TOKEN}} which OpenCode resolves at
 * runtime. In direct modes, the config references standard provider env vars.
 *
 * <p>The config is injected to {@code /workspace/opencode.json} where OpenCode auto-discovers it.
 */
public class OpenCodeAgentAdapter implements AgentAdapter {

    static final String IMAGE = "ghcr.io/ls1intum/hephaestus/agent-opencode:latest";
    static final String OUTPUT_PATH = "/workspace/.output";

    private final ObjectMapper objectMapper;

    OpenCodeAgentAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentType agentType() {
        return AgentType.OPENCODE;
    }

    @Override
    public AgentSandboxSpec buildSandboxSpec(AgentAdapterRequest request) {
        Map<String, String> env = new HashMap<>();
        Map<String, byte[]> inputFiles = new LinkedHashMap<>();

        configureAuth(request, env);
        byte[] configJson = buildConfigJson(request);
        inputFiles.put("opencode.json", configJson);
        inputFiles.put(".prompt", request.prompt().getBytes(StandardCharsets.UTF_8));

        String command =
            "mkdir -p " +
            OUTPUT_PATH +
            " && PROMPT=$(cat /workspace/.prompt)" +
            " && opencode run \"$PROMPT\" --format json" +
            " > " +
            OUTPUT_PATH +
            "/result.json";

        return new AgentSandboxSpec(
            IMAGE,
            List.of("sh", "-c", command),
            env,
            inputFiles,
            OUTPUT_PATH,
            null,
            AgentAdapter.buildNetworkPolicy(request)
        );
    }

    void configureAuth(AgentAdapterRequest request, Map<String, String> env) {
        switch (request.credentialMode()) {
            case PROXY -> {
                // Env vars set by DockerSandboxAdapter (LLM_PROXY_URL, LLM_PROXY_TOKEN).
                // Config file references them via {env:...} syntax.
            }
            case API_KEY, OAUTH -> {
                // OpenCode does not distinguish OAUTH from API_KEY — both use the provider's
                // standard API key env var. OAuth access tokens work as bearer tokens in the
                // same header position.
                switch (request.llmProvider()) {
                    case ANTHROPIC -> env.put("ANTHROPIC_API_KEY", request.credential());
                    case OPENAI -> env.put("OPENAI_API_KEY", request.credential());
                    default -> throw new IllegalArgumentException(
                        "Unsupported LLM provider for OpenCode: " + request.llmProvider()
                    );
                }
            }
        }
    }

    /**
     * Build the OpenCode JSON config as serialized bytes.
     *
     * <p>Uses Jackson ObjectMapper for safe JSON generation — all values are properly escaped,
     * preventing JSON injection through model names or other user-supplied strings.
     */
    byte[] buildConfigJson(AgentAdapterRequest request) {
        if (request.modelName() == null || request.modelName().isBlank()) {
            throw new IllegalArgumentException(
                "modelName is required for OpenCode — unlike Claude Code, OpenCode has no default model"
            );
        }
        String model = request.modelName();
        Map<String, Object> config = new LinkedHashMap<>();

        if (request.credentialMode() == CredentialMode.PROXY) {
            Map<String, Object> providerOptions = new LinkedHashMap<>();
            providerOptions.put("baseURL", "{env:LLM_PROXY_URL}");
            providerOptions.put("apiKey", "{env:LLM_PROXY_TOKEN}");

            Map<String, Object> hephaestusProvider = new LinkedHashMap<>();
            hephaestusProvider.put("npm", "@ai-sdk/openai-compatible");
            hephaestusProvider.put("options", providerOptions);
            hephaestusProvider.put("models", Map.of(model, Map.of()));

            config.put("provider", Map.of("hephaestus", hephaestusProvider));
            config.put("model", "hephaestus/" + model);
        } else {
            // Direct mode: omit "provider" field — OpenCode resolves the built-in
            // provider from the model prefix (e.g. "openai/gpt-5-mini").
            // Setting "provider" to a string is invalid (OpenCode expects a record).
            String providerPrefix = switch (request.llmProvider()) {
                case ANTHROPIC -> "anthropic";
                case OPENAI -> "openai";
            };
            config.put("model", providerPrefix + "/" + model);
        }

        config.put("share", "disabled");
        config.put("autoupdate", false);

        return serializeJson(config);
    }

    private byte[] serializeJson(Object data) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize OpenCode config", e);
        }
    }
}
