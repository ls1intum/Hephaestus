package de.tum.in.www1.hephaestus.agent.adapter.spi;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.adapter.AgentResult;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Translates agent-specific configuration into a sandbox-ready specification.
 *
 * <p>Each {@link AgentType} has exactly one adapter. Adapters are stateless, thread-safe, and
 * produce an {@link AgentSandboxSpec} that the orchestrator uses to build the final
 * {@link de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxSpec}.
 *
 * <p>Adapters are registered in
 * {@link de.tum.in.www1.hephaestus.agent.adapter.AgentAdapterRegistry} and looked up by
 * {@link AgentType}.
 */
public interface AgentAdapter {
    /** The agent type this adapter handles. */
    AgentType agentType();

    /**
     * Build a sandbox specification for the given agent execution request.
     *
     * @param request the agent execution parameters
     * @return a sandbox specification ready for the orchestrator to execute
     */
    AgentSandboxSpec buildSandboxSpec(AgentAdapterRequest request);

    /**
     * Parse the raw sandbox result into an agent-level result.
     *
     * <p>Default implementation treats exit code 0 (no timeout) as success and extracts
     * {@code result.json} from the output files. Adapters may override for agent-specific parsing.
     *
     * @param sandboxResult the raw container execution result
     * @return parsed agent result with success flag and structured output
     */
    default AgentResult parseResult(SandboxResult sandboxResult) {
        boolean success = sandboxResult.exitCode() == 0 && !sandboxResult.timedOut();
        Map<String, Object> output = new HashMap<>();
        output.put("exitCode", sandboxResult.exitCode());
        output.put("timedOut", sandboxResult.timedOut());

        byte[] resultFile = sandboxResult.outputFiles().get("result.json");
        if (resultFile != null) {
            output.put("rawOutput", new String(resultFile, StandardCharsets.UTF_8));
        }

        return new AgentResult(success, output);
    }

    /**
     * Build a {@link NetworkPolicy} from the request's credential mode and internet settings.
     *
     * <p>In {@link CredentialMode#PROXY} mode, internet access is determined by
     * {@link AgentAdapterRequest#allowInternet()}. In direct modes ({@code API_KEY}, {@code OAUTH}),
     * internet is always enabled (enforced by validation).
     *
     * <p>Note: {@code llmProxyUrl} is always {@code null} in the returned policy. The sandbox
     * layer resolves the actual proxy IP during the PREPARE phase and injects it as
     * {@code LLM_PROXY_URL} before container start.
     *
     * @param request the agent execution parameters
     * @return the network policy for the container
     */
    /**
     * Build the shell command fragment that runs precomputation scripts before the agent.
     *
     * <p>Scripts are injected from DB into {@code .precompute/practices/} by the handler.
     * The runner auto-discovers them, executes via Bun, and produces
     * {@code .precompute/summary.md} + per-practice JSON. Failure is non-fatal —
     * the agent works without hints if precompute fails.
     *
     * @return shell command fragment ending with {@code " && "}, ready to prepend to agent command
     */
    static String buildPrecomputeStep() {
        // Handler injects practice scripts (root-owned) into .precompute/practices/.
        // Agent (uid 1000) can't write to root-owned dirs, so:
        // 1. Copy scripts + shared libs to writable /tmp/precompute/
        // 2. Run from there, output to agent-writable .precompute-out/
        // 3. The agent reads .precompute-out/summary.md
        return (
            "(mkdir -p /workspace/.precompute-out/practices" +
            " && cp /workspace/.precompute/practices/*.ts /workspace/.precompute-out/practices/" +
            " && ln -sf /opt/precompute/lib /workspace/.precompute-out/lib" +
            " && bun run /opt/precompute/runner.ts" +
            " --repo /workspace/repo" +
            " --diff /workspace/.context/diff.patch" +
            " --metadata /workspace/.context/metadata.json" +
            " --output /workspace/.precompute-out" +
            " > /tmp/precompute-runner.log 2>&1" +
            " || echo '[precompute] failed, continuing without hints') && "
        );
    }

    static NetworkPolicy buildNetworkPolicy(AgentAdapterRequest request) {
        if (request.credentialMode() == CredentialMode.PROXY) {
            String providerPath = request.llmProvider().name().toLowerCase(java.util.Locale.ROOT);
            return new NetworkPolicy(request.allowInternet(), null, request.jobToken(), providerPath);
        }
        return new NetworkPolicy(true, null, null, null);
    }

    /** Classpath prefix for agent resource files. */
    String AGENT_RESOURCE_PREFIX = "agent/";

    /**
     * Load a classpath resource from the {@code agent/} directory.
     *
     * <p>Shared utility for all adapters — eliminates duplicate private methods.
     *
     * @param relativePath path relative to {@code agent/} (e.g. {@code "CLAUDE.md"})
     * @return file content as bytes
     * @throws IllegalStateException if the resource is missing or unreadable
     */
    static byte[] loadClasspathResource(String relativePath) {
        String fullPath = AGENT_RESOURCE_PREFIX + relativePath;
        try (InputStream is = AgentAdapter.class.getClassLoader().getResourceAsStream(fullPath)) {
            if (is == null) {
                throw new IllegalStateException("Missing classpath resource: " + fullPath);
            }
            return is.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read classpath resource: " + fullPath, e);
        }
    }
}
