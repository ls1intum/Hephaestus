/**
 * Create-time presets for the two OpenAI wire APIs supported by Hephaestus.
 *
 * Presets are presentation only. Persisted connections keep only their endpoint, protocol and
 * credential shape, so the runtime stays provider-neutral and OpenAI-compatible.
 */
export type ProviderPreset = "OPENAI" | "AZURE_OPENAI_V1" | "OTHER";
export type LlmAuthMode = "BEARER" | "API_KEY";

export const PROVIDER_PRESET_ORDER: readonly ProviderPreset[] = [
	"OPENAI",
	"AZURE_OPENAI_V1",
	"OTHER",
];

export const PROVIDER_PRESET_LABELS: Record<ProviderPreset, string> = {
	OPENAI: "OpenAI",
	AZURE_OPENAI_V1: "Azure OpenAI v1",
	OTHER: "Other OpenAI-compatible endpoint",
};

export const PROVIDER_PRESET_SELECT_ITEMS = PROVIDER_PRESET_ORDER.map((preset) => ({
	value: preset,
	label: PROVIDER_PRESET_LABELS[preset],
}));

export const API_PROTOCOLS = {
	OPENAI_COMPLETIONS: "openai-completions",
	OPENAI_RESPONSES: "openai-responses",
} as const;

export function defaultProtocolFor(useResponsesApi = false): string {
	return useResponsesApi ? API_PROTOCOLS.OPENAI_RESPONSES : API_PROTOCOLS.OPENAI_COMPLETIONS;
}

export function usesResponsesApi(apiProtocol: string): boolean {
	return apiProtocol === API_PROTOCOLS.OPENAI_RESPONSES;
}

export function authModeDefaultFor(preset: ProviderPreset): LlmAuthMode {
	return preset === "AZURE_OPENAI_V1" ? "API_KEY" : "BEARER";
}

export function baseUrlDefaultFor(preset: ProviderPreset): string {
	switch (preset) {
		case "OPENAI":
			return "https://api.openai.com/v1";
		case "AZURE_OPENAI_V1":
			return "https://RESOURCE.openai.azure.com/openai/v1";
		case "OTHER":
			return "";
	}
}

export interface OpenAiConnectionIdentity {
	apiProtocol: string;
	baseUrl: string;
}

/** Only recognizes OpenAI itself. Azure remains a create-time convenience, not inferred domain state. */
export function presetForConnection(connection: OpenAiConnectionIdentity): ProviderPreset {
	try {
		if (new URL(connection.baseUrl).hostname.toLowerCase() === "api.openai.com") return "OPENAI";
	} catch {
		// Invalid legacy URLs remain editable as generic OpenAI-compatible endpoints.
	}
	return "OTHER";
}
