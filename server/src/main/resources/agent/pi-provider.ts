// Shared Pi custom-provider registration helper (ONE credential path).
//
// Both pi-runner.ts and pi-mentor-runner.ts import this so the two runners can NEVER drift on how
// the "hephaestus" Pi provider is registered. Byte-identical registration holds by construction, not
// by a dedicated sync test: both runner profiles stage this same classpath resource verbatim into
// their sandbox, so there is only ever one copy of this file to drift from.
//
// Reads pi-provider.json (written by the server from the job's ConfigSnapshot — wire protocol, model
// id, capability envelope) for WHAT to request, and $LLM_PROXY_URL / $LLM_PROXY_TOKEN (written by the
// sandbox adapter) for WHERE to call and HOW to authenticate. The real provider API key never reaches
// this process — the proxy resolves it server-side from the live connection row on every call.
//
// The server derives cost from reported token usage + the model's price table (see LlmUsageRecorder /
// LlmModelPrice). Pi nevertheless requires the model's local `cost` shape while finalising assistant
// messages, so register zero rates here. They prevent an SDK crash without competing with the
// authoritative server-side catalog price.

import { existsSync, readFileSync } from "node:fs";
import type { ModelRegistry, ProviderModelConfig } from "@earendil-works/pi-coding-agent";
import { errorText } from "./pi-error-text.ts";

/** The subset of pi-provider.json this helper reads; the server writes it from the job's snapshot. */
export interface ProviderConfig {
	apiProtocol?: string;
	modelId?: string;
	supportsReasoning?: boolean;
	contextWindow?: number;
	maxOutputTokens?: number;
}

export const PROVIDER_CONFIG_FILENAME = "pi-provider.json";
export const DEFAULT_WORKSPACE_ROOT = "/workspace";

/**
 * A model as the registry really takes one.
 *
 * <p>`ProviderModelConfig` declares `contextWindow` and `maxTokens` required. The registry validates
 * each only when it is present and stores whatever it is handed, and PiRuntimeFactory omits them
 * whenever the model catalogue does not know them. Registering without them is the behaviour that has
 * always shipped; inventing a window here would silently change when a session compacts.
 */
type RegisterableModel = Omit<ProviderModelConfig, "contextWindow" | "maxTokens"> &
	Partial<Pick<ProviderModelConfig, "contextWindow" | "maxTokens">>;

/** The registry's own registration shape, reached through the one method this helper calls. */
type SdkProviderConfig = Parameters<ModelRegistry["registerProvider"]>[1];

/**
 * What this helper needs of a Pi ModelRegistry: that one method, taking the model above. Everything
 * except `models` stays the SDK's own type, so this states exactly one difference from it and a rename
 * anywhere else still fails the build. The SDK's own ModelRegistry satisfies it.
 */
declare class ModelRegistryPort {
	registerProvider(
		providerName: string,
		config: Omit<SdkProviderConfig, "models"> & { models?: RegisterableModel[] },
	): void;
}

/**
 * The narrowing this file starts from, declared rather than imported: pi-provider.ts is staged into
 * both runner sandboxes and may only depend on what both of them stage.
 */
function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null;
}

/**
 * pi-provider.json's fields, as the file actually carries them. The server writes it, but it reaches
 * here as parsed JSON with no guarantees, and a field of the wrong type reads as absent — which is
 * what {@link registerHephaestusProvider} already declines to register on.
 */
function asProviderConfig(parsed: unknown): ProviderConfig | null {
	if (!isRecord(parsed)) return null;
	const numberOrUndefined = (value: unknown) => (typeof value === "number" ? value : undefined);
	return {
		apiProtocol: typeof parsed.apiProtocol === "string" ? parsed.apiProtocol : undefined,
		modelId: typeof parsed.modelId === "string" ? parsed.modelId : undefined,
		supportsReasoning: parsed.supportsReasoning === true,
		contextWindow: numberOrUndefined(parsed.contextWindow),
		maxOutputTokens: numberOrUndefined(parsed.maxOutputTokens),
	};
}

/**
 * Load pi-provider.json from `${cwd}/pi-provider.json`. `cwd` defaults to the production workspace
 * root but is overridable — pi-mentor-runner.ts's live tests spawn the runner against a temp
 * directory (see MENTOR_RUNNER_CWD) rather than a real `/workspace` mount.
 *
 * <p>Returns null (not throws) when absent or malformed — callers decide how fatal that is.
 */
export function loadProviderConfig(cwd = DEFAULT_WORKSPACE_ROOT): ProviderConfig | null {
	const path = `${cwd}/${PROVIDER_CONFIG_FILENAME}`;
	if (!existsSync(path)) return null;
	try {
		const parsed: unknown = JSON.parse(readFileSync(path, "utf8"));
		return asProviderConfig(parsed);
	} catch (e) {
		console.error(`[pi-provider] failed to parse ${path}: ${errorText(e)}`);
		return null;
	}
}

/**
 * Register the "hephaestus" custom provider on the given ModelRegistry from a loaded provider
 * config + the sandbox's env vars. Returns true if registration happened, false if the config or
 * env vars were missing (caller logs/handles as appropriate — mirrors the previous
 * PI_HEPHAESTUS_BASE_URL-presence check).
 */
export function registerHephaestusProvider(
	modelRegistry: ModelRegistryPort,
	config: ProviderConfig | null,
	env: Record<string, string | undefined> = process.env,
): boolean {
	const baseUrl = env.LLM_PROXY_URL;
	const hasToken = Boolean(env.LLM_PROXY_TOKEN);
	if (!config?.apiProtocol || !config.modelId || !baseUrl || !hasToken) {
		return false;
	}

	const model: RegisterableModel = {
		id: config.modelId,
		name: config.modelId,
		reasoning: Boolean(config.supportsReasoning),
		input: ["text"],
		cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 },
	};
	if (Number.isFinite(config.contextWindow)) {
		model.contextWindow = config.contextWindow;
	}
	if (Number.isFinite(config.maxOutputTokens)) {
		model.maxTokens = config.maxOutputTokens;
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
