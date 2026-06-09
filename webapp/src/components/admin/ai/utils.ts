import type { AgentConfig, AiSettingsView } from "@/api/types.gen";

// types.gen exposes these unions only inline, so re-derive them here.
export type CredentialMode = AgentConfig["credentialMode"];
export type LlmProvider = AgentConfig["llmProvider"];

/** How a config is wired into a workspace's AI features. */
export type ConfigDesignation = "practice" | "mentor" | "both";

/**
 * Map each config id to its workspace designation derived from the AI settings
 * bindings. A config bound to both purposes is "both"; one purpose yields the
 * matching single designation; unbound configs are absent from the map.
 */
export function deriveDesignations(
	settings: Pick<AiSettingsView, "practiceConfigId" | "mentorConfigId"> | undefined,
): Map<number, ConfigDesignation> {
	const map = new Map<number, ConfigDesignation>();
	if (!settings) {
		return map;
	}
	const { practiceConfigId, mentorConfigId } = settings;
	if (practiceConfigId != null && practiceConfigId === mentorConfigId) {
		map.set(practiceConfigId, "both");
		return map;
	}
	if (practiceConfigId != null) {
		map.set(practiceConfigId, "practice");
	}
	if (mentorConfigId != null) {
		map.set(mentorConfigId, "mentor");
	}
	return map;
}

/**
 * Whether an in-progress credential input should be sent on save. PROXY never carries a key;
 * otherwise a non-empty input sets/replaces it and a blank input is a no-op (keep current —
 * clearing is the separate explicit clearLlmApiKey action).
 */
export function shouldSendKey(args: { mode: CredentialMode; input: string }): boolean {
	if (args.mode === "PROXY") {
		return false;
	}
	return args.input.trim().length > 0;
}

export const LLM_PROVIDER_LABELS: Record<LlmProvider, string> = {
	ANTHROPIC: "Anthropic",
	OPENAI: "OpenAI",
	AZURE_OPENAI: "Azure OpenAI",
};

export const CREDENTIAL_MODE_LABELS: Record<CredentialMode, string> = {
	PROXY: "Internal proxy",
	API_KEY: "API key",
	OAUTH: "OAuth",
};
