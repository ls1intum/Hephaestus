package de.tum.cit.aet.hephaestus.agent.sandbox.docker;

import de.tum.cit.aet.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxSpec;
import de.tum.cit.aet.hephaestus.observability.StructuredLogKeys;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * The environment of a practice review's sandbox, the same on every runtime: what the request asks for, less
 * {@link SandboxEnvBlocklist}, then the enforced Git configuration and the gateway variables. Uses no runtime.
 */
public final class SandboxEnvironment {

    private static final Logger log = LoggerFactory.getLogger(SandboxEnvironment.class);

    private static final String PROXY_URL_PLACEHOLDER = "{appServerIp}";

    /**
     * Overrides repository, global and system Git config; explicit {@code git -c} options take precedence.
     *
     * <p>Each entry pairs a git config key that can execute code with a value that neutralises it: an
     * empty string disables the feature, {@code /nonexistent} points it at a path that cannot exist, and
     * {@code cat} is the canonical no-op pager.
     *
     * @see <a href="https://git-scm.com/docs/git-config#_environment">git-config environment</a>
     */
    public static final List<Map.Entry<String, String>> GIT_SECURITY_CONFIGS = List.of(
            Map.entry("core.hooksPath", "/nonexistent"),
            Map.entry("core.fsmonitor", "false"),
            Map.entry("core.sshCommand", ""),
            Map.entry("core.askPass", ""),
            Map.entry("core.editor", ""),
            Map.entry("core.pager", "cat"),
            Map.entry("core.gitProxy", ""),
            Map.entry("sequence.editor", ""),
            Map.entry("credential.helper", ""),
            Map.entry("diff.external", ""),
            Map.entry("protocol.ext.allow", "never"));

    private SandboxEnvironment() {}

    /**
     * @param gatewayHost the host part of the gateway's URL as the sandbox reaches it: a name or an address, an IPv6
     *     address in brackets
     */
    public static Map<String, String> build(SandboxSpec spec, String gatewayHost, int gatewayPort) {
        Map<String, String> env = new HashMap<>();

        // Enforced values must overwrite caller-supplied variables: SandboxEnvBlocklist already drops the
        // dangerous ones, and this ordering keeps a value added below winning even if it is not listed there.
        for (var entry : spec.environment().entrySet()) {
            if (SandboxEnvBlocklist.isBlocked(entry.getKey())) {
                log.warn("Blocked dangerous environment variable: {}", entry.getKey());
            } else {
                env.put(entry.getKey(), entry.getValue());
            }
        }
        addTraceEnvironment(env);

        // The agent can clone a repository at runtime and carry a hostile .git/config in with it.
        int idx = 0;
        for (var gitConfig : GIT_SECURITY_CONFIGS) {
            env.put("GIT_CONFIG_KEY_" + idx, gitConfig.getKey());
            env.put("GIT_CONFIG_VALUE_" + idx, gitConfig.getValue());
            idx++;
        }
        env.put("GIT_CONFIG_COUNT", String.valueOf(idx));

        // Prevent git from prompting interactively (would hang the agent)
        env.put("GIT_TERMINAL_PROMPT", "0");
        // Prevent system-wide gitattributes from being loaded
        env.put("GIT_ATTR_NOSYSTEM", "1");

        NetworkPolicy policy = spec.networkPolicy();
        if (policy != null) {
            String gatewayUrl = "http://" + gatewayHost + ":" + gatewayPort;
            String proxyUrl = policy.llmProxyUrl();
            if (proxyUrl != null) {
                env.put("LLM_PROXY_URL", proxyUrl.replace(PROXY_URL_PLACEHOLDER, gatewayHost));
            } else {
                // One route for every provider: the proxy identifies the connection from the
                // authenticated job token, not the URL.
                env.put("LLM_PROXY_URL", gatewayUrl + "/internal/llm");
            }

            String token = policy.llmProxyToken();
            if (token != null) {
                env.put("LLM_PROXY_TOKEN", token);
            }
        }

        return env;
    }

    private static void addTraceEnvironment(Map<String, String> env) {
        String traceId = MDC.get(StructuredLogKeys.TRACE_ID);
        String spanId = MDC.get(StructuredLogKeys.SPAN_ID);
        if (traceId != null && spanId != null) {
            env.put("TRACE_ID", traceId);
            env.put(
                    "TRACEPARENT",
                    "00-" + traceId + "-" + spanId + "-"
                            + ("01".equals(MDC.get(StructuredLogKeys.TRACE_FLAGS)) ? "01" : "00"));
        }
    }
}
