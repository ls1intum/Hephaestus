package de.tum.in.www1.hephaestus.agent.adapter;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentAdapter;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentAdapterRequest;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentSandboxSpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for Anthropic's Claude Code CLI agent.
 *
 * <p>CLI: {@code claude -p "prompt" --output-format json --dangerously-skip-permissions}
 *
 * <p>Authentication modes:
 * <ul>
 *   <li>PROXY: bridges {@code $LLM_PROXY_URL} → {@code ANTHROPIC_BASE_URL}</li>
 *   <li>API_KEY: sets {@code ANTHROPIC_API_KEY} directly</li>
 *   <li>OAUTH: sets {@code CLAUDE_CODE_OAUTH_TOKEN} directly</li>
 * </ul>
 */
public class ClaudeCodeAgentAdapter implements AgentAdapter {

    static final String IMAGE = "ghcr.io/ls1intum/hephaestus/agent-claude-code:latest";
    static final String OUTPUT_PATH = "/workspace/.output";

    @Override
    public AgentType agentType() {
        return AgentType.CLAUDE_CODE;
    }

    @Override
    public AgentSandboxSpec buildSandboxSpec(AgentAdapterRequest request) {
        Map<String, String> env = new HashMap<>();
        String authSetup = buildAuthSetup(request, env);

        if (request.modelName() != null && !request.modelName().isBlank()) {
            env.put("ANTHROPIC_MODEL", request.modelName());
        }

        String command =
            authSetup +
            "mkdir -p " +
            OUTPUT_PATH +
            " && PROMPT=$(cat /workspace/.prompt)" +
            " && claude -p \"$PROMPT\" --output-format json" +
            " --dangerously-skip-permissions" +
            " > " +
            OUTPUT_PATH +
            "/result.json";

        Map<String, byte[]> inputFiles = Map.of(".prompt", request.prompt().getBytes(StandardCharsets.UTF_8));

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

    /**
     * Build the shell auth setup prefix and populate env vars based on credential mode.
     */
    String buildAuthSetup(AgentAdapterRequest request, Map<String, String> env) {
        return switch (request.credentialMode()) {
            case PROXY -> "export ANTHROPIC_BASE_URL=\"$LLM_PROXY_URL\"" +
            " ANTHROPIC_API_KEY=\"$LLM_PROXY_TOKEN\"" +
            " ANTHROPIC_AUTH_TOKEN=''" +
            " CLAUDE_CODE_OAUTH_TOKEN=''" +
            " && ";
            case API_KEY -> {
                env.put("ANTHROPIC_API_KEY", request.credential());
                yield "export ANTHROPIC_AUTH_TOKEN=''" + " CLAUDE_CODE_OAUTH_TOKEN=''" + " && ";
            }
            case OAUTH -> {
                env.put("CLAUDE_CODE_OAUTH_TOKEN", request.credential());
                yield "export ANTHROPIC_API_KEY=''" + " ANTHROPIC_AUTH_TOKEN=''" + " && ";
            }
        };
    }
}
