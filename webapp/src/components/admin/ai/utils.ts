import type { AgentConfig, AiSettingsView } from "@/api/types.gen";

// types.gen exposes this union only inline, so re-derive it here.
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

export const LLM_PROVIDER_LABELS: Record<LlmProvider, string> = {
	ANTHROPIC: "Anthropic",
	OPENAI: "OpenAI",
	AZURE_OPENAI: "Azure OpenAI",
};
