package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Shared blocklist of environment-variable names that callers must never inject into a sandbox
 * container — they enable library injection ({@code LD_PRELOAD}), code execution via tooling
 * ({@code GIT_SSH_COMMAND}, {@code NODE_OPTIONS}), proxy hijacking, or credential exfiltration
 * ({@code AWS_*}, {@code AZURE_*}, …).
 *
 * <p>Both the one-shot sync adapter and the long-lived interactive adapter route their caller
 * env through this filter; without it the interactive path would accept the very vectors the
 * sync path rejects.
 */
public final class SandboxEnvBlocklist {

    /** Exact names. Comparison is case-insensitive — Linux env vars are conventionally upper case but Java {@code Map} keys can be anything. */
    static final Set<String> BLOCKED_NAMES;

    /** Prefix matches. Catches new credentials in known families (e.g. {@code AWS_ROLE_ARN}) without per-key maintenance. */
    static final List<String> BLOCKED_PREFIXES = List.of(
        "AWS_",
        "GOOGLE_",
        "GCP_",
        "AZURE_",
        "DOCKER_",
        "ALIBABA_CLOUD_",
        "GIT_CONFIG_"
    );

    /** Specific names re-allowed even when they match a blocked prefix — set by adapters for legitimate SDK config. */
    static final Set<String> ALLOWED_PREFIX_EXCEPTIONS;

    static {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(
            List.of(
                // Library injection & path manipulation
                "LD_PRELOAD",
                "LD_LIBRARY_PATH",
                "PATH",
                "SHELL",
                "USER",
                // Node.js arbitrary-module-load — the mentor runner is `node`, so this would mirror LD_PRELOAD.
                // Documented at https://nodejs.org/api/cli.html#node_optionsoptions
                "NODE_OPTIONS",
                // Proxy hijacking
                "http_proxy",
                "https_proxy",
                "HTTP_PROXY",
                "HTTPS_PROXY",
                "no_proxy",
                "NO_PROXY",
                // Git: env-based command execution vectors (override git-config equivalents independently).
                "GIT_SSH",
                "GIT_SSH_COMMAND",
                "GIT_ASKPASS",
                "GIT_EDITOR",
                "GIT_EXEC_PATH",
                "GIT_TEMPLATE_DIR",
                "GIT_EXTERNAL_DIFF",
                "GIT_PROXY_COMMAND",
                "GIT_SEQUENCE_EDITOR",
                "GIT_PAGER",
                "GIT_TERMINAL_PROMPT",
                "GIT_ATTR_NOSYSTEM"
            )
        );
        BLOCKED_NAMES = names;

        TreeSet<String> allowed = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        allowed.addAll(
            List.of("AZURE_OPENAI_DEPLOYMENT_NAME_MAP", "AZURE_OPENAI_BASE_URL", "AZURE_OPENAI_API_VERSION")
        );
        ALLOWED_PREFIX_EXCEPTIONS = allowed;
    }

    private SandboxEnvBlocklist() {}

    /** {@return true if the named env var is forbidden by exact or prefix match (case-insensitive)}. */
    public static boolean isBlocked(String name) {
        if (name == null) {
            return false;
        }
        if (BLOCKED_NAMES.contains(name)) {
            return true;
        }
        if (ALLOWED_PREFIX_EXCEPTIONS.contains(name)) {
            return false;
        }
        String upper = name.toUpperCase(Locale.ROOT);
        for (String prefix : BLOCKED_PREFIXES) {
            if (upper.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
