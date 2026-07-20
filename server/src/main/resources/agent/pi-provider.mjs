// Shared Pi custom-provider registration helper (#1368 slice 5 — ONE credential path).
//
// Both pi-runner.mjs and pi-mentor-runner.mjs import this so the two runners can NEVER drift on how
// the "hephaestus" Pi provider is registered (byte-identical registration is enforced by
// PiProviderHelperSyncTest on the Java side — the classpath copy staged for each runner must be the
// same file).
//
// Reads pi-provider.json (written by the server from the job's ConfigSnapshot — wire protocol, model
// id, capability envelope) for WHAT to request, and $LLM_PROXY_URL / $LLM_PROXY_TOKEN (written by the
// sandbox adapter) for WHERE to call and HOW to authenticate. The real provider API key never reaches
// this process — the proxy resolves it server-side from the live connection row on every call.
//
// The server derives cost from reported token usage + the model's price table (see LlmUsageRecorder /
// LlmModelPrice); this module intentionally reports NO per-token `cost` on the model registration —
// the runner only ever reports token counts as it observes them.

import { existsSync, readFileSync } from "fs";

export const PROVIDER_CONFIG_FILENAME = "pi-provider.json";
export const DEFAULT_WORKSPACE_ROOT = "/workspace";

/**
 * Load pi-provider.json from `${cwd}/pi-provider.json`. `cwd` defaults to the production workspace
 * root but is overridable — pi-mentor-runner.mjs's live tests spawn the runner against a temp
 * directory (see MENTOR_RUNNER_CWD) rather than a real `/workspace` mount.
 *
 * <p>Returns null (not throws) when absent or malformed — callers decide how fatal that is.
 */
export function loadProviderConfig(cwd = DEFAULT_WORKSPACE_ROOT) {
    const path = `${cwd}/${PROVIDER_CONFIG_FILENAME}`;
    if (!existsSync(path)) return null;
    try {
        return JSON.parse(readFileSync(path, "utf-8"));
    } catch (e) {
        console.error(`[pi-provider] failed to parse ${path}: ${e.message}`);
        return null;
    }
}

/**
 * Register the "hephaestus" custom provider on the given ModelRegistry from a loaded provider
 * config + the sandbox's env vars. Returns true if registration happened, false if the config or
 * env vars were missing (caller logs/handles as appropriate — mirrors the previous
 * PI_HEPHAESTUS_BASE_URL-presence check).
 */
export function registerHephaestusProvider(modelRegistry, config, env = process.env) {
    const baseUrl = env.LLM_PROXY_URL;
    const hasToken = Boolean(env.LLM_PROXY_TOKEN);
    if (!config || !config.apiProtocol || !config.modelId || !baseUrl || !hasToken) {
        return false;
    }

    const model = {
        id: config.modelId,
        name: config.modelId,
        reasoning: Boolean(config.supportsReasoning),
        input: ["text"],
    };
    if (Number.isFinite(config.contextWindow)) {
        model.contextWindow = config.contextWindow;
    }
    if (Number.isFinite(config.maxOutputTokens)) {
        model.maxTokens = config.maxOutputTokens;
    }
    if (config.cacheControlFormat) {
        // Opt into cache_control markers so the large stable prefix (system prompt + the diff/context
        // the agent reads once) is cached across turns. A no-op if the upstream ignores the markers.
        model.compat = { cacheControlFormat: config.cacheControlFormat, supportsLongCacheRetention: true };
    }

    modelRegistry.registerProvider("hephaestus", {
        name: "Hephaestus Gateway",
        baseUrl,
        // Env VAR NAME, not the literal value — the SDK reads process.env[apiKey] itself. The sandbox
        // adapter already sets LLM_PROXY_TOKEN from NetworkPolicy.llmProxyToken (the job-scoped token).
        apiKey: "LLM_PROXY_TOKEN",
        authHeader: true,
        api: config.apiProtocol,
        models: [model],
    });
    return true;
}
